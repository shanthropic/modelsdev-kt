package dev.shantoislam.modelskt

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(kotlin.time.ExperimentalTime::class)
public class DefaultModelCatalog(
    private val source: ModelCatalogSource,
    private val cache: CatalogCache? = null,
    private val refreshPolicy: RefreshPolicy = RefreshPolicy(),
    private val nowEpochMillis: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) : ModelCatalog {
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow<ModelCatalogState>(ModelCatalogState.Uninitialized)
    private var current = CatalogSnapshot()
    private var cached: CachedCatalog? = null

    override val state: StateFlow<ModelCatalogState> = mutableState.asStateFlow()

    override fun snapshot(): CatalogSnapshot = current
    override fun find(key: ModelKey): ModelDescriptor? = current.find(key)
    override fun findCanonical(modelId: String): ModelDescriptor? = current.findCanonical(modelId)
    override fun models(providerId: String): List<ModelDescriptor> = current.modelsForProvider(providerId)
    override fun canonicalModels(): List<ModelDescriptor> = current.canonicalModels.values.toList()
    override fun providers(): List<ProviderDescriptor> = current.providers.values.toList()

    override suspend fun initialize(): CatalogSnapshot = refreshMutex.withLock {
        if (current.providers.isNotEmpty()) return@withLock current
        val stored = cache?.read(source.sourceId)
        if (stored != null) {
            runCatching { source.decode(stored.payload) }
                .onSuccess { parsed ->
                    cached = stored
                    current = parsed.copy(refreshedAtEpochMillis = stored.refreshedAtEpochMillis)
                    mutableState.value = ModelCatalogState.Ready(current, stale = isStale(stored))
                }
                .onFailure { cache.invalidate(source.sourceId) }
        }
        current
    }

    override suspend fun refresh(force: Boolean): CatalogRefreshResult = refreshMutex.withLock {
        val existing = cached
        if (!force && existing != null && !isStale(existing) && source.isSnapshotCurrent(current)) {
            return@withLock CatalogRefreshResult.Unchanged(current)
        }
        if (current.providers.isNotEmpty()) {
            mutableState.value = ModelCatalogState.Ready(current, stale = existing?.let(::isStale) ?: true, refreshing = true)
        }
        try {
            val snapshotCurrent = source.isSnapshotCurrent(current)
            val validator = existing?.etag?.takeIf { snapshotCurrent }
            when (val result = source.fetch(CatalogValidator(validator))) {
                CatalogFetchResult.NotModified -> {
                    check(snapshotCurrent) { "Catalog source returned not-modified for an incompatible snapshot" }
                    val refreshed = existing?.copy(refreshedAtEpochMillis = nowEpochMillis())
                    if (refreshed != null) {
                        cache?.write(refreshed)
                        cached = refreshed
                        current = current.copy(refreshedAtEpochMillis = refreshed.refreshedAtEpochMillis)
                    }
                    mutableState.value = ModelCatalogState.Ready(current, stale = false)
                    CatalogRefreshResult.Unchanged(current)
                }
                is CatalogFetchResult.Updated -> {
                    val now = nowEpochMillis()
                    val stored = CachedCatalog(source.sourceId, result.payload, result.etag, now)
                    cache?.write(stored)
                    cached = stored
                    current = result.snapshot.copy(refreshedAtEpochMillis = now)
                    mutableState.value = ModelCatalogState.Ready(current, stale = false)
                    CatalogRefreshResult.Updated(current)
                }
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (current.providers.isEmpty()) {
                mutableState.value = ModelCatalogState.EmptyFailure(error.message ?: "Catalog refresh failed")
            } else {
                mutableState.value = ModelCatalogState.Ready(
                    current,
                    stale = true,
                    lastError = error.message ?: "Catalog refresh failed",
                )
            }
            CatalogRefreshResult.Failed(error, current.takeIf { it.providers.isNotEmpty() })
        }
    }

    private fun isStale(value: CachedCatalog): Boolean =
        nowEpochMillis() - value.refreshedAtEpochMillis >= refreshPolicy.freshForMillis
}
