package dev.shantoislam.modelskt.modelsdev

import dev.shantoislam.modelskt.CatalogHttpClient
import dev.shantoislam.modelskt.CatalogHttpRequest
import dev.shantoislam.modelskt.CatalogHttpResponse
import dev.shantoislam.modelskt.CatalogSnapshot
import dev.shantoislam.modelskt.ModelDescriptor
import dev.shantoislam.modelskt.ModelKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ModelsDevCatalogSourceTest {
    @Test
    fun fetchesCombinedCatalogByDefault() = runTest {
        val client = RecordingHttpClient()
        val source = ModelsDevCatalogSource(client)

        source.fetch()

        assertTrue(client.request?.url == ModelsDevConfig.DEFAULT_CATALOG_URL)
    }

    @Test
    fun legacyProviderOnlySnapshotRequiresUpgradeForDefaultSource() {
        val source = ModelsDevCatalogSource(RecordingHttpClient())
        val legacy = CatalogSnapshot(models = mapOf(ModelKey("google", "model") to descriptor("google", "model")))
        val combined = legacy.copy(
            canonicalModels = mapOf("google/model" to descriptor("google", "model")),
        )

        assertFalse(source.isSnapshotCurrent(legacy))
        assertTrue(source.isSnapshotCurrent(combined))
    }

    private class RecordingHttpClient : CatalogHttpClient {
        var request: CatalogHttpRequest? = null

        override suspend fun execute(request: CatalogHttpRequest): CatalogHttpResponse {
            this.request = request
            return CatalogHttpResponse(
                statusCode = 200,
                headers = emptyMap(),
                body = """{"models":{},"providers":{"test":{"id":"test","models":{}}}}""",
            )
        }
    }

    private fun descriptor(providerId: String, modelId: String) = ModelDescriptor(
        key = ModelKey(providerId, modelId),
        sourceId = "test",
    )
}
