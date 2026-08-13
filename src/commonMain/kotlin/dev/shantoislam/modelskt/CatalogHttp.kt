package dev.shantoislam.modelskt

public data class CatalogHttpRequest(
    public val url: String,
    public val headers: Map<String, String> = emptyMap(),
    public val maximumResponseBytes: Long = 8L * 1_024L * 1_024L,
)

public data class CatalogHttpResponse(
    public val statusCode: Int,
    public val headers: Map<String, String>,
    public val body: String?,
) {
    public fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
}

public interface CatalogHttpClient {
    public suspend fun execute(request: CatalogHttpRequest): CatalogHttpResponse
}
