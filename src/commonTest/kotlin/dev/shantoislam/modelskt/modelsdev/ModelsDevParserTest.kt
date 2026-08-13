package dev.shantoislam.modelskt.modelsdev

import dev.shantoislam.modelskt.ModelKey
import dev.shantoislam.modelskt.ModelLifecycle
import dev.shantoislam.modelskt.Support
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelsDevParserTest {
    @Test
    fun parsesProviderQualifiedModelsAndUnknownFields() {
        val snapshot = ModelsDevParser().parse(CATALOG)
        val model = snapshot.find(ModelKey("openai", "gpt-test"))!!

        assertEquals("GPT Test", model.name)
        assertEquals(128_000L, model.limits.contextTokens)
        assertEquals(16_384L, model.limits.outputTokens)
        assertEquals(Support.SUPPORTED, model.capabilities.toolCalling)
        assertEquals(Support.UNKNOWN, model.capabilities.temperature)
        assertEquals(setOf("text", "image"), model.capabilities.inputModalities)
        assertEquals("reasoning_details", model.capabilities.interleavedReasoning)
        assertEquals("0.15", model.pricing?.inputPerMillion)
        assertEquals(ModelLifecycle.BETA, model.lifecycle)
    }

    @Test
    fun skipsMalformedSiblingRecords() {
        val snapshot = ModelsDevParser().parse(CATALOG)

        assertEquals(1, snapshot.models.size)
        assertNull(snapshot.find(ModelKey("openai", "bad")))
    }

    private companion object {
        const val CATALOG = """
            {
              "openai": {
                "id": "openai",
                "name": "OpenAI",
                "doc": "https://example.test",
                "future_field": true,
                "models": {
                  "gpt-test": {
                    "id": "gpt-test",
                    "name": "GPT Test",
                    "description": "Fixture",
                    "attachment": true,
                    "reasoning": true,
                    "tool_call": true,
                    "interleaved": {"field": "reasoning_details"},
                    "modalities": {"input": ["text", "image"], "output": ["text"]},
                    "limit": {"context": 128000, "output": 16384},
                    "cost": {"input": 0.15, "output": 0.60},
                    "status": "beta",
                    "open_weights": false
                  },
                  "bad": "not-an-object"
                }
              }
            }
        """
    }
}
