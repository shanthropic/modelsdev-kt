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

    @Test
    fun parsesCombinedCanonicalAndProviderCatalog() {
        val snapshot = ModelsDevParser().parse(COMBINED_CATALOG)

        assertEquals(1_048_576L, snapshot.findCanonical("gemini-3.6-flash")?.limits?.contextTokens)
        assertEquals(65_536L, snapshot.findCanonical("google/gemini-3.6-flash")?.limits?.outputTokens)
        assertEquals(Support.SUPPORTED, snapshot.findCanonical("gemini-3.6-flash")?.capabilities?.temperature)
        assertEquals(1_000_000L, snapshot.findCanonical("claude-opus-5")?.limits?.contextTokens)
        assertEquals(Support.UNSUPPORTED, snapshot.findCanonical("claude-opus-5")?.capabilities?.temperature)
        assertEquals(1_000_000L, snapshot.find(ModelKey("gateway", "gemini-3.6-flash"))?.limits?.contextTokens)
    }

    @Test
    fun canonicalBareIdLookupRejectsAmbiguity() {
        val snapshot = ModelsDevParser().parse(AMBIGUOUS_CANONICAL_CATALOG)

        assertNull(snapshot.findCanonical("shared"))
        assertEquals("First", snapshot.findCanonical("lab-a/shared")?.name)
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

        const val COMBINED_CATALOG = """
            {
              "models": {
                "google/gemini-3.6-flash": {
                  "id": "google/gemini-3.6-flash",
                  "name": "Gemini 3.6 Flash",
                  "tool_call": true,
                  "temperature": true,
                  "limit": {"context": 1048576, "output": 65536}
                },
                "anthropic/claude-opus-5": {
                  "id": "anthropic/claude-opus-5",
                  "name": "Claude Opus 5",
                  "tool_call": true,
                  "temperature": false,
                  "limit": {"context": 1000000, "output": 128000}
                }
              },
              "providers": {
                "gateway": {
                  "id": "gateway",
                  "name": "Gateway",
                  "models": {
                    "gemini-3.6-flash": {
                      "id": "gemini-3.6-flash",
                      "limit": {"context": 1000000, "output": 64000}
                    }
                  }
                }
              }
            }
        """

        const val AMBIGUOUS_CANONICAL_CATALOG = """
            {
              "models": {
                "lab-a/shared": {"id": "lab-a/shared", "name": "First"},
                "lab-b/shared": {"id": "lab-b/shared", "name": "Second"}
              },
              "providers": {"gateway": {"id": "gateway", "models": {}}}
            }
        """
    }
}
