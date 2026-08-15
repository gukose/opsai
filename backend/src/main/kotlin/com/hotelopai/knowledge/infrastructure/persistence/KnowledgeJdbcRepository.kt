package com.hotelopai.knowledge.infrastructure.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.hotelopai.knowledge.application.KnowledgeChunkRepository
import com.hotelopai.knowledge.application.KnowledgeAnswer
import com.hotelopai.knowledge.application.KnowledgeAnswerConfidence
import com.hotelopai.knowledge.application.KnowledgeAnswerFailureCategory
import com.hotelopai.knowledge.application.KnowledgeAnswerFeedback
import com.hotelopai.knowledge.application.KnowledgeAnswerFeedbackType
import com.hotelopai.knowledge.application.KnowledgeAnswerId
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderDiagnosticOutcome
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderDiagnosticPage
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderDiagnosticRecord
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderDiagnosticTrigger
import com.hotelopai.knowledge.application.KnowledgeAnswerProviderDiagnosticType
import com.hotelopai.knowledge.application.KnowledgeAnswerFeedbackAnalytics
import com.hotelopai.knowledge.application.KnowledgeAnswerRepository
import com.hotelopai.knowledge.application.KnowledgeAnswerRequestLifecycle
import com.hotelopai.knowledge.application.KnowledgeAnswerRequestStatus
import com.hotelopai.knowledge.application.KnowledgeAnswerResponseValidationOutcome
import com.hotelopai.knowledge.application.KnowledgeAnswerStatusCounts
import com.hotelopai.knowledge.application.KnowledgeAnswerStatus
import com.hotelopai.knowledge.application.KnowledgeCitation
import com.hotelopai.knowledge.application.KnowledgeDocumentFilter
import com.hotelopai.knowledge.application.KnowledgeDocumentPage
import com.hotelopai.knowledge.application.KnowledgeDocumentRepository
import com.hotelopai.knowledge.application.KnowledgeEmbeddingFailureCategory
import com.hotelopai.knowledge.application.KnowledgeEmbeddingProviderId
import com.hotelopai.knowledge.application.KnowledgeEmbeddingDiagnosticOutcome
import com.hotelopai.knowledge.application.KnowledgeEmbeddingDiagnosticRecord
import com.hotelopai.knowledge.application.KnowledgeEmbeddingDiagnosticType
import com.hotelopai.knowledge.application.KnowledgeEmbeddingRecord
import com.hotelopai.knowledge.application.KnowledgeEmbeddingRepository
import com.hotelopai.knowledge.application.KnowledgeEmbeddingBatchSummary
import com.hotelopai.knowledge.application.KnowledgeEmbeddingProviderReadiness
import com.hotelopai.knowledge.application.KnowledgeEmbeddingScheduleState
import com.hotelopai.knowledge.application.KnowledgeEmbeddingService
import com.hotelopai.knowledge.application.KnowledgeEmbeddingStatus
import com.hotelopai.knowledge.application.KnowledgeEmbeddingVector
import com.hotelopai.knowledge.application.KnowledgeMetadataRepository
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationRepository
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationRun
import com.hotelopai.knowledge.application.KnowledgeRetrievalEvaluationStatus
import com.hotelopai.knowledge.application.KnowledgeRetrievalMetricSummary
import com.hotelopai.knowledge.application.KnowledgeSemanticCandidate
import com.hotelopai.knowledge.application.KnowledgeSearchMode
import com.hotelopai.knowledge.application.KnowledgeSearchPage
import com.hotelopai.knowledge.application.KnowledgeSearchQuery
import com.hotelopai.knowledge.application.KnowledgeSearchResult
import com.hotelopai.knowledge.domain.KnowledgeCategory
import com.hotelopai.knowledge.domain.KnowledgeChunk
import com.hotelopai.knowledge.domain.KnowledgeChunkId
import com.hotelopai.knowledge.domain.KnowledgeDocument
import com.hotelopai.knowledge.domain.KnowledgeDocumentId
import com.hotelopai.knowledge.domain.KnowledgeMetadata
import com.hotelopai.knowledge.domain.KnowledgeSource
import com.hotelopai.shared.kernel.PersistenceInstant
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.sqrt

@Repository
@Transactional
class KnowledgeJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) : KnowledgeDocumentRepository, KnowledgeChunkRepository, KnowledgeMetadataRepository, KnowledgeEmbeddingRepository, KnowledgeRetrievalEvaluationRepository, KnowledgeAnswerRepository {
    private val mapType = object : TypeReference<Map<String, String>>() {}
    private val citationListType = object : TypeReference<List<KnowledgeCitation>>() {}

    override fun save(document: KnowledgeDocument): KnowledgeDocument {
        val normalized = document.copy(metadata = document.metadata.normalized())
        jdbcTemplate.update(
            """
            insert into knowledge_document (
                id, hotel_id, title, category, source, language, original_content, metadata, created_at, updated_at, version
            ) values (
                :id, :hotelId, :title, :category, :source, :language, :originalContent, cast(:metadata as jsonb), :createdAt, :updatedAt, :version
            )
            on conflict (id) do update set
                hotel_id = :hotelId,
                title = :title,
                category = :category,
                source = :source,
                language = :language,
                original_content = :originalContent,
                metadata = cast(:metadata as jsonb),
                updated_at = :updatedAt,
                version = knowledge_document.version + 1
            """.trimIndent(),
            documentParams(normalized)
        )
        return requireNotNull(find(normalized.id))
    }

    @Transactional(readOnly = true)
    override fun find(id: KnowledgeDocumentId): KnowledgeDocument? =
        jdbcTemplate.query(
            "select * from knowledge_document where id = :id",
            mapOf("id" to id.value),
            ::mapDocument
        ).firstOrNull()?.withStoredMetadataAndChunks()

