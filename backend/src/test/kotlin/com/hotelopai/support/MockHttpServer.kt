package com.hotelopai.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

data class RecordedHttpRequest(
    val method: String,
    val path: String,
    val query: String?,
    val body: String,
    val headers: Map<String, List<String>>
)

data class MockHttpResponse(
    val status: Int,
    val body: String = "",
    val contentType: String = "application/json",
    val delayMs: Long = 0,
    val headers: Map<String, String> = emptyMap()
)

class MockHttpServer {
    private val server = HttpServer.create(InetSocketAddress(0), 0)
    private val stubs = ConcurrentHashMap<String, MockHttpResponse>()
    private val stubSequences = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockHttpResponse>>()
    private val recordedRequests = CopyOnWriteArrayList<RecordedHttpRequest>()

    init {
        server.createContext("/") { exchange -> handle(exchange) }
        server.executor = Executors.newCachedThreadPool()
    }

    val baseUrl: String
        get() = "http://localhost:${server.address.port}"

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop(0)
    }

    fun stub(method: String, path: String, response: MockHttpResponse) {
        stubs[key(method, path)] = response
    }

    fun stubSequence(method: String, path: String, responses: List<MockHttpResponse>) {
        stubSequences[key(method, path)] = ConcurrentLinkedQueue(responses)
    }

    fun reset() {
        stubs.clear()
        stubSequences.clear()
        recordedRequests.clear()
    }

    fun requests(method: String, path: String): List<RecordedHttpRequest> =
        recordedRequests.filter { it.method == method.uppercase() && it.path == path }

    fun lastRequest(method: String, path: String): RecordedHttpRequest? =
        requests(method, path).lastOrNull()

    private fun handle(exchange: HttpExchange) {
        val requestBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val method = exchange.requestMethod.uppercase()
        val path = exchange.requestURI.path
        recordedRequests += RecordedHttpRequest(
            method = method,
            path = path,
            query = exchange.requestURI.rawQuery,
            body = requestBody,
            headers = exchange.requestHeaders.mapKeys { it.key.lowercase() }
                .mapValues { it.value.toList() }
        )

        val response = stubSequences[key(method, path)]?.poll()
            ?: stubs[key(method, path)]
            ?: MockHttpResponse(
            status = 404,
            body = """{"message":"Not found"}"""
        )

        if (response.delayMs > 0) {
            Thread.sleep(response.delayMs)
        }

        val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", response.contentType)
        response.headers.forEach { (key, value) ->
            exchange.responseHeaders.add(key, value)
        }
        exchange.sendResponseHeaders(response.status, bytes.size.toLong())
        exchange.responseBody.use { output ->
            output.write(bytes)
        }
    }

    private fun key(method: String, path: String): String =
        "${method.uppercase()} $path"
}
