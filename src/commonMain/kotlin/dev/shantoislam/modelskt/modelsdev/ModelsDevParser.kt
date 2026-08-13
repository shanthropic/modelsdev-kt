package dev.shantoislam.modelskt.modelsdev

import dev.shantoislam.modelskt.CatalogSnapshot
import dev.shantoislam.modelskt.ModelCapabilities
import dev.shantoislam.modelskt.ModelDescriptor
import dev.shantoislam.modelskt.ModelKey
import dev.shantoislam.modelskt.ModelLifecycle
import dev.shantoislam.modelskt.ModelLimits
import dev.shantoislam.modelskt.ModelPricing
import dev.shantoislam.modelskt.ProviderDescriptor
import dev.shantoislam.modelskt.Support
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

public class ModelsDevParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    public fun parse(payload: String, sourceId: String = ModelsDevCatalogSource.SOURCE_ID): CatalogSnapshot {
        val root = json.parseToJsonElement(payload) as? JsonObject
            ?: throw IllegalArgumentException("models.dev catalog root must be an object")
        val providers = linkedMapOf<String, ProviderDescriptor>()
        val models = linkedMapOf<ModelKey, ModelDescriptor>()

        root.forEach { (providerMapKey, providerElement) ->
            val provider = providerElement as? JsonObject ?: return@forEach
            val providerId = provider.string("id")?.takeIf(String::isNotBlank) ?: providerMapKey
            if (providerId.isBlank()) return@forEach
            providers[providerId] = ProviderDescriptor(
                id = providerId,
                name = provider.string("name"),
                api = provider.string("api"),
                documentation = provider.string("doc"),
            )
            val providerModels = provider["models"] as? JsonObject ?: return@forEach
            providerModels.forEach modelLoop@{ (modelMapKey, modelElement) ->
                val model = modelElement as? JsonObject ?: return@modelLoop
                val modelId = model.string("id")?.takeIf(String::isNotBlank) ?: modelMapKey
                if (modelId.isBlank()) return@modelLoop
                val limit = model.obj("limit")
                val modalities = model.obj("modalities")
                val key = ModelKey(providerId, modelId)
                models[key] = ModelDescriptor(
                    key = key,
                    name = model.string("name"),
                    family = model.string("family"),
                    description = model.string("description"),
                    limits = ModelLimits(
                        contextTokens = limit?.long("context")?.takeIf { it >= 0 },
                        inputTokens = limit?.long("input")?.takeIf { it >= 0 },
                        outputTokens = limit?.long("output")?.takeIf { it >= 0 },
                    ),
                    capabilities = ModelCapabilities(
                        toolCalling = model.support("tool_call"),
                        structuredOutput = model.support("structured_output"),
                        temperature = model.support("temperature"),
                        reasoning = model.support("reasoning"),
                        attachments = model.support("attachment"),
                        inputModalities = modalities.stringSet("input"),
                        outputModalities = modalities.stringSet("output"),
                        interleavedReasoning = model.interleaved(),
                    ),
                    pricing = model.obj("cost")?.toPricing(),
                    lifecycle = when (model.string("status")) {
                        "alpha" -> ModelLifecycle.ALPHA
                        "beta" -> ModelLifecycle.BETA
                        "deprecated" -> ModelLifecycle.DEPRECATED
                        null -> ModelLifecycle.ACTIVE
                        else -> ModelLifecycle.UNKNOWN
                    },
                    releaseDate = model.string("release_date"),
                    lastUpdated = model.string("last_updated"),
                    knowledgeCutoff = model.string("knowledge"),
                    openWeights = model.boolean("open_weights"),
                    sourceId = sourceId,
                )
            }
        }
        if (providers.isEmpty()) throw IllegalArgumentException("models.dev catalog contains no valid providers")
        return CatalogSnapshot(providers = providers, models = models)
    }
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull
private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject?.stringSet(name: String): Set<String> =
    (this?.get(name) as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.toSet()
        .orEmpty()

private fun JsonObject.support(name: String): Support = when (boolean(name)) {
    true -> Support.SUPPORTED
    false -> Support.UNSUPPORTED
    null -> Support.UNKNOWN
}

private fun JsonObject.interleaved(): String? = when (val value = this["interleaved"]) {
    is JsonPrimitive -> if (value.booleanOrNull == true) "supported" else null
    is JsonObject -> value.string("field")
    else -> null
}

private fun JsonObject.toPricing(): ModelPricing? {
    fun numberText(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    return ModelPricing(
        inputPerMillion = numberText("input"),
        outputPerMillion = numberText("output"),
        cacheReadPerMillion = numberText("cache_read"),
        cacheWritePerMillion = numberText("cache_write"),
    ).takeUnless {
        it.inputPerMillion == null && it.outputPerMillion == null &&
            it.cacheReadPerMillion == null && it.cacheWritePerMillion == null
    }
}
