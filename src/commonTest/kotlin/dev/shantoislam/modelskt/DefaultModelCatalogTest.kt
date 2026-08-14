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

    @Test
    fun refreshesFreshCacheWhenSourceRequiresANewerSnapshotShape() = runTest {
        val payload = """{"test":{"id":"test","name":"Test","models":{}}}"""
        val cache = MemoryCache(CachedCatalog("test", payload, "etag", 1L))
        val source = UpgradingSource()
        val catalog = DefaultModelCatalog(
            source = source,
            cache = cache,
            refreshPolicy = RefreshPolicy(freshForMillis = 10_000L),
            nowEpochMillis = { 2L },
        )

        catalog.initialize()
        val result = catalog.refresh()

        assertIs<CatalogRefreshResult.Updated>(result)
        assertEquals("Canonical", catalog.findCanonical("model")?.name)
        assertEquals(1, source.fetchCount)
        assertEquals(null, source.receivedValidator?.etag)
    }

    private object FailingSource : ModelCatalogSource {
        override val sourceId = "test"
        override fun decode(payload: String) = dev.shantoislam.modelskt.modelsdev.ModelsDevParser().parse(payload, sourceId)
        override suspend fun fetch(validator: CatalogValidator?): CatalogFetchResult = error("offline")
    }

    private class UpgradingSource : ModelCatalogSource {
        override val sourceId = "test"
        var fetchCount = 0
        var receivedValidator: CatalogValidator? = null

        override fun decode(payload: String) = dev.shantoislam.modelskt.modelsdev.ModelsDevParser().parse(payload, sourceId)

        override fun isSnapshotCurrent(snapshot: CatalogSnapshot): Boolean = snapshot.canonicalModels.isNotEmpty()

        override suspend fun fetch(validator: CatalogValidator?): CatalogFetchResult {
            fetchCount++
            receivedValidator = validator
            val payload = """
                {
                  "models": {"lab/model": {"id": "lab/model", "name": "Canonical"}},
                  "providers": {"test": {"id": "test", "models": {}}}
                }
            """.trimIndent()
            return CatalogFetchResult.Updated(decode(payload), payload, "new-etag")
        }
    }

    private class MemoryCache(initial: CachedCatalog?) : CatalogCache {
        private var value = initial
        override suspend fun read(sourceId: String) = value
        override suspend fun write(catalog: CachedCatalog) { value = catalog }
        override suspend fun invalidate(sourceId: String) { value = null }
    }
}
