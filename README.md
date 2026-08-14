# models-kt

A Kotlin Multiplatform client for the [models.dev](https://models.dev) API targeting Android (API 30+) and JVM 17+.

`models-kt` downloads and parses the models.dev catalog, normalizes it into canonical and provider-qualified model metadata, caches snapshots on disk, and refreshes them with HTTP conditional requests. It has no dependency on any LLM runtime, model provider, or UI framework.

- Package: `dev.shantoislam.modelskt`
- License: [MIT](LICENSE)
- Targets: Android (minSdk 30), JVM (17)

## Features

- **Combined catalog ingestion** — fetches `https://models.dev/catalog.json` (canonical models + providers in one response) and still decodes legacy `api.json` payloads.
- **Canonical and provider-qualified metadata** — canonical records keyed by `lab/model-id` for gateway-agnostic lookups, plus per-provider records for exact provider identity.
- **Ambiguity-safe canonical lookup** — bare model IDs resolve only when unique across the canonical catalog.
- **Conditional refresh** — ETag/`If-None-Match` revalidation with a configurable freshness window.
- **Portable disk cache** — atomic writes, versioned schema, and safe source-ID file names.
- **Resilient HTTP** — HTTPS enforcement, response size caps, and bounded retry with backoff for transient failures.
- **Reactive state** — an observable `StateFlow<ModelCatalogState>` for loading, staleness, and error signaling.

## Requirements

- Kotlin 2.x (Kotlin Multiplatform plugin)
- JVM 17 bytecode target
- Android `compileSdk 37`, `minSdk 30`
- [OkHttp](https://square.github.io/okhttp/) (provided by the library)

## Installation

### Included build (local module)

If you already have `models-kt` checked out, include it as a composite/local project:

```kotlin
// settings.gradle.kts
include(":models-kt")
```

```kotlin
// app/build.gradle.kts
implementation(project(":models-kt"))
```

### Maven

Published under `dev.shantoislam:models-kt:<version>` (currently `0.1.0-SNAPSHOT`). Use your configured Maven repository:

```kotlin
implementation("dev.shantoislam:models-kt:0.1.0-SNAPSHOT")
```

## Quick start

```kotlin
import dev.shantoislam.modelskt.DefaultModelCatalog
import dev.shantoislam.modelskt.FileCatalogCache
import dev.shantoislam.modelskt.ModelCatalog
import dev.shantoislam.modelskt.ModelCatalogState
import dev.shantoislam.modelskt.ModelKey
import dev.shantoislam.modelskt.OkHttpCatalogHttpClient
import dev.shantoislam.modelskt.modelsdev.ModelsDevCatalogSource
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

val catalog: ModelCatalog = DefaultModelCatalog(
    source = ModelsDevCatalogSource(OkHttpCatalogHttpClient()),
    cache = FileCatalogCache(cacheDirectory.absolutePath),
)

catalog.initialize() // Loads a valid cached snapshot synchronously, if present.
catalog.refresh()    // Revalidates over HTTP; retains stale data on failure.

// Provider-qualified lookup (exact provider identity).
val openAiModel = catalog.find(ModelKey(providerId = "openai", modelId = "gpt-4o"))

// Canonical lookup by full canonical ID or a unique bare ID.
val canonical = catalog.findCanonical("gpt-4o")        // bare ID
val qualified = catalog.findCanonical("openai/gpt-4o") // full canonical ID

// Observe catalog readiness and staleness.
catalog.state
    .onEach { state ->
        when (state) {
            ModelCatalogState.Uninitialized -> println("No snapshot yet")
            is ModelCatalogState.Ready ->
                println("Ready (stale=${state.stale}, refreshing=${state.refreshing}, error=${state.lastError})")
            is ModelCatalogState.EmptyFailure -> println("Failed: ${state.message}")
        }
    }
    .launchIn(scope)
```

## Core concepts

### Canonical vs provider-qualified metadata

models.dev models appear once in the canonical catalog under a `lab/model-id` key and once per provider that serves them. `models-kt` keeps both views:

- `CatalogSnapshot.models` — provider-qualified records, keyed by `ModelKey(providerId, modelId)`. Use these when the provider is known exactly.
- `CatalogSnapshot.canonicalModels` — canonical records, keyed by the full canonical ID (`lab/model-id`). Use these for private gateways and provider-agnostic lookups.

`findCanonical(modelId)` accepts either a full canonical ID or a bare ID. A bare ID resolves only when it matches exactly one canonical record; ambiguous bare IDs return `null` rather than guessing. Model IDs are opaque and are never rewritten.

### Catalog lifecycle and state

The catalog publishes `StateFlow<ModelCatalogState>`:

| State | Meaning |
|---|---|
| `Uninitialized` | No snapshot loaded yet. |
| `Ready(snapshot, stale, refreshing, lastError)` | A snapshot is available. `stale`/`refreshing` reflect refresh progress; `lastError` carries the most recent failure. |
| `EmptyFailure(message)` | No snapshot could be loaded or fetched. |

- `initialize()` loads a cached snapshot (if valid) without network I/O.
- `refresh(force)` revalidates over HTTP. Without `force`, it skips the request while the cached snapshot is younger than `RefreshPolicy.freshForMillis` and the source considers it current.
- On network failure, the previous snapshot is retained and surfaced as `Ready(stale = true, lastError = ...)`.

### Refresh, ETag, and cache upgrades

Fetches send `If-None-Match` with the cached ETag. A `304 Not Modified` refreshes the timestamp; otherwise the new payload is parsed, written to the cache, and published.

If a cached snapshot no longer matches the current catalog format (for example, a legacy provider-only `api.json` payload now that `catalog.json` is the default), the catalog skips the stale ETag and fetches a full update. The old snapshot remains usable offline.

### HTTP behavior

`OkHttpCatalogHttpClient`:
- requires HTTPS (except `http://localhost` for testing),
- enforces a maximum response size (default 8 MiB),
- retries `408`, `429`, and `5xx` responses and `IOException`s with exponential backoff (default 3 attempts).

### Cache

`FileCatalogCache` stores one JSON record per source ID with a schema version, the raw payload, its ETag, and a refresh timestamp. Writes go through a temp file plus atomic move so a crash never leaves a corrupt cache. Corrupt or unsupported records are invalidated and re-fetched.

## models.dev endpoints

| Endpoint | Purpose | Status |
|---|---|---|
| `https://models.dev/catalog.json` | Combined canonical models + providers (default) | Used by default |
| `https://models.dev/api.json` | Provider-only records | Decoded for legacy caches |
| `https://models.dev/models.json` | Canonical model metadata | Not fetched by default |

`ModelsDevConfig.apiUrl` can point `ModelsDevCatalogSource` at another endpoint; a non-default URL keeps the catalog compatible with legacy payloads.

## Non-goals

`models-kt` intentionally does **not** handle:
- API credentials, keys, or account-specific model availability,
- any UI (pick lists, settings screens, model selection),
- LLM runtime adapters (for example, Koog), or
- model ID rewriting or guessing.

## Documentation

- [API reference](docs/API.md) — every public type, function, and JSON field mapping.

## License

[MIT](LICENSE) © 2026 Shanto Islam
