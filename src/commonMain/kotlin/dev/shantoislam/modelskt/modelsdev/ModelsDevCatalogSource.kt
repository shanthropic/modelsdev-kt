package dev.shantoislam.modelskt.modelsdev

import dev.shantoislam.modelskt.CatalogFetchResult
import dev.shantoislam.modelskt.CatalogHttpClient
import dev.shantoislam.modelskt.CatalogHttpRequest
import dev.shantoislam.modelskt.CatalogValidator
import dev.shantoislam.modelskt.ModelCatalogSource

public data class ModelsDevConfig(
    public val apiUrl: String = DEFAULT_CATALOG_URL,
    public val maximumResponseBytes: Long = 8L * 1_024L * 1_024L,
) {
    public companion object {
        public const val DEFAULT_CATALOG_URL: String = "https://models.dev/catalog.json"
    }
}

public class ModelsDevCatalogSource(
    private val httpClient: CatalogHttpClient,
    private val config: ModelsDevConfig = ModelsDevConfig(),
    private val parser: ModelsDevParser = ModelsDevParser(),
) : ModelCatalogSource {
    override val sourceId: String = SOURCE_ID

    override fun decode(payload: String): dev.shantoislam.modelskt.CatalogSnapshot =
        parser.parse(payload, sourceId)

    override fun isSnapshotCurrent(snapshot: dev.shantoislam.modelskt.CatalogSnapshot): Boolean =
        config.apiUrl != ModelsDevConfig.DEFAULT_CATALOG_URL || snapshot.canonicalModels.isNotEmpty()

    override suspend fun fetch(validator: CatalogValidator?): CatalogFetchResult {
        val response = httpClient.execute(
            CatalogHttpRequest(
                url = config.apiUrl,
                headers = buildMap {
                    put("Accept", "application/json")
                    validator?.etag?.let { put("If-None-Match", it) }
                },
                maximumResponseBytes = config.maximumResponseBytes,
            )
        )
        if (response.statusCode == 304) return CatalogFetchResult.NotModified
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("models.dev request failed with HTTP ${response.statusCode}")
        }
        val payload = response.body ?: throw IllegalStateException("models.dev returned an empty response")
        return CatalogFetchResult.Updated(
            snapshot = decode(payload),
            payload = payload,
            etag = response.header("ETag"),
        )
    }

    public companion object {
        public const val SOURCE_ID: String = "models.dev"
    }
}
