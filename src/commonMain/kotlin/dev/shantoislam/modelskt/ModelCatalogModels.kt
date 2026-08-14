package dev.shantoislam.modelskt

import kotlinx.serialization.Serializable

public data class ModelKey(
    public val providerId: String,
    public val modelId: String,
)

@Serializable
public enum class Support {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

public enum class ModelLifecycle {
    ACTIVE,
    ALPHA,
    BETA,
    DEPRECATED,
    UNKNOWN,
}

public data class ModelLimits(
    public val contextTokens: Long? = null,
    public val inputTokens: Long? = null,
    public val outputTokens: Long? = null,
)

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

public data class ModelPricing(
    public val inputPerMillion: String? = null,
    public val outputPerMillion: String? = null,
    public val cacheReadPerMillion: String? = null,
    public val cacheWritePerMillion: String? = null,
)

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

public data class ProviderDescriptor(
    public val id: String,
    public val name: String? = null,
    public val api: String? = null,
    public val documentation: String? = null,
)

public data class CatalogSnapshot(
    public val providers: Map<String, ProviderDescriptor> = emptyMap(),
    public val models: Map<ModelKey, ModelDescriptor> = emptyMap(),
    public val canonicalModels: Map<String, ModelDescriptor> = emptyMap(),
    public val sourceVersions: Map<String, String> = emptyMap(),
    public val refreshedAtEpochMillis: Long? = null,
) {
    public fun find(key: ModelKey): ModelDescriptor? = models[key]

    public fun modelsForProvider(providerId: String): List<ModelDescriptor> =
        models.values.filter { it.key.providerId == providerId }

    public fun findCanonical(modelId: String): ModelDescriptor? {
        val normalized = modelId.trim()
        canonicalModels[normalized]?.let { return it }
        val candidates = canonicalModels.filterKeys { canonicalId ->
            if ('/' in normalized) {
                canonicalId.equals(normalized, ignoreCase = true)
            } else {
                canonicalId.substringAfterLast('/').equals(normalized, ignoreCase = true)
            }
        }.values
        return candidates.singleOrNull()
    }
}
