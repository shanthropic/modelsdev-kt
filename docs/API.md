# models-kt API reference

This document describes the complete public API of `models-kt`. The library uses `explicitApi()`, so every type and member listed here is public and stable within its source version.

- Module: `models-kt`
- Coordinates: `dev.shantoislam:models-kt:0.1.0-SNAPSHOT`
- Targets: Android (minSdk 30), JVM (17)

Packages:

- [`dev.shantoislam.modelskt`](#devshantoislammodelskt) — core catalog model, API, HTTP, and cache.
- [`dev.shantoislam.modelskt.modelsdev`](#devshantoislammodelsktmodelsdev) — models.dev source, parser, and configuration.

---

## `dev.shantoislam.modelskt`

### Data model

#### `ModelKey`

```kotlin
public data class ModelKey(
    public val providerId: String,
    public val modelId: String,
)
```

Uniquely identifies a provider-qualified model. `providerId` is the models.dev provider ID (for example `openai`); `modelId` is the provider model ID (for example `gpt-4o`).

#### `Support`

```kotlin
@Serializable
public enum class Support { SUPPORTED, UNSUPPORTED, UNKNOWN }
```

Tri-state capability value. `UNKNOWN` means the catalog does not state a value.

#### `ModelLifecycle`

```kotlin
public enum class ModelLifecycle { ACTIVE, ALPHA, BETA, DEPRECATED, UNKNOWN }
```

Lifecycle parsed from the `status` field. Absent `status` maps to `ACTIVE`.

#### `ModelLimits`

```kotlin
public data class ModelLimits(
    public val contextTokens: Long? = null,
    public val inputTokens: Long? = null,
    public val outputTokens: Long? = null,
)
```

Token limits parsed from `limit.context`, `limit.input`, and `limit.output`. Negative or missing values are `null`.

#### `ModelCapabilities`

```kotlin
public data class ModelCapabilities(
    public val toolCalling: Support = Support.UNKNOWN,
    public val structuredOutput: Support = Support.UNKNOWN,
    public val temperature: Support = Support.UNKNOWN,
    public val reasoning: Support = Support.UNKNOWN,
    public val attachments: Support = Support.UNKNOWN,
    public val inputModalities: Set<String> = emptySet(),
    public val outputModalities: Set<String> = emptySet(),
    public val interleavedReasoning: String? = null,
)
```

Parsed from `tool_call`, `structured_output`, `temperature`, `reasoning`, `attachment`, `modalities.input`, `modalities.output`, and `interleaved`.

#### `ModelPricing`

```kotlin
public data class ModelPricing(
    public val inputPerMillion: String? = null,
    public val outputPerMillion: String? = null,
    public val cacheReadPerMillion: String? = null,
    public val cacheWritePerMillion: String? = null,
)
```

Per-million-token prices parsed from `cost.input`, `cost.output`, `cost.cache_read`, and `cost.cache_write`. Values are kept as strings because the catalog emits them as raw JSON text. `ModelPricing` is `null` on a model when none of the four fields are present.

#### `ModelDescriptor`

```kotlin
public data class ModelDescriptor(
    public val key: ModelKey,
    public val name: String? = null,
    public val family: String? = null,
    public val description: String? = null,
    public val limits: ModelLimits = ModelLimits(),
    public val capabilities: ModelCapabilities = ModelCapabilities(),
    public val pricing: ModelPricing? = null,
    public val lifecycle: ModelLifecycle = ModelLifecycle.UNKNOWN,
    public val releaseDate: String? = null,
    public val lastUpdated: String? = null,
    public val knowledgeCutoff: String? = null,
    public val openWeights: Boolean? = null,
    public val sourceId: String,
)
```

One normalized model record. For canonical records, `key.providerId` is the canonical lab and `key.modelId` the canonical ID (both halves of `lab/model-id`). For provider records, `key` matches the provider exactly.

#### `ProviderDescriptor`

```kotlin
public data class ProviderDescriptor(
    public val id: String,
    public val name: String? = null,
    public val api: String? = null,
    public val documentation: String? = null,
)
```

Parsed from provider `id`, `name`, `api`, and `doc`.

#### `CatalogSnapshot`

```kotlin
public data class CatalogSnapshot(
    public val providers: Map<String, ProviderDescriptor> = emptyMap(),
    public val models: Map<ModelKey, ModelDescriptor> = emptyMap(),
    public val canonicalModels: Map<String, ModelDescriptor> = emptyMap(),
    public val sourceVersions: Map<String, String> = emptyMap(),
    public val refreshedAtEpochMillis: Long? = null,
) {
    public fun find(key: ModelKey): ModelDescriptor?
    public fun modelsForProvider(providerId: String): List<ModelDescriptor>
    public fun findCanonical(modelId: String): ModelDescriptor?
}
```

Immutable view of one catalog download.

- `find(key)` — exact provider-qualified lookup.
- `modelsForProvider(providerId)` — all provider-qualified records for a provider.
- `findCanonical(modelId)` — canonical lookup that accepts a full canonical ID or a unique bare ID:
  - A full canonical ID (`lab/model-id`) matches case-insensitively.
  - A bare ID matches when exactly one canonical record ends with it (`substringAfterLast('/')`); ambiguous bare IDs return `null`.

### Catalog API

#### `ModelCatalog`

```kotlin
public interface ModelCatalog {
    public val state: StateFlow<ModelCatalogState>
    public fun snapshot(): CatalogSnapshot
    public fun find(key: ModelKey): ModelDescriptor?
    public fun findCanonical(modelId: String): ModelDescriptor?
    public fun models(providerId: String): List<ModelDescriptor>
    public fun canonicalModels(): List<ModelDescriptor>
    public fun providers(): List<ProviderDescriptor>
    public suspend fun initialize(): CatalogSnapshot
    public suspend fun refresh(force: Boolean = false): CatalogRefreshResult
}
```

The observable catalog facade.

- `state` — reactive snapshot state; see [`ModelCatalogState`](#modelcatalogstate).
- `snapshot()` — current immutable snapshot (may be empty before `initialize()`).
- `find` / `findCanonical` — read helpers over the current snapshot.
- `models(providerId)` / `canonicalModels()` / `providers()` — collection helpers.
- `initialize()` — load a valid cached snapshot without network I/O; returns the current snapshot.
- `refresh(force)` — revalidate over HTTP. Honors freshness and source currency unless `force = true`.

#### `DefaultModelCatalog`

```kotlin
public class DefaultModelCatalog(
    private val source: ModelCatalogSource,
    private val cache: CatalogCache? = null,
    private val refreshPolicy: RefreshPolicy = RefreshPolicy(),
    private val nowEpochMillis: () -> Long = { ... },
) : ModelCatalog
```

Reference `ModelCatalog` implementation.

- All public operations are serialized by an internal `Mutex`.
- `initialize()` decodes the cached payload if present; a decode failure invalidates the cache.
- `refresh()`:
  - Skips the network call when `!force`, the cached snapshot is fresh, and `source.isSnapshotCurrent(snapshot)`.
  - Sends the cached ETag only when the snapshot shape is current.
  - On `304 Not Modified`, updates the refresh timestamp in place.
  - On failure, keeps the previous snapshot and reports it as stale (or `EmptyFailure` when none exists).

#### `ModelCatalogState`

```kotlin
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
```

| State | Meaning |
|---|---|
| `Uninitialized` | No snapshot loaded. |
| `Ready` | Snapshot available; `stale` and `lastError` describe the last refresh. |
| `EmptyFailure` | No snapshot could be loaded or fetched. |

#### `CatalogRefreshResult`

```kotlin
public sealed interface CatalogRefreshResult {
    public data class Updated(public val snapshot: CatalogSnapshot) : CatalogRefreshResult
    public data class Unchanged(public val snapshot: CatalogSnapshot) : CatalogRefreshResult
    public data class Failed(
        public val error: Throwable,
        public val retainedSnapshot: CatalogSnapshot?,
    ) : CatalogRefreshResult
}
```

Returned by `ModelCatalog.refresh()`. `retainedSnapshot` is the previous snapshot when the catalog still has one.

#### `CatalogValidator`

```kotlin
public data class CatalogValidator(
    public val etag: String? = null,
)
```

Revalidation metadata passed to `ModelCatalogSource.fetch()`. The catalog omits the ETag when the cached snapshot is no longer current.

### Source SPI

#### `ModelCatalogSource`

```kotlin
public interface ModelCatalogSource {
    public val sourceId: String
    public fun decode(payload: String): CatalogSnapshot
    public fun isSnapshotCurrent(snapshot: CatalogSnapshot): Boolean = true
    public suspend fun fetch(validator: CatalogValidator? = null): CatalogFetchResult
}
```

Pluggable catalog backend.

- `sourceId` — cache namespace and cache file name.
- `decode(payload)` — parse a raw payload into a snapshot.
- `isSnapshotCurrent(snapshot)` — whether the snapshot uses the current schema/shape; when `false`, the catalog forces a refresh and drops the ETag.
- `fetch(validator)` — perform the network request, returning [`CatalogFetchResult`](#catalogfetchresult).

#### `CatalogFetchResult`

```kotlin
public sealed interface CatalogFetchResult {
    public data class Updated(
        public val snapshot: CatalogSnapshot,
        public val payload: String,
        public val etag: String?,
    ) : CatalogFetchResult
    public data object NotModified : CatalogFetchResult
}
```

A successful `fetch()` returns `Updated` with the parsed snapshot, raw payload, and ETag for caching, or `NotModified` when the server validated the cached copy.

### HTTP

#### `CatalogHttpClient`

```kotlin
public interface CatalogHttpClient {
    public suspend fun execute(request: CatalogHttpRequest): CatalogHttpResponse
}
```

Transport abstraction for catalog fetches.

#### `CatalogHttpRequest`

```kotlin
public data class CatalogHttpRequest(
    public val url: String,
    public val headers: Map<String, String> = emptyMap(),
    public val maximumResponseBytes: Long = 8L * 1_024L * 1_024L,
)
```

#### `CatalogHttpResponse`

```kotlin
public data class CatalogHttpResponse(
    public val statusCode: Int,
    public val headers: Map<String, String>,
    public val body: String?,
) {
    public fun header(name: String): String?
}
```

`header(name)` does a case-insensitive lookup.

#### `OkHttpCatalogHttpClient`

```kotlin
public class OkHttpCatalogHttpClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val maximumAttempts: Int = 3,
) : CatalogHttpClient
```

Default OkHttp-backed client.

- Requires `https://` URLs (allows `http://localhost` for testing).
- Enforces `maximumResponseBytes` on both `Content-Length` and streamed bytes.
- Retries `408`, `429`, and `5xx` responses (honoring `Retry-After`) and `IOException`s with exponential backoff up to `maximumAttempts`.

### Cache

#### `CatalogCache`

```kotlin
public interface CatalogCache {
    public suspend fun read(sourceId: String): CachedCatalog?
    public suspend fun write(catalog: CachedCatalog)
    public suspend fun invalidate(sourceId: String)
}
```

#### `CachedCatalog`

```kotlin
public data class CachedCatalog(
    public val sourceId: String,
    public val payload: String,
    public val etag: String?,
    public val refreshedAtEpochMillis: Long,
)
```

#### `FileCatalogCache`

```kotlin
public class FileCatalogCache(
    cacheDirectory: String,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : CatalogCache
```

Disk cache backed by Okio.

- Stores one JSON record per source under `<cacheDirectory>/<sanitized-source-id>.cache.json`.
- Uses temp-file + atomic-move writes; corrupt or schema-mismatched records are deleted on read.
- Source IDs are sanitized to `[A-Za-z0-9_-]` for safe file names.

#### `RefreshPolicy`

```kotlin
public data class RefreshPolicy(
    public val freshForMillis: Long = 24L * 60L * 60L * 1_000L,
)
```

A cached snapshot younger than `freshForMillis` is considered fresh.

---

## `dev.shantoislam.modelskt.modelsdev`

### `ModelsDevConfig`

```kotlin
public data class ModelsDevConfig(
    public val apiUrl: String = DEFAULT_CATALOG_URL,
    public val maximumResponseBytes: Long = 8L * 1_024L * 1_024L,
) {
    public companion object {
        public const val DEFAULT_CATALOG_URL: String = "https://models.dev/catalog.json"
    }
}
```

Configuration for `ModelsDevCatalogSource`. A non-default `apiUrl` (for example the legacy `https://models.dev/api.json`) keeps the source compatible with provider-only payloads.

### `ModelsDevCatalogSource`

```kotlin
public class ModelsDevCatalogSource(
    private val httpClient: CatalogHttpClient,
    private val config: ModelsDevConfig = ModelsDevConfig(),
    private val parser: ModelsDevParser = ModelsDevParser(),
) : ModelCatalogSource {
    public companion object {
        public const val SOURCE_ID: String = "models.dev"
    }
}
```

The models.dev-backed source.

- `fetch()` requests `config.apiUrl` with `Accept: application/json` and `If-None-Match` when an ETag is provided; `304` maps to `CatalogFetchResult.NotModified`, non-2xx to an `IllegalStateException`.
- `isSnapshotCurrent()` is `true` unless the default `catalog.json` URL is used with a snapshot that has no canonical models — in that case the catalog performs an upgrade refresh.

### `ModelsDevParser`

```kotlin
public class ModelsDevParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    public fun parse(payload: String, sourceId: String = ModelsDevCatalogSource.SOURCE_ID): CatalogSnapshot
}
```

Parses models.dev JSON into a `CatalogSnapshot`.

- For `catalog.json` (root contains `providers` and `models`):
  - `providers` become `ProviderDescriptor`s; each provider's `models` become provider-qualified records.
  - `models` become canonical records keyed by full canonical ID.
- For legacy `api.json` (root is the provider map), provider records are parsed the same way and no canonical records exist.
- Throws `IllegalArgumentException` when the root is not an object or no valid providers are found.
- Unknown fields are ignored.

### JSON field mapping

Provider record:

| JSON | Kotlin |
|---|---|
| `id` / map key | `ProviderDescriptor.id` |
| `name` | `ProviderDescriptor.name` |
| `api` | `ProviderDescriptor.api` |
| `doc` | `ProviderDescriptor.documentation` |

Model record (provider or canonical):

| JSON | Kotlin |
|---|---|
| `id` / map key | `ModelKey.modelId` |
| `name` | `ModelDescriptor.name` |
| `family` | `ModelDescriptor.family` |
| `description` | `ModelDescriptor.description` |
| `limit.context` | `ModelLimits.contextTokens` |
| `limit.input` | `ModelLimits.inputTokens` |
| `limit.output` | `ModelLimits.outputTokens` |
| `tool_call` | `ModelCapabilities.toolCalling` |
| `structured_output` | `ModelCapabilities.structuredOutput` |
| `temperature` | `ModelCapabilities.temperature` |
| `reasoning` | `ModelCapabilities.reasoning` |
| `attachment` | `ModelCapabilities.attachments` |
| `modalities.input` | `ModelCapabilities.inputModalities` |
| `modalities.output` | `ModelCapabilities.outputModalities` |
| `interleaved` | `ModelCapabilities.interleavedReasoning` |
| `cost.input` | `ModelPricing.inputPerMillion` |
| `cost.output` | `ModelPricing.outputPerMillion` |
| `cost.cache_read` | `ModelPricing.cacheReadPerMillion` |
| `cost.cache_write` | `ModelPricing.cacheWritePerMillion` |
| `status` | `ModelDescriptor.lifecycle` |
| `release_date` | `ModelDescriptor.releaseDate` |
| `last_updated` | `ModelDescriptor.lastUpdated` |
| `knowledge` | `ModelDescriptor.knowledgeCutoff` |
| `open_weights` | `ModelDescriptor.openWeights` |

Boolean capability fields map to `Support`: `true` → `SUPPORTED`, `false` → `UNSUPPORTED`, absent → `UNKNOWN`. Negative token limits are discarded to `null`.