    @Transactional(readOnly = true)
    override fun find(filter: KnowledgeDocumentFilter): KnowledgeDocumentPage {
        val page = filter.page.coerceAtLeast(0)
        val size = filter.size.coerceIn(1, 100)
        val params = MapSqlParameterSource().addValue("limit", size).addValue("offset", page * size)
        val where = mutableListOf<String>()
        filter.hotelId?.let { where += "d.hotel_id = :hotelId"; params.addValue("hotelId", it) }
        filter.category?.let { where += "d.category = :category"; params.addValue("category", it.name) }
        filter.source?.let { where += "d.source = :source"; params.addValue("source", it.name) }
        filter.tag?.let {
            where += ":tag = any(m.tags)"
            params.addValue("tag", KnowledgeMetadata.normalizeTag(it))
        }
        val whereSql = if (where.isEmpty()) "" else "where ${where.joinToString(" and ")}"
        val fromSql = "from knowledge_document d left join knowledge_metadata m on m.document_id = d.id $whereSql"
        val total = jdbcTemplate.queryForObject("select count(*) $fromSql", params, Long::class.java) ?: 0L
        val documents = jdbcTemplate.query(
            """
            select d.*
            $fromSql
            order by d.updated_at desc, d.id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapDocument
        ).map { it.withStoredMetadataAndChunks() }
        return KnowledgeDocumentPage(documents, page, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    override fun delete(id: KnowledgeDocumentId): Boolean =
        jdbcTemplate.update("delete from knowledge_document where id = :id", mapOf("id" to id.value)) > 0

    override fun replace(documentId: KnowledgeDocumentId, chunks: List<KnowledgeChunk>): List<KnowledgeChunk> {
        jdbcTemplate.update("delete from knowledge_chunk where document_id = :documentId", mapOf("documentId" to documentId.value))
        chunks.sortedBy { it.order }.forEach { chunk ->
            jdbcTemplate.update(
                """
                insert into knowledge_chunk (
                    id, document_id, chunk_order, heading, text, metadata, created_at, updated_at, version
                ) values (
                    :id, :documentId, :chunkOrder, :heading, :text, cast(:metadata as jsonb), :createdAt, :updatedAt, :version
                )
                """.trimIndent(),
                chunkParams(chunk.copy(metadata = chunk.metadata.normalized()))
            )
        }
        return findByDocument(documentId)
    }

    @Transactional(readOnly = true)
    override fun findByDocument(documentId: KnowledgeDocumentId): List<KnowledgeChunk> =
        jdbcTemplate.query(
            """
            select *
            from knowledge_chunk
            where document_id = :documentId
            order by chunk_order asc, id asc
            """.trimIndent(),
            mapOf("documentId" to documentId.value),
            ::mapChunk
        )

    @Transactional(readOnly = true)
    override fun search(query: KnowledgeSearchQuery): KnowledgeSearchPage {
        val page = query.page.coerceAtLeast(0)
        val size = query.size.coerceIn(1, 100)
        val normalizedQuery = query.query.trim().lowercase()
        val terms = normalizedQuery.split(Regex("\\s+")).filter { it.length >= 2 }.distinct().take(8)
        val params = MapSqlParameterSource().addValue("pattern", "%$normalizedQuery%")
        val termFilters = terms.mapIndexed { index, term ->
            params.addValue("term$index", "%$term%")
            "(lower(d.title) like :term$index or lower(c.text) like :term$index or exists (select 1 from unnest(m.tags) tag where tag like :term$index))"
        }
        val textFilter = if (termFilters.isEmpty()) {
            "(lower(d.title) like :pattern or lower(c.text) like :pattern or exists (select 1 from unnest(m.tags) tag where tag like :pattern))"
        } else {
            "(${termFilters.joinToString(" or ")})"
        }
        val where = mutableListOf(textFilter)
        query.hotelId?.let { where += "d.hotel_id = :hotelId"; params.addValue("hotelId", it) }
        query.category?.let { where += "d.category = :category"; params.addValue("category", it.name) }
        query.tag?.let { where += ":tag = any(m.tags)"; params.addValue("tag", KnowledgeMetadata.normalizeTag(it)) }
        val rows = jdbcTemplate.query(
            """
            select d.id as document_id, c.id as chunk_id, c.chunk_order, d.title, d.category, d.source, d.language,
                   c.heading, c.text, m.tags, d.updated_at
            from knowledge_chunk c
            join knowledge_document d on d.id = c.document_id
            left join knowledge_metadata m on m.document_id = d.id
            where ${where.joinToString(" and ")}
            order by d.updated_at desc, c.chunk_order asc, c.id asc
            """.trimIndent(),
            params
        ) { rs, _ -> mapSearchCandidate(rs, terms) }
            .sortedWith(compareByDescending<KnowledgeSearchResult> { it.rank }.thenByDescending { it.updatedAt }.thenBy { it.documentId.value }.thenBy { it.chunkId.value })
        val total = rows.size.toLong()
        val content = rows.drop(page * size).take(size)
        return KnowledgeSearchPage(content, page, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    @Transactional(readOnly = true)
    override fun findChunksNeedingEmbeddings(providerId: KnowledgeEmbeddingProviderId, modelId: String, limit: Int): List<com.hotelopai.knowledge.application.KnowledgeEmbeddingRequest> =
        jdbcTemplate.query(
            """
            select c.id, c.text, e.status, e.content_fingerprint
            from knowledge_chunk c
            left join knowledge_chunk_embedding e
              on e.chunk_id = c.id
             and e.provider_id = :providerId
             and e.model_id = :modelId
            order by c.updated_at asc, c.chunk_order asc, c.id asc
            """.trimIndent(),
            mapOf("providerId" to providerId.value, "modelId" to modelId)
        ) { rs, _ ->
            val text = rs.getString("text")
            val fingerprint = KnowledgeEmbeddingService.fingerprint(text)
            val status = rs.getString("status")
            val storedFingerprint = rs.getString("content_fingerprint")
            if (status == null || status in setOf("FAILED", "STALE") || storedFingerprint != fingerprint) {
                com.hotelopai.knowledge.application.KnowledgeEmbeddingRequest(
                    chunkId = KnowledgeChunkId(rs.getObject("id", UUID::class.java)),
                    text = text,
                    contentFingerprint = fingerprint
                )
            } else {
                null
            }
        }.filterNotNull().take(limit.coerceIn(1, 100))

    @Transactional(readOnly = true)
    override fun findEmbedding(chunkId: KnowledgeChunkId, providerId: KnowledgeEmbeddingProviderId, modelId: String): KnowledgeEmbeddingRecord? =
        jdbcTemplate.query(
            """
            select *
            from knowledge_chunk_embedding
            where chunk_id = :chunkId and provider_id = :providerId and model_id = :modelId
            """.trimIndent(),
            mapOf("chunkId" to chunkId.value, "providerId" to providerId.value, "modelId" to modelId),
            ::mapEmbedding
        ).firstOrNull()

    override fun upsertReady(record: KnowledgeEmbeddingRecord): KnowledgeEmbeddingRecord {
        jdbcTemplate.update(
            """
            insert into knowledge_chunk_embedding (
                chunk_id, provider_id, model_id, embedding_dimension, embedding_vector,
                content_fingerprint, generated_at, status, failure_category,
                attempt_count, next_attempt_at, created_at, updated_at, version
            ) values (
                :chunkId, :providerId, :modelId, :dimension, :vector,
                :fingerprint, :generatedAt, :status, null,
                :attemptCount, null, :createdAt, :updatedAt, :version
            )
            on conflict (chunk_id, provider_id, model_id) do update set
                embedding_dimension = :dimension,
                embedding_vector = :vector,
                content_fingerprint = :fingerprint,
                generated_at = :generatedAt,
                status = :status,
                failure_category = null,
                attempt_count = 0,
                next_attempt_at = null,
                updated_at = :updatedAt,
                version = knowledge_chunk_embedding.version + 1
            """.trimIndent(),
            embeddingParams(record)
        )
        return requireNotNull(findEmbedding(record.chunkId, record.providerId, record.modelId))
    }

    override fun markFailed(record: KnowledgeEmbeddingRecord): KnowledgeEmbeddingRecord {
        jdbcTemplate.update(
            """
            insert into knowledge_chunk_embedding (
                chunk_id, provider_id, model_id, embedding_dimension, embedding_vector,
                content_fingerprint, generated_at, status, failure_category,
                attempt_count, next_attempt_at, created_at, updated_at, version
            ) values (
                :chunkId, :providerId, :modelId, :dimension, null,
                :fingerprint, null, :status, :failureCategory,
                :attemptCount, null, :createdAt, :updatedAt, :version
            )
            on conflict (chunk_id, provider_id, model_id) do update set
                status = :status,
                failure_category = :failureCategory,
                attempt_count = knowledge_chunk_embedding.attempt_count + 1,
                updated_at = :updatedAt,
                version = knowledge_chunk_embedding.version + 1
            """.trimIndent(),
            embeddingParams(record)
        )
        return requireNotNull(findEmbedding(record.chunkId, record.providerId, record.modelId))
    }

    override fun markDocumentStale(documentId: KnowledgeDocumentId, providerId: KnowledgeEmbeddingProviderId, modelId: String, now: Instant): Int =
        jdbcTemplate.update(
            """
            update knowledge_chunk_embedding e
            set status = 'STALE',
                updated_at = :updatedAt,
                version = e.version + 1
            from knowledge_chunk c
            where c.id = e.chunk_id
              and c.document_id = :documentId
              and e.provider_id = :providerId
              and e.model_id = :modelId
            """.trimIndent(),
            mapOf(
                "documentId" to documentId.value,
                "providerId" to providerId.value,
                "modelId" to modelId,
                "updatedAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )

    @Transactional(readOnly = true)
    override fun semanticCandidates(
        providerId: KnowledgeEmbeddingProviderId,
        modelId: String,
        hotelId: UUID?,
        queryVector: KnowledgeEmbeddingVector,
        limit: Int
    ): List<KnowledgeSemanticCandidate> =
        MapSqlParameterSource()
            .addValue("providerId", providerId.value)
            .addValue("modelId", modelId)
            .apply {
                hotelId?.let { addValue("hotelId", it) }
            }
            .let { params ->
                val hotelWhere = if (hotelId == null) "" else "and d.hotel_id = :hotelId"
                jdbcTemplate.query(
                    """
                    select c.id as chunk_id, d.id as document_id, d.title, d.category, d.source, d.language,
                           c.heading, c.text, c.chunk_order, m.tags, d.updated_at, e.embedding_vector, e.content_fingerprint as embedding_fingerprint
                    from knowledge_chunk_embedding e
                    join knowledge_chunk c on c.id = e.chunk_id
                    join knowledge_document d on d.id = c.document_id
                    left join knowledge_metadata m on m.document_id = d.id
                    where e.provider_id = :providerId
                      and e.model_id = :modelId
                      and e.status = 'READY'
                      and e.embedding_vector is not null
                      $hotelWhere
                    """.trimIndent(),
                    params
                ) { rs, _ ->
                    val text = rs.getString("text")
                    val vector = doubleList(rs.getArray("embedding_vector"))
                    KnowledgeSemanticCandidate(
                        chunkId = KnowledgeChunkId(rs.getObject("chunk_id", UUID::class.java)),
                        documentId = KnowledgeDocumentId(rs.getObject("document_id", UUID::class.java)),
                        title = rs.getString("title"),
                        category = KnowledgeCategory.valueOf(rs.getString("category")),
                        source = KnowledgeSource.valueOf(rs.getString("source")),
                        language = rs.getString("language"),
                        heading = rs.getString("heading"),
                snippet = text.take(500),
                chunkOrder = rs.getInt("chunk_order"),
                tags = tags(rs),
                        updatedAt = rs.getTimestamp("updated_at").toInstant(),
                        similarity = if (rs.getString("embedding_fingerprint") == KnowledgeEmbeddingService.fingerprint(text)) {
                            cosine(queryVector.values, vector)
                        } else {
                            -2.0
                        }
                    )
                }
            }.filter { it.similarity >= -1.0 }
            .sortedWith(compareByDescending<KnowledgeSemanticCandidate> { it.similarity }.thenByDescending { it.updatedAt }.thenBy { it.documentId.value }.thenBy { it.chunkId.value })
            .take(limit.coerceIn(1, 100))

    @Transactional(readOnly = true)
    override fun failed(providerId: KnowledgeEmbeddingProviderId, modelId: String, limit: Int): List<KnowledgeEmbeddingRecord> =
        jdbcTemplate.query(
            """
            select *
            from knowledge_chunk_embedding
            where provider_id = :providerId and model_id = :modelId and status = 'FAILED'
            order by updated_at asc, chunk_id asc
            limit :limit
            """.trimIndent(),
            mapOf("providerId" to providerId.value, "modelId" to modelId, "limit" to limit.coerceIn(1, 100)),
            ::mapEmbedding
        )

    @Transactional(readOnly = true)
    override fun counts(providerId: KnowledgeEmbeddingProviderId, modelId: String): Map<KnowledgeEmbeddingStatus, Long> =
        jdbcTemplate.query(
            """
            select status, count(*) as count
            from knowledge_chunk_embedding
            where provider_id = :providerId and model_id = :modelId
            group by status
            """.trimIndent(),
            mapOf("providerId" to providerId.value, "modelId" to modelId)
        ) { rs, _ -> KnowledgeEmbeddingStatus.valueOf(rs.getString("status")) to rs.getLong("count") }.toMap()

    @Transactional(readOnly = true)
    override fun countChunks(): Long =
        jdbcTemplate.queryForObject("select count(*) from knowledge_chunk", emptyMap<String, Any>(), Long::class.java) ?: 0L

    override fun saveDiagnostic(record: KnowledgeEmbeddingDiagnosticRecord): KnowledgeEmbeddingDiagnosticRecord {
        jdbcTemplate.update(
            """
            insert into knowledge_embedding_provider_diagnostic (
                id, provider_id, model_id, diagnostic_type, outcome, readiness,
                failure_category, latency_band, batch_size, generated_at, created_at
            ) values (
                :id, :providerId, :modelId, :diagnosticType, :outcome, :readiness,
                :failureCategory, :latencyBand, :batchSize, :generatedAt, :createdAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", record.id)
                .addValue("providerId", record.providerId)
                .addValue("modelId", record.modelId)
                .addValue("diagnosticType", record.diagnosticType.name)
                .addValue("outcome", record.outcome.name)
                .addValue("readiness", record.readiness.name)
                .addValue("failureCategory", record.failureCategory?.name)
                .addValue("latencyBand", record.latencyBand)
                .addValue("batchSize", record.batchSize)
                .addValue("generatedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(record.generatedAt)))
                .addValue("createdAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(record.createdAt)))
        )
        return requireNotNull(findDiagnostic(record.id))
    }

    @Transactional(readOnly = true)
    override fun diagnostics(providerId: String?, limit: Int, offset: Int): List<KnowledgeEmbeddingDiagnosticRecord> {
        val params = MapSqlParameterSource()
            .addValue("limit", limit.coerceIn(1, 100))
            .addValue("offset", offset.coerceAtLeast(0))
        val where = if (providerId.isNullOrBlank()) "" else "where provider_id = :providerId".also { params.addValue("providerId", providerId) }
        return jdbcTemplate.query(
            """
            select *
            from knowledge_embedding_provider_diagnostic
            $where
            order by created_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapDiagnostic
        )
    }

    @Transactional(readOnly = true)
    override fun findDiagnostic(id: UUID): KnowledgeEmbeddingDiagnosticRecord? =
        jdbcTemplate.query(
            "select * from knowledge_embedding_provider_diagnostic where id = :id",
            mapOf("id" to id),
            ::mapDiagnostic
        ).firstOrNull()

    override fun cleanupDiagnostics(before: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            delete from knowledge_embedding_provider_diagnostic
            where id in (
                select id
                from knowledge_embedding_provider_diagnostic
                where created_at < :before
                order by created_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf("before" to Timestamp.from(PersistenceInstant.toPersistencePrecision(before)), "limit" to limit.coerceIn(1, 1_000))
        )

    override fun getOrCreateScheduleState(scheduleId: String, now: Instant): KnowledgeEmbeddingScheduleState {
        findScheduleState(scheduleId)?.let { return it }
        jdbcTemplate.update(
            """
            insert into knowledge_embedding_schedule_state (schedule_id, paused, updated_at)
            values (:scheduleId, false, :now)
            on conflict (schedule_id) do nothing
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId, "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
        )
        return requireNotNull(findScheduleState(scheduleId))
    }

    override fun markSchedulePaused(scheduleId: String, now: Instant): KnowledgeEmbeddingScheduleState {
        jdbcTemplate.update(
            """
            insert into knowledge_embedding_schedule_state (schedule_id, paused, paused_at, updated_at)
            values (:scheduleId, true, :now, :now)
            on conflict (schedule_id) do update set paused = true, paused_at = :now, updated_at = :now
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId, "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
        )
        return requireNotNull(findScheduleState(scheduleId))
    }

    override fun markScheduleResumed(scheduleId: String, now: Instant): KnowledgeEmbeddingScheduleState {
        jdbcTemplate.update(
            """
            insert into knowledge_embedding_schedule_state (schedule_id, paused, resumed_at, updated_at)
            values (:scheduleId, false, :now, :now)
            on conflict (schedule_id) do update set paused = false, resumed_at = :now, updated_at = :now
            """.trimIndent(),
            mapOf("scheduleId" to scheduleId, "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
        )
        return requireNotNull(findScheduleState(scheduleId))
    }

    override fun recordScheduleAttempt(
        scheduleId: String,
        summary: KnowledgeEmbeddingBatchSummary,
        now: Instant,
        failure: KnowledgeEmbeddingFailureCategory?
    ): KnowledgeEmbeddingScheduleState {
        jdbcTemplate.update(
            """
            insert into knowledge_embedding_schedule_state (
                schedule_id, paused, last_attempted_at, last_successful_at,
                last_embedded_count, last_failure_category, updated_at
            ) values (
                :scheduleId, false, :now, :lastSuccessfulAt, :embedded, :failureCategory, :now
            )
            on conflict (schedule_id) do update set
                last_attempted_at = :now,
                last_successful_at = :lastSuccessfulAt,
                last_embedded_count = :embedded,
                last_failure_category = :failureCategory,
                updated_at = :now
            """.trimIndent(),
            mapOf(
                "scheduleId" to scheduleId,
                "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)),
                "lastSuccessfulAt" to if (failure == null) Timestamp.from(PersistenceInstant.toPersistencePrecision(now)) else null,
                "embedded" to summary.embedded,
                "failureCategory" to failure?.name
            )
        )
        return requireNotNull(findScheduleState(scheduleId))
    }

    override fun save(documentId: KnowledgeDocumentId, metadata: KnowledgeMetadata, now: Instant) {
        val normalized = metadata.normalized()
        jdbcTemplate.update(
            """
            insert into knowledge_metadata (document_id, tags, attributes, created_at, updated_at, version)
            values (:documentId, :tags, cast(:attributes as jsonb), :createdAt, :updatedAt, 0)
            on conflict (document_id) do update set
                tags = :tags,
                attributes = cast(:attributes as jsonb),
                updated_at = :updatedAt,
                version = knowledge_metadata.version + 1
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("documentId", documentId.value)
                .addValue("tags", normalized.tags.toTypedArray())
                .addValue("attributes", jsonb(normalized.attributes))
                .addValue("createdAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
                .addValue("updatedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
        )
    }

    @Transactional(readOnly = true)
    override fun findMetadata(documentId: KnowledgeDocumentId): KnowledgeMetadata? =
        jdbcTemplate.query(
            "select tags, attributes from knowledge_metadata where document_id = :documentId",
            mapOf("documentId" to documentId.value)
        ) { rs, _ -> KnowledgeMetadata(tags(rs), attributes(rs.getString("attributes"))).normalized() }.firstOrNull()

    override fun saveEvaluationRun(run: KnowledgeRetrievalEvaluationRun): KnowledgeRetrievalEvaluationRun {
        jdbcTemplate.update(
            """
            insert into knowledge_retrieval_evaluation_run (
                id, name, status, case_count, k_value, modes, started_at,
                completed_at, failure_category, created_at
            ) values (
                :id, :name, :status, :caseCount, :kValue, :modes, :startedAt,
                :completedAt, :failureCategory, :createdAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", run.id)
                .addValue("name", run.name)
                .addValue("status", run.status.name)
                .addValue("caseCount", run.caseCount)
                .addValue("kValue", run.k)
                .addValue("modes", run.modes.map { it.name }.toTypedArray())
                .addValue("startedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(run.startedAt)))
                .addValue("completedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(run.completedAt)))
                .addValue("failureCategory", run.failureCategory?.name)
                .addValue("createdAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(run.completedAt)))
        )
        run.metrics.forEach { metric ->
            jdbcTemplate.update(
                """
                insert into knowledge_retrieval_evaluation_metric (
                    run_id, mode, precision_at_k, recall_at_k, mean_reciprocal_rank,
                    ndcg, hit_rate, average_latency_millis, average_retrieved_chunks, score_band
                ) values (
                    :runId, :mode, :precisionAtK, :recallAtK, :mrr,
                    :ndcg, :hitRate, :averageLatencyMillis, :averageRetrievedChunks, :scoreBand
                )
                """.trimIndent(),
                mapOf(
                    "runId" to run.id,
                    "mode" to metric.mode.name,
                    "precisionAtK" to metric.precisionAtK,
                    "recallAtK" to metric.recallAtK,
                    "mrr" to metric.meanReciprocalRank,
                    "ndcg" to metric.normalizedDiscountedCumulativeGain,
                    "hitRate" to metric.hitRate,
                    "averageLatencyMillis" to metric.averageLatencyMillis,
                    "averageRetrievedChunks" to metric.averageRetrievedChunks,
                    "scoreBand" to metric.scoreBand
                )
            )
        }
        return requireNotNull(evaluationRun(run.id))
    }

    @Transactional(readOnly = true)
    override fun evaluationRuns(limit: Int, offset: Int): List<KnowledgeRetrievalEvaluationRun> =
        jdbcTemplate.query(
            """
            select *
            from knowledge_retrieval_evaluation_run
            order by created_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            mapOf("limit" to limit.coerceIn(1, 100), "offset" to offset.coerceAtLeast(0)),
            ::mapEvaluationRun
        ).map { it.withEvaluationMetrics() }

    @Transactional(readOnly = true)
    override fun evaluationRun(id: UUID): KnowledgeRetrievalEvaluationRun? =
        jdbcTemplate.query(
            "select * from knowledge_retrieval_evaluation_run where id = :id",
            mapOf("id" to id),
            ::mapEvaluationRun
        ).firstOrNull()?.withEvaluationMetrics()

    @Transactional(readOnly = true)
    override fun latestEvaluationRun(): KnowledgeRetrievalEvaluationRun? =
        jdbcTemplate.query(
            """
            select *
            from knowledge_retrieval_evaluation_run
            order by created_at desc, id desc
            limit 1
            """.trimIndent(),
            emptyMap<String, Any>(),
            ::mapEvaluationRun
        ).firstOrNull()?.withEvaluationMetrics()

    @Transactional(readOnly = true)
    override fun findDuplicate(hotelId: UUID?, requestFingerprint: String, since: Instant): KnowledgeAnswer? {
        val params = MapSqlParameterSource()
            .addValue("fingerprint", requestFingerprint)
            .addValue("since", Timestamp.from(PersistenceInstant.toPersistencePrecision(since)))
        val hotelWhere = if (hotelId == null) {
            "hotel_id is null"
        } else {
            params.addValue("hotelId", hotelId)
            "hotel_id = :hotelId"
        }
        return jdbcTemplate.query(
            """
            select *
            from knowledge_answer_history
            where $hotelWhere
              and request_fingerprint = :fingerprint
              and created_at >= :since
            order by created_at desc, id desc
            limit 1
            """.trimIndent(),
            params,
            ::mapAnswer
        ).firstOrNull()
    }

    override fun saveAnswer(answer: KnowledgeAnswer): KnowledgeAnswer {
        jdbcTemplate.update(
            """
            insert into knowledge_answer_history (
                id, hotel_id, provider_id, model_id, prompt_template_id, prompt_version,
                retrieval_mode, context_schema_version, status, confidence, answer_text,
                citation_refs, request_fingerprint, failure_category, actor_user_id, created_at, updated_at
            ) values (
                :id, :hotelId, :providerId, :modelId, :promptTemplateId, :promptVersion,
                :retrievalMode, :contextSchemaVersion, :status, :confidence, :answerText,
                cast(:citationRefs as jsonb), :requestFingerprint, :failureCategory, :actorUserId, :createdAt, :updatedAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", answer.id.value)
                .addValue("hotelId", answer.hotelId)
                .addValue("providerId", answer.providerId)
                .addValue("modelId", answer.modelId)
                .addValue("promptTemplateId", answer.promptTemplateId)
                .addValue("promptVersion", answer.promptVersion)
                .addValue("retrievalMode", answer.retrievalMode.name)
                .addValue("contextSchemaVersion", answer.contextSchemaVersion)
                .addValue("status", answer.status.name)
                .addValue("confidence", answer.confidence?.name)
                .addValue("answerText", answer.answerText)
                .addValue("citationRefs", objectMapper.writeValueAsString(answer.citations))
                .addValue("requestFingerprint", answer.requestFingerprint)
                .addValue("failureCategory", answer.failureCategory?.name)
                .addValue("actorUserId", answer.actorUserId)
                .addValue("createdAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(answer.createdAt)))
                .addValue("updatedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(answer.updatedAt)))
        )
        return requireNotNull(answer(answer.id, answer.hotelId))
    }

    @Transactional(readOnly = true)
    override fun answer(id: KnowledgeAnswerId, hotelId: UUID?): KnowledgeAnswer? {
        val params = MapSqlParameterSource().addValue("id", id.value)
        val hotelWhere = if (hotelId == null) "" else "and hotel_id = :hotelId".also { params.addValue("hotelId", hotelId) }
        return jdbcTemplate.query(
            """
            select *
            from knowledge_answer_history
            where id = :id $hotelWhere
            """.trimIndent(),
            params,
            ::mapAnswer
        ).firstOrNull()
    }

    @Transactional(readOnly = true)
    override fun answers(hotelId: UUID?, limit: Int, offset: Int): List<KnowledgeAnswer> {
        val params = MapSqlParameterSource()
            .addValue("limit", limit.coerceIn(1, 100))
            .addValue("offset", offset.coerceAtLeast(0))
        val where = if (hotelId == null) "" else "where hotel_id = :hotelId".also { params.addValue("hotelId", hotelId) }
        return jdbcTemplate.query(
            """
            select *
            from knowledge_answer_history
            $where
            order by created_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapAnswer
        )
    }

    override fun cleanupAnswers(before: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            delete from knowledge_answer_history
            where id in (
                select id
                from knowledge_answer_history
                where created_at < :before
                order by created_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf("before" to Timestamp.from(PersistenceInstant.toPersistencePrecision(before)), "limit" to limit.coerceIn(1, 1_000))
        )

    @Transactional(readOnly = true)
    override fun countAnswers(hotelId: UUID?, actorUserId: UUID?, since: Instant): Long {
        val params = MapSqlParameterSource()
            .addValue("since", Timestamp.from(PersistenceInstant.toPersistencePrecision(since)))
            .addValue("actorUserId", actorUserId)
        val where = mutableListOf("created_at >= :since")
        hotelId?.let { where += "hotel_id = :hotelId"; params.addValue("hotelId", it) }
        actorUserId?.let { where += "actor_user_id = :actorUserId" }
        return jdbcTemplate.queryForObject(
            "select count(*) from knowledge_answer_history where ${where.joinToString(" and ")}",
            params,
            Long::class.java
        ) ?: 0L
    }

    override fun saveFeedback(feedback: KnowledgeAnswerFeedback): KnowledgeAnswerFeedback {
        jdbcTemplate.update(
            """
            insert into knowledge_answer_feedback (answer_id, feedback_type, actor_user_id, created_at)
            values (:answerId, :feedbackType, :actorUserId, :createdAt)
            on conflict (answer_id, actor_user_id, feedback_type) do update set
                created_at = :createdAt
            """.trimIndent(),
            mapOf(
                "answerId" to feedback.answerId.value,
                "feedbackType" to feedback.feedbackType.name,
                "actorUserId" to feedback.actorUserId,
                "createdAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(feedback.createdAt))
            )
        )
        return feedback
    }

    @Transactional(readOnly = true)
    override fun feedbackFor(answerId: KnowledgeAnswerId): List<KnowledgeAnswerFeedback> =
        jdbcTemplate.query(
            """
            select *
            from knowledge_answer_feedback
            where answer_id = :answerId
            order by created_at desc, feedback_type asc
            """.trimIndent(),
            mapOf("answerId" to answerId.value)
        ) { rs, _ ->
            KnowledgeAnswerFeedback(
                answerId = KnowledgeAnswerId(rs.getObject("answer_id", UUID::class.java)),
                feedbackType = KnowledgeAnswerFeedbackType.valueOf(rs.getString("feedback_type")),
                actorUserId = rs.getObject("actor_user_id", UUID::class.java),
                createdAt = rs.getTimestamp("created_at").toInstant()
            )
        }

    override fun acquireAnswerRequestLifecycle(
        hotelId: UUID?,
        actorUserId: UUID?,
        providerId: String,
        modelId: String,
        retrievalMode: KnowledgeSearchMode,
        requestFingerprint: String,
        inFlightLimit: Int,
        abandonedBefore: Instant,
        now: Instant
    ): KnowledgeAnswerRequestLifecycle? {
        val hotel = hotelId ?: return null
        val actor = actorUserId ?: return null
        jdbcTemplate.update(
            """
            insert into knowledge_answer_inflight_scope (hotel_id, actor_user_id, updated_at)
            values (:hotelId, :actorUserId, :now)
            on conflict (hotel_id, actor_user_id) do nothing
            """.trimIndent(),
            mapOf("hotelId" to hotel, "actorUserId" to actor, "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
        )
        jdbcTemplate.queryForObject(
            """
            select updated_at
            from knowledge_answer_inflight_scope
            where hotel_id = :hotelId and actor_user_id = :actorUserId
            for update
            """.trimIndent(),
            mapOf("hotelId" to hotel, "actorUserId" to actor),
            Timestamp::class.java
        )
        recoverAbandonedAnswerRequests(abandonedBefore, now, 1_000)
        val active = countActiveAnswerRequests(hotel, actor)
        if (active >= inFlightLimit) return null
        val requestId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            insert into knowledge_answer_request_lifecycle (
                request_id, hotel_id, actor_user_id, provider_id, model_id, retrieval_mode,
                request_fingerprint, status, requested_at, updated_at
            ) values (
                :requestId, :hotelId, :actorUserId, :providerId, :modelId, :retrievalMode,
                :fingerprint, :status, :requestedAt, :updatedAt
            )
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "hotelId" to hotel,
                "actorUserId" to actor,
                "providerId" to providerId,
                "modelId" to modelId,
                "retrievalMode" to retrievalMode.name,
                "fingerprint" to requestFingerprint,
                "status" to KnowledgeAnswerRequestStatus.REQUESTED.name,
                "requestedAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)),
                "updatedAt" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now))
            )
        )
        return answerRequest(requestId, hotel)
    }

    override fun transitionAnswerRequest(
        requestId: UUID,
        status: KnowledgeAnswerRequestStatus,
        now: Instant,
        answerId: KnowledgeAnswerId?,
        failureCategory: KnowledgeAnswerFailureCategory?,
        citationCountBand: String?,
        latencyBand: String?
    ): KnowledgeAnswerRequestLifecycle? {
        jdbcTemplate.update(
            """
            update knowledge_answer_request_lifecycle
            set answer_id = coalesce(:answerId, answer_id),
                status = :status,
                completed_at = case when :terminal then :now else completed_at end,
                updated_at = :now,
                failure_category = :failureCategory,
                citation_count_band = coalesce(:citationCountBand, citation_count_band),
                latency_band = coalesce(:latencyBand, latency_band)
            where request_id = :requestId
            """.trimIndent(),
            mapOf(
                "requestId" to requestId,
                "answerId" to answerId?.value,
                "status" to status.name,
                "terminal" to (status in TERMINAL_ANSWER_REQUEST_STATUSES),
                "now" to Timestamp.from(PersistenceInstant.toPersistencePrecision(now)),
                "failureCategory" to failureCategory?.name,
                "citationCountBand" to citationCountBand,
                "latencyBand" to latencyBand
            )
        )
        return answerRequest(requestId, null)
    }

    @Transactional(readOnly = true)
    override fun answerRequest(requestId: UUID, hotelId: UUID?): KnowledgeAnswerRequestLifecycle? {
        val params = MapSqlParameterSource().addValue("requestId", requestId)
        val hotelWhere = if (hotelId == null) "" else "and hotel_id = :hotelId".also { params.addValue("hotelId", hotelId) }
        return jdbcTemplate.query(
            "select * from knowledge_answer_request_lifecycle where request_id = :requestId $hotelWhere",
            params,
            ::mapAnswerRequestLifecycle
        ).firstOrNull()
    }

    @Transactional(readOnly = true)
    override fun activeAnswerRequests(hotelId: UUID?, limit: Int, offset: Int): List<KnowledgeAnswerRequestLifecycle> {
        val params = MapSqlParameterSource()
            .addValue("limit", limit.coerceIn(1, 100))
            .addValue("offset", offset.coerceAtLeast(0))
        val where = mutableListOf("status = any(:statuses)")
        params.addValue("statuses", ACTIVE_ANSWER_REQUEST_STATUSES.map { it.name }.toTypedArray())
        hotelId?.let { where += "hotel_id = :hotelId"; params.addValue("hotelId", it) }
        return jdbcTemplate.query(
            """
            select *
            from knowledge_answer_request_lifecycle
            where ${where.joinToString(" and ")}
            order by requested_at asc, request_id asc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapAnswerRequestLifecycle
        )
    }

    @Transactional(readOnly = true)
    override fun countActiveAnswerRequests(hotelId: UUID?, actorUserId: UUID?): Long {
        val params = MapSqlParameterSource().addValue("statuses", ACTIVE_ANSWER_REQUEST_STATUSES.map { it.name }.toTypedArray())
        val where = mutableListOf("status = any(:statuses)")
        hotelId?.let { where += "hotel_id = :hotelId"; params.addValue("hotelId", it) }
        actorUserId?.let { where += "actor_user_id = :actorUserId"; params.addValue("actorUserId", it) }
        return jdbcTemplate.queryForObject(
            "select count(*) from knowledge_answer_request_lifecycle where ${where.joinToString(" and ")}",
            params,
            Long::class.java
        ) ?: 0L
    }

    @Transactional(readOnly = true)
    override fun countAbandonedAnswerRequests(hotelId: UUID?): Long {
        val params = MapSqlParameterSource()
        val where = mutableListOf("status = 'ABANDONED'")
        hotelId?.let { where += "hotel_id = :hotelId"; params.addValue("hotelId", it) }
        return jdbcTemplate.queryForObject(
            "select count(*) from knowledge_answer_request_lifecycle where ${where.joinToString(" and ")}",
            params,
            Long::class.java
        ) ?: 0L
    }

    override fun recoverAbandonedAnswerRequests(before: Instant, now: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            update knowledge_answer_request_lifecycle
            set status = 'ABANDONED',
                completed_at = :now,
                updated_at = :now,
                failure_category = 'ABANDONED',
                latency_band = 'abandoned'
            where request_id in (
                select request_id
                from knowledge_answer_request_lifecycle
                where status = any(:statuses)
                  and updated_at < :before
                order by updated_at asc, request_id asc
                limit :limit
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("statuses", ACTIVE_ANSWER_REQUEST_STATUSES.map { it.name }.toTypedArray())
                .addValue("before", Timestamp.from(PersistenceInstant.toPersistencePrecision(before)))
                .addValue("now", Timestamp.from(PersistenceInstant.toPersistencePrecision(now)))
                .addValue("limit", limit.coerceIn(1, 1_000))
        )

    override fun cleanupAnswerRequestLifecycles(before: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            delete from knowledge_answer_request_lifecycle
            where request_id in (
                select request_id
                from knowledge_answer_request_lifecycle
                where not (status = any(:statuses))
                  and updated_at < :before
                order by updated_at asc, request_id asc
                limit :limit
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("statuses", ACTIVE_ANSWER_REQUEST_STATUSES.map { it.name }.toTypedArray())
                .addValue("before", Timestamp.from(PersistenceInstant.toPersistencePrecision(before)))
                .addValue("limit", limit.coerceIn(1, 1_000))
        )

    @Transactional(readOnly = true)
    override fun answerStatusCounts(hotelId: UUID?, since: Instant): KnowledgeAnswerStatusCounts {
        val rows = answerLifecycleGrouped(hotelId, since, "status")
        return KnowledgeAnswerStatusCounts(
            answered = rows[KnowledgeAnswerRequestStatus.COMPLETED.name] ?: 0,
            insufficientContext = rows[KnowledgeAnswerRequestStatus.INSUFFICIENT_CONTEXT.name] ?: 0,
            failed = listOf(KnowledgeAnswerRequestStatus.FAILED.name, KnowledgeAnswerRequestStatus.REJECTED.name, KnowledgeAnswerRequestStatus.ABANDONED.name).sumOf { rows[it] ?: 0 }
        )
    }

    @Transactional(readOnly = true)
    override fun answerCitationBands(hotelId: UUID?, since: Instant): Map<String, Long> =
        answerLifecycleGrouped(hotelId, since, "citation_count_band")

    @Transactional(readOnly = true)
    override fun answerLatencyBands(hotelId: UUID?, since: Instant): Map<String, Long> =
        answerLifecycleGrouped(hotelId, since, "latency_band")

    @Transactional(readOnly = true)
    override fun answerFailureCategories(hotelId: UUID?, since: Instant): Map<KnowledgeAnswerFailureCategory, Long> =
        answerLifecycleGrouped(hotelId, since, "failure_category")
            .filterKeys { it.isNotBlank() }
            .mapKeys { KnowledgeAnswerFailureCategory.valueOf(it.key) }

    @Transactional(readOnly = true)
    override fun feedbackAnalytics(hotelId: UUID?, since: Instant): KnowledgeAnswerFeedbackAnalytics {
        val params = MapSqlParameterSource().addValue("since", Timestamp.from(PersistenceInstant.toPersistencePrecision(since)))
        val where = mutableListOf("f.created_at >= :since")
        hotelId?.let { where += "a.hotel_id = :hotelId"; params.addValue("hotelId", it) }
        val rows = jdbcTemplate.query(
            """
            select f.feedback_type, a.provider_id, a.retrieval_mode,
                   coalesce(l.citation_count_band, 'unknown') as citation_count_band,
                   count(*) as count
            from knowledge_answer_feedback f
            join knowledge_answer_history a on a.id = f.answer_id
            left join knowledge_answer_request_lifecycle l on l.answer_id = a.id
            where ${where.joinToString(" and ")}
            group by f.feedback_type, a.provider_id, a.retrieval_mode, coalesce(l.citation_count_band, 'unknown')
            """.trimIndent(),
            params
        ) { rs, _ ->
            FeedbackAnalyticsRow(
                KnowledgeAnswerFeedbackType.valueOf(rs.getString("feedback_type")),
                rs.getString("provider_id"),
                KnowledgeSearchMode.valueOf(rs.getString("retrieval_mode")),
                rs.getString("citation_count_band"),
                rs.getLong("count")
            )
        }
        val total = rows.sumOf { it.count }.toDouble().coerceAtLeast(1.0)
        return KnowledgeAnswerFeedbackAnalytics(
            counts = rows.groupingBy { it.type }.fold(0L) { acc, row -> acc + row.count },
            rates = rows.groupingBy { it.type }.fold(0L) { acc, row -> acc + row.count }.mapValues { it.value / total },
            providerBreakdown = rows.groupBy { it.providerId }.mapValues { (_, values) -> values.groupingBy { it.type }.fold(0L) { acc, row -> acc + row.count } },
            retrievalModeBreakdown = rows.groupBy { it.retrievalMode }.mapValues { (_, values) -> values.groupingBy { it.type }.fold(0L) { acc, row -> acc + row.count } },
            citationCountBandBreakdown = rows.groupBy { it.citationBand }.mapValues { (_, values) -> values.groupingBy { it.type }.fold(0L) { acc, row -> acc + row.count } }
        )
    }

    override fun saveAnswerProviderDiagnostic(record: KnowledgeAnswerProviderDiagnosticRecord): KnowledgeAnswerProviderDiagnosticRecord {
        jdbcTemplate.update(
            """
            insert into knowledge_answer_provider_diagnostic (
                id, provider_id, diagnostic_type, trigger_type, started_at, completed_at,
                outcome, failure_category, latency_band, retry_count, response_validation_outcome,
                prompt_template_id, prompt_version, model_id, environment_class, created_at
            ) values (
                :id, :providerId, :diagnosticType, :triggerType, :startedAt, :completedAt,
                :outcome, :failureCategory, :latencyBand, :retryCount, :validationOutcome,
                :promptTemplateId, :promptVersion, :modelId, :environmentClass, :createdAt
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", record.id)
                .addValue("providerId", record.providerId)
                .addValue("diagnosticType", record.diagnosticType.name)
                .addValue("triggerType", record.triggerType.name)
                .addValue("startedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(record.startedAt)))
                .addValue("completedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(record.completedAt)))
                .addValue("outcome", record.outcome.name)
                .addValue("failureCategory", record.failureCategory?.name)
                .addValue("latencyBand", record.latencyBand)
                .addValue("retryCount", record.retryCount)
                .addValue("validationOutcome", record.responseValidationOutcome.name)
                .addValue("promptTemplateId", record.promptTemplateId)
                .addValue("promptVersion", record.promptVersion)
                .addValue("modelId", record.modelId)
                .addValue("environmentClass", record.environmentClass)
                .addValue("createdAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(record.createdAt)))
        )
        return requireNotNull(answerProviderDiagnostic(record.id))
    }

    @Transactional(readOnly = true)
    override fun answerProviderDiagnostics(providerId: String?, limit: Int, offset: Int): KnowledgeAnswerProviderDiagnosticPage {
        val params = MapSqlParameterSource()
            .addValue("limit", limit.coerceIn(1, 100))
            .addValue("offset", offset.coerceAtLeast(0))
        val where = if (providerId.isNullOrBlank()) "" else "where provider_id = :providerId".also { params.addValue("providerId", providerId) }
        val total = jdbcTemplate.queryForObject(
            "select count(*) from knowledge_answer_provider_diagnostic $where",
            params,
            Long::class.java
        ) ?: 0L
        val content = jdbcTemplate.query(
            """
            select *
            from knowledge_answer_provider_diagnostic
            $where
            order by created_at desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
            params,
            ::mapAnswerProviderDiagnostic
        )
        val size = limit.coerceIn(1, 100)
        return KnowledgeAnswerProviderDiagnosticPage(content, offset.coerceAtLeast(0) / size, size, total, if (total == 0L) 0 else ceil(total.toDouble() / size.toDouble()).toInt())
    }

    @Transactional(readOnly = true)
    override fun answerProviderDiagnostic(id: UUID): KnowledgeAnswerProviderDiagnosticRecord? =
        jdbcTemplate.query(
            "select * from knowledge_answer_provider_diagnostic where id = :id",
            mapOf("id" to id),
            ::mapAnswerProviderDiagnostic
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun latestAnswerProviderDiagnostic(providerId: String): KnowledgeAnswerProviderDiagnosticRecord? =
        jdbcTemplate.query(
            """
            select *
            from knowledge_answer_provider_diagnostic
            where provider_id = :providerId
            order by created_at desc, id desc
            limit 1
            """.trimIndent(),
            mapOf("providerId" to providerId),
            ::mapAnswerProviderDiagnostic
        ).firstOrNull()

    @Transactional(readOnly = true)
    override fun latestSuccessfulAnswerProviderDiagnostic(providerId: String): KnowledgeAnswerProviderDiagnosticRecord? =
        jdbcTemplate.query(
            """
            select *
            from knowledge_answer_provider_diagnostic
            where provider_id = :providerId and outcome = 'SUCCEEDED'
            order by created_at desc, id desc
            limit 1
            """.trimIndent(),
            mapOf("providerId" to providerId),
            ::mapAnswerProviderDiagnostic
        ).firstOrNull()

    override fun cleanupAnswerProviderDiagnostics(before: Instant, limit: Int): Int =
        jdbcTemplate.update(
            """
            delete from knowledge_answer_provider_diagnostic
            where id in (
                select id
                from knowledge_answer_provider_diagnostic
                where created_at < :before
                order by created_at asc, id asc
                limit :limit
            )
            """.trimIndent(),
            mapOf("before" to Timestamp.from(PersistenceInstant.toPersistencePrecision(before)), "limit" to limit.coerceIn(1, 1_000))
        )

    private fun KnowledgeDocument.withStoredMetadataAndChunks(): KnowledgeDocument =
        copy(metadata = findMetadata(id) ?: metadata, chunks = findByDocument(id))

    private fun KnowledgeRetrievalEvaluationRun.withEvaluationMetrics(): KnowledgeRetrievalEvaluationRun =
        copy(metrics = evaluationMetrics(id))

    private fun documentParams(document: KnowledgeDocument): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", document.id.value)
            .addValue("hotelId", document.hotelId)
            .addValue("title", document.title)
            .addValue("category", document.category.name)
            .addValue("source", document.source.name)
            .addValue("language", document.language)
            .addValue("originalContent", document.originalContent)
            .addValue("metadata", jsonb(document.metadata.attributes))
            .addValue("createdAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(document.createdAt)))
            .addValue("updatedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(document.updatedAt)))
            .addValue("version", document.version)

    private fun chunkParams(chunk: KnowledgeChunk): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", chunk.id.value)
            .addValue("documentId", chunk.documentId.value)
            .addValue("chunkOrder", chunk.order)
            .addValue("heading", chunk.heading)
            .addValue("text", chunk.text)
            .addValue("metadata", jsonb(chunk.metadata.attributes))
            .addValue("createdAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(chunk.createdAt)))
            .addValue("updatedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(chunk.updatedAt)))
            .addValue("version", chunk.version)

    private fun mapDocument(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeDocument =
        KnowledgeDocument(
            id = KnowledgeDocumentId(rs.getObject("id", UUID::class.java)),
            hotelId = rs.getObject("hotel_id", UUID::class.java),
            title = rs.getString("title"),
            category = KnowledgeCategory.valueOf(rs.getString("category")),
            source = KnowledgeSource.valueOf(rs.getString("source")),
            language = rs.getString("language"),
            originalContent = rs.getString("original_content"),
            metadata = KnowledgeMetadata(attributes = attributes(rs.getString("metadata"))).normalized(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version")
        )

    private fun mapChunk(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeChunk =
        KnowledgeChunk(
            id = KnowledgeChunkId(rs.getObject("id", UUID::class.java)),
            documentId = KnowledgeDocumentId(rs.getObject("document_id", UUID::class.java)),
            order = rs.getInt("chunk_order"),
            heading = rs.getString("heading"),
            text = rs.getString("text"),
            metadata = KnowledgeMetadata(attributes = attributes(rs.getString("metadata"))).normalized(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version")
        )

    private fun mapSearchCandidate(rs: ResultSet, terms: List<String>): KnowledgeSearchResult {
        val title = rs.getString("title")
        val text = rs.getString("text")
        val tags = tags(rs)
        val haystackTitle = title.lowercase()
        val haystackText = text.lowercase()
        val rank = terms.sumOf { term ->
            (if (haystackTitle.contains(term)) 10 else 0) +
                (if (tags.any { it.contains(term) }) 5 else 0) +
                Regex.escape(term).toRegex().findAll(haystackText).count()
        }.coerceAtLeast(1)
        return KnowledgeSearchResult(
            documentId = KnowledgeDocumentId(rs.getObject("document_id", UUID::class.java)),
            chunkId = KnowledgeChunkId(rs.getObject("chunk_id", UUID::class.java)),
            title = title,
            category = KnowledgeCategory.valueOf(rs.getString("category")),
            source = KnowledgeSource.valueOf(rs.getString("source")),
            language = rs.getString("language"),
            heading = rs.getString("heading"),
            snippet = text.take(500),
            chunkOrder = rs.getInt("chunk_order"),
            rank = rank,
            tags = tags,
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            score = com.hotelopai.knowledge.application.KnowledgeSearchScore(rank.toDouble(), 0.0, rank.toDouble())
        )
    }

    private fun mapEmbedding(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeEmbeddingRecord =
        KnowledgeEmbeddingRecord(
            chunkId = KnowledgeChunkId(rs.getObject("chunk_id", UUID::class.java)),
            providerId = KnowledgeEmbeddingProviderId(rs.getString("provider_id")),
            modelId = rs.getString("model_id"),
            dimension = rs.getInt("embedding_dimension"),
            vector = rs.getArray("embedding_vector")?.let { KnowledgeEmbeddingVector(doubleList(it)) },
            contentFingerprint = rs.getString("content_fingerprint"),
            generatedAt = rs.getTimestamp("generated_at")?.toInstant(),
            status = KnowledgeEmbeddingStatus.valueOf(rs.getString("status")),
            failureCategory = rs.getString("failure_category")?.let(KnowledgeEmbeddingFailureCategory::valueOf),
            attemptCount = rs.getInt("attempt_count"),
            nextAttemptAt = rs.getTimestamp("next_attempt_at")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            version = rs.getLong("version")
        )

    private fun mapDiagnostic(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeEmbeddingDiagnosticRecord =
        KnowledgeEmbeddingDiagnosticRecord(
            id = rs.getObject("id", UUID::class.java),
            providerId = rs.getString("provider_id"),
            modelId = rs.getString("model_id"),
            diagnosticType = KnowledgeEmbeddingDiagnosticType.valueOf(rs.getString("diagnostic_type")),
            outcome = KnowledgeEmbeddingDiagnosticOutcome.valueOf(rs.getString("outcome")),
            readiness = KnowledgeEmbeddingProviderReadiness.valueOf(rs.getString("readiness")),
            failureCategory = rs.getString("failure_category")?.let(KnowledgeEmbeddingFailureCategory::valueOf),
            latencyBand = rs.getString("latency_band"),
            batchSize = rs.getInt("batch_size"),
            generatedAt = rs.getTimestamp("generated_at").toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )

    private fun findScheduleState(scheduleId: String): KnowledgeEmbeddingScheduleState? =
        jdbcTemplate.query(
            "select * from knowledge_embedding_schedule_state where schedule_id = :scheduleId",
            mapOf("scheduleId" to scheduleId),
            ::mapScheduleState
        ).firstOrNull()

    private fun mapScheduleState(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeEmbeddingScheduleState =
        KnowledgeEmbeddingScheduleState(
            scheduleId = rs.getString("schedule_id"),
            paused = rs.getBoolean("paused"),
            pausedAt = rs.getTimestamp("paused_at")?.toInstant(),
            resumedAt = rs.getTimestamp("resumed_at")?.toInstant(),
            lastAttemptedAt = rs.getTimestamp("last_attempted_at")?.toInstant(),
            lastSuccessfulAt = rs.getTimestamp("last_successful_at")?.toInstant(),
            lastEmbeddedCount = rs.getInt("last_embedded_count"),
            lastFailureCategory = rs.getString("last_failure_category")?.let(KnowledgeEmbeddingFailureCategory::valueOf),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )

    private fun mapEvaluationRun(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeRetrievalEvaluationRun =
        KnowledgeRetrievalEvaluationRun(
            id = rs.getObject("id", UUID::class.java),
            name = rs.getString("name"),
            status = KnowledgeRetrievalEvaluationStatus.valueOf(rs.getString("status")),
            caseCount = rs.getInt("case_count"),
            k = rs.getInt("k_value"),
            modes = searchModes(rs.getArray("modes")),
            startedAt = rs.getTimestamp("started_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at").toInstant(),
            failureCategory = rs.getString("failure_category")?.let(KnowledgeEmbeddingFailureCategory::valueOf),
            metrics = emptyList()
        )

    private fun evaluationMetrics(runId: UUID): List<KnowledgeRetrievalMetricSummary> =
        jdbcTemplate.query(
            """
            select *
            from knowledge_retrieval_evaluation_metric
            where run_id = :runId
            order by mode asc
            """.trimIndent(),
            mapOf("runId" to runId)
        ) { rs, _ ->
            KnowledgeRetrievalMetricSummary(
                mode = com.hotelopai.knowledge.application.KnowledgeSearchMode.valueOf(rs.getString("mode")),
                precisionAtK = rs.getDouble("precision_at_k"),
                recallAtK = rs.getDouble("recall_at_k"),
                meanReciprocalRank = rs.getDouble("mean_reciprocal_rank"),
                normalizedDiscountedCumulativeGain = rs.getDouble("ndcg"),
                hitRate = rs.getDouble("hit_rate"),
                averageLatencyMillis = rs.getLong("average_latency_millis"),
                averageRetrievedChunks = rs.getDouble("average_retrieved_chunks"),
                scoreBand = rs.getString("score_band")
            )
        }

    private fun mapAnswer(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeAnswer =
        KnowledgeAnswer(
            id = KnowledgeAnswerId(rs.getObject("id", UUID::class.java)),
            hotelId = rs.getObject("hotel_id", UUID::class.java),
            providerId = rs.getString("provider_id"),
            modelId = rs.getString("model_id"),
            promptTemplateId = rs.getString("prompt_template_id"),
            promptVersion = rs.getString("prompt_version"),
            retrievalMode = com.hotelopai.knowledge.application.KnowledgeSearchMode.valueOf(rs.getString("retrieval_mode")),
            contextSchemaVersion = rs.getString("context_schema_version"),
            status = KnowledgeAnswerStatus.valueOf(rs.getString("status")),
            confidence = rs.getString("confidence")?.let(KnowledgeAnswerConfidence::valueOf),
            answerText = rs.getString("answer_text"),
            citations = citations(rs.getString("citation_refs")),
            requestFingerprint = rs.getString("request_fingerprint"),
            failureCategory = rs.getString("failure_category")?.let(KnowledgeAnswerFailureCategory::valueOf),
            actorUserId = runCatching { rs.getObject("actor_user_id", UUID::class.java) }.getOrNull(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )

    private fun mapAnswerProviderDiagnostic(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeAnswerProviderDiagnosticRecord =
        KnowledgeAnswerProviderDiagnosticRecord(
            id = rs.getObject("id", UUID::class.java),
            providerId = rs.getString("provider_id"),
            diagnosticType = KnowledgeAnswerProviderDiagnosticType.valueOf(rs.getString("diagnostic_type")),
            triggerType = KnowledgeAnswerProviderDiagnosticTrigger.valueOf(rs.getString("trigger_type")),
            startedAt = rs.getTimestamp("started_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at").toInstant(),
            outcome = KnowledgeAnswerProviderDiagnosticOutcome.valueOf(rs.getString("outcome")),
            failureCategory = rs.getString("failure_category")?.let(KnowledgeAnswerFailureCategory::valueOf),
            latencyBand = rs.getString("latency_band"),
            retryCount = rs.getInt("retry_count"),
            responseValidationOutcome = KnowledgeAnswerResponseValidationOutcome.valueOf(rs.getString("response_validation_outcome")),
            promptTemplateId = rs.getString("prompt_template_id"),
            promptVersion = rs.getString("prompt_version"),
            modelId = rs.getString("model_id"),
            environmentClass = rs.getString("environment_class"),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )

    private fun mapAnswerRequestLifecycle(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int): KnowledgeAnswerRequestLifecycle =
        KnowledgeAnswerRequestLifecycle(
            requestId = rs.getObject("request_id", UUID::class.java),
            answerId = rs.getObject("answer_id", UUID::class.java)?.let(::KnowledgeAnswerId),
            originalRequestId = rs.getObject("original_request_id", UUID::class.java),
            hotelId = rs.getObject("hotel_id", UUID::class.java),
            actorUserId = rs.getObject("actor_user_id", UUID::class.java),
            providerId = rs.getString("provider_id"),
            modelId = rs.getString("model_id"),
            retrievalMode = KnowledgeSearchMode.valueOf(rs.getString("retrieval_mode")),
            requestFingerprint = rs.getString("request_fingerprint"),
            status = KnowledgeAnswerRequestStatus.valueOf(rs.getString("status")),
            requestedAt = rs.getTimestamp("requested_at").toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            failureCategory = rs.getString("failure_category")?.let(KnowledgeAnswerFailureCategory::valueOf),
            citationCountBand = rs.getString("citation_count_band"),
            latencyBand = rs.getString("latency_band")
        )

    private fun answerLifecycleGrouped(hotelId: UUID?, since: Instant, column: String): Map<String, Long> {
        require(column in setOf("status", "citation_count_band", "latency_band", "failure_category")) { "unsupported lifecycle group column" }
        val params = MapSqlParameterSource().addValue("since", Timestamp.from(PersistenceInstant.toPersistencePrecision(since)))
        val where = mutableListOf("requested_at >= :since")
        hotelId?.let { where += "hotel_id = :hotelId"; params.addValue("hotelId", it) }
        return jdbcTemplate.query(
            """
            select coalesce($column, '') as bucket, count(*) as count
            from knowledge_answer_request_lifecycle
            where ${where.joinToString(" and ")}
            group by coalesce($column, '')
            """.trimIndent(),
            params
        ) { rs, _ -> rs.getString("bucket") to rs.getLong("count") }.toMap()
    }

    private data class FeedbackAnalyticsRow(
        val type: KnowledgeAnswerFeedbackType,
        val providerId: String,
        val retrievalMode: KnowledgeSearchMode,
        val citationBand: String,
        val count: Long
    )

    companion object {
        private val ACTIVE_ANSWER_REQUEST_STATUSES = setOf(
            KnowledgeAnswerRequestStatus.REQUESTED,
            KnowledgeAnswerRequestStatus.RETRIEVING,
            KnowledgeAnswerRequestStatus.GENERATING,
            KnowledgeAnswerRequestStatus.VALIDATING
        )
        private val TERMINAL_ANSWER_REQUEST_STATUSES = setOf(
            KnowledgeAnswerRequestStatus.COMPLETED,
            KnowledgeAnswerRequestStatus.INSUFFICIENT_CONTEXT,
            KnowledgeAnswerRequestStatus.FAILED,
            KnowledgeAnswerRequestStatus.REJECTED,
            KnowledgeAnswerRequestStatus.ABANDONED
        )
    }

    private fun embeddingParams(record: KnowledgeEmbeddingRecord): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("chunkId", record.chunkId.value)
            .addValue("providerId", record.providerId.value)
            .addValue("modelId", record.modelId)
            .addValue("dimension", record.dimension)
            .addValue("vector", record.vector?.values?.toTypedArray())
            .addValue("fingerprint", record.contentFingerprint)
            .addValue("generatedAt", record.generatedAt?.let { Timestamp.from(PersistenceInstant.toPersistencePrecision(it)) })
            .addValue("status", record.status.name)
            .addValue("failureCategory", record.failureCategory?.name)
            .addValue("attemptCount", record.attemptCount)
            .addValue("createdAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(record.createdAt)))
            .addValue("updatedAt", Timestamp.from(PersistenceInstant.toPersistencePrecision(record.updatedAt)))
            .addValue("version", record.version)

    private fun jsonb(value: Map<String, String>): String =
        objectMapper.writeValueAsString(value)

    private fun attributes(value: String?): Map<String, String> =
        if (value.isNullOrBlank()) emptyMap() else objectMapper.readValue(value, mapType)

    private fun citations(value: String?): List<KnowledgeCitation> =
        if (value.isNullOrBlank()) emptyList() else objectMapper.readValue(value, citationListType)

    private fun tags(rs: ResultSet): Set<String> {
        val array = rs.getArray("tags") ?: return emptySet()
        @Suppress("UNCHECKED_CAST")
        return ((array.array as Array<String>?) ?: emptyArray()).toSortedSet()
    }

    private fun doubleList(array: java.sql.Array): List<Double> {
        val raw = array.array
        return when (raw) {
            is DoubleArray -> raw.toList()
            is Array<*> -> raw.map { (it as Number).toDouble() }
            else -> emptyList()
        }
    }

    private fun searchModes(array: java.sql.Array): Set<com.hotelopai.knowledge.application.KnowledgeSearchMode> {
        @Suppress("UNCHECKED_CAST")
        val raw = (array.array as Array<String>?) ?: emptyArray()
        return raw.map { com.hotelopai.knowledge.application.KnowledgeSearchMode.valueOf(it) }.toSortedSet(compareBy { it.name })
    }

    private fun cosine(left: List<Double>, right: List<Double>): Double {
        if (left.size != right.size || left.isEmpty()) return -1.0
        val dot = left.indices.sumOf { left[it] * right[it] }
        val leftMagnitude = sqrt(left.sumOf { it * it })
        val rightMagnitude = sqrt(right.sumOf { it * it })
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) return 0.0
        return dot / (leftMagnitude * rightMagnitude)
    }
}
