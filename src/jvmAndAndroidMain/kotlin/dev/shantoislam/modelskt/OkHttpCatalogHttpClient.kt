package dev.shantoislam.modelskt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

public class OkHttpCatalogHttpClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val maximumAttempts: Int = 3,
) : CatalogHttpClient {
    override suspend fun execute(request: CatalogHttpRequest): CatalogHttpResponse {
        require(request.url.startsWith("https://") || request.url.startsWith("http://localhost")) {
            "Catalog URL must use HTTPS"
        }
        var attempt = 0
        var lastFailure: IOException? = null
        while (attempt < maximumAttempts) {
            attempt++
            try {
                val response = executeOnce(request)
                if (response.statusCode !in RETRYABLE_STATUS || attempt == maximumAttempts) return response
                delay(retryDelayMillis(response, attempt))
            } catch (error: IOException) {
                lastFailure = error
                if (attempt == maximumAttempts) throw error
                delay(backoffMillis(attempt))
            }
        }
        throw lastFailure ?: IOException("Catalog request failed")
    }

    private suspend fun executeOnce(request: CatalogHttpRequest): CatalogHttpResponse = withContext(Dispatchers.IO) {
        val call = client.newCall(
            Request.Builder().url(request.url).apply {
                request.headers.forEach { (name, value) -> header(name, value) }
            }.build()
        )
        call.execute().use { response ->
            val contentLength = response.body.contentLength()
            if (contentLength > request.maximumResponseBytes) {
                throw IOException("Catalog response exceeds ${request.maximumResponseBytes} bytes")
            }
            val bytes = response.body.source().readByteArray(request.maximumResponseBytes + 1L)
            if (bytes.size > request.maximumResponseBytes) {
                throw IOException("Catalog response exceeds ${request.maximumResponseBytes} bytes")
            }
            CatalogHttpResponse(
                statusCode = response.code,
                headers = response.headers.toMap(),
                body = bytes.takeIf { it.isNotEmpty() }?.decodeToString(),
            )
        }
    }

    private fun retryDelayMillis(response: CatalogHttpResponse, attempt: Int): Long {
        val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
        return retryAfterSeconds?.times(1_000L) ?: backoffMillis(attempt)
    }

    private fun backoffMillis(attempt: Int): Long =
        min(4_000L, 250L * (1L shl (attempt - 1))) + Random.nextLong(100L)

    private companion object {
        val RETRYABLE_STATUS: Set<Int> = setOf(408, 429) + (500..599)
    }
}
