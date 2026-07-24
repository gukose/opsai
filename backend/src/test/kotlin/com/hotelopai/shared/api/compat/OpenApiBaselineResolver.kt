package com.hotelopai.shared.api.compat

import java.nio.file.Files
import java.nio.file.Path

data class BaselineContract(
    val source: String,
    val content: String?,
    val bootstrap: Boolean
)

class OpenApiBaselineResolver(
    private val repoRoot: Path = Path.of("..").normalize()
) {
    fun resolve(): BaselineContract {
        val explicitRef = System.getProperty("hotelopai.openapi.baseline.ref")
            ?: System.getenv("OPENAPI_BASELINE_REF")
        if (!explicitRef.isNullOrBlank()) {
            gitShow(explicitRef)?.let { return BaselineContract(explicitRef, it, bootstrap = false) }
            return BaselineContract(explicitRef, null, bootstrap = true)
        }

        val githubBase = System.getenv("GITHUB_BASE_REF")?.takeIf(String::isNotBlank)
        if (githubBase != null) {
            val ref = "origin/$githubBase"
            gitShow(ref)?.let { return BaselineContract(ref, it, bootstrap = false) }
            return BaselineContract(ref, null, bootstrap = true)
        }

        val candidates = listOf("origin/main", "main", "origin/master", "master")
        candidates.forEach { ref ->
            gitShow(ref)?.let { return BaselineContract(ref, it, bootstrap = false) }
        }

        val current = repoRoot.resolve("docs/api/openapi-v1.yaml")
        return BaselineContract("working-tree-bootstrap", Files.readString(current), bootstrap = true)
    }

    private fun gitShow(ref: String): String? {
        val process = ProcessBuilder("git", "show", "$ref:docs/api/openapi-v1.yaml")
            .directory(repoRoot.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val content = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        return content.takeIf { exit == 0 && it.isNotBlank() }
    }
}
