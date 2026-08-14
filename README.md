# models-kt

`models-kt` is a Kotlin Multiplatform model-catalog library for Android and JVM. It provides normalized canonical and provider-qualified model metadata, models.dev ingestion, conditional refresh, and portable caching without depending on an LLM runtime.

Package: `dev.shantoislam.modelskt`

The library intentionally does not handle credentials, account-specific model availability, UI, or runtime adapters such as Koog.

## Usage

```kotlin
val catalog: ModelCatalog = DefaultModelCatalog(
    source = ModelsDevCatalogSource(OkHttpCatalogHttpClient()),
    cache = FileCatalogCache(cacheDirectory.absolutePath),
)

catalog.initialize() // Loads a valid cached snapshot, if present.
catalog.refresh()    // Uses ETag revalidation and retains stale data on failure.

val model = catalog.find(ModelKey(providerId = "openai", modelId = "gpt-4o"))
val canonical = catalog.findCanonical("gpt-4o")
```

Provider catalog identities are always provider-qualified. Canonical lookup accepts a full canonical ID or a unique bare ID, returning `null` when a bare ID is ambiguous. Model IDs are opaque and are never rewritten.
