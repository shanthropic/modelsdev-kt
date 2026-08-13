package dev.shantoislam.modelskt

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultModelCatalogTest {
    @Test
    fun keepsCachedSnapshotWhenRefreshFails() = runTest {
        val payload = """{"test":{"id":"test","name":"Test","models":{}}}"""
        val cache = MemoryCache(CachedCatalog("test", payload, "etag", 0L))
        val catalog = DefaultModelCatalog(
            source = FailingSource,
            cache = cache,
            refreshPolicy = RefreshPolicy(freshForMillis = 1L),
            nowEpochMillis = { 2L },
        )

        catalog.initialize()
        val result = catalog.refresh()

        assertIs<CatalogRefreshResult.Failed>(result)
        assertTrue(catalog.providers().isNotEmpty())
        assertEquals("offline", (catalog.state.value as ModelCatalogState.Ready).lastError)
    }

    private object FailingSource : ModelCatalogSource {
        override val sourceId = "test"
        override fun decode(payload: String) = dev.shantoislam.modelskt.modelsdev.ModelsDevParser().parse(payload, sourceId)
        override suspend fun fetch(validator: CatalogValidator?): CatalogFetchResult = error("offline")
    }

    private class MemoryCache(initial: CachedCatalog?) : CatalogCache {
        private var value = initial
        override suspend fun read(sourceId: String) = value
        override suspend fun write(catalog: CachedCatalog) { value = catalog }
        override suspend fun invalidate(sourceId: String) { value = null }
    }
}
