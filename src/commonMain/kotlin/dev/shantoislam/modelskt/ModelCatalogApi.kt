package dev.shantoislam.modelskt

import kotlinx.coroutines.flow.StateFlow

public data class CatalogValidator(
    public val etag: String? = null,
)

public sealed interface CatalogFetchResult {
    public data class Updated(
        public val snapshot: CatalogSnapshot,
        public val payload: String,
        public val etag: String?,
    ) : CatalogFetchResult

    public data object NotModified : CatalogFetchResult
}

public interface ModelCatalogSource {
    public val sourceId: String
    public fun decode(payload: String): CatalogSnapshot
    public suspend fun fetch(validator: CatalogValidator? = null): CatalogFetchResult
}

public sealed interface ModelCatalogState {
    public data object Uninitialized : ModelCatalogState
    public data class Ready(
        public val snapshot: CatalogSnapshot,
        public val stale: Boolean,
        public val refreshing: Boolean = false,
        public val lastError: String? = null,
    ) : ModelCatalogState

    public data class EmptyFailure(public val message: String) : ModelCatalogState
}

public sealed interface CatalogRefreshResult {
    public data class Updated(public val snapshot: CatalogSnapshot) : CatalogRefreshResult
    public data class Unchanged(public val snapshot: CatalogSnapshot) : CatalogRefreshResult
    public data class Failed(public val error: Throwable, public val retainedSnapshot: CatalogSnapshot?) : CatalogRefreshResult
}

public interface ModelCatalog {
    public val state: StateFlow<ModelCatalogState>
    public fun snapshot(): CatalogSnapshot
    public fun find(key: ModelKey): ModelDescriptor?
    public fun models(providerId: String): List<ModelDescriptor>
    public fun providers(): List<ProviderDescriptor>
    public suspend fun initialize(): CatalogSnapshot
    public suspend fun refresh(force: Boolean = false): CatalogRefreshResult
}

public data class CachedCatalog(
    public val sourceId: String,
    public val payload: String,
    public val etag: String?,
    public val refreshedAtEpochMillis: Long,
)

public interface CatalogCache {
    public suspend fun read(sourceId: String): CachedCatalog?
    public suspend fun write(catalog: CachedCatalog)
    public suspend fun invalidate(sourceId: String)
}

public data class RefreshPolicy(
    public val freshForMillis: Long = 24L * 60L * 60L * 1_000L,
)
