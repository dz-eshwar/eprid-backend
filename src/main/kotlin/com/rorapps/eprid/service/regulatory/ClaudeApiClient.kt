package com.rorapps.eprid.service.regulatory

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.rorapps.eprid.dto.regulatory.ClaudeRegulatoryAnalysis
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class ClaudeApiClient(
    private val objectMapper: ObjectMapper,
    @Value("\${app.anthropic.api-key:}") private val apiKey: String,
    @Value("\${app.anthropic.model:claude-haiku-4-5-20251001}") private val model: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client = WebClient.builder()
        .baseUrl("https://api.anthropic.com")
        .defaultHeader("x-api-key", apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .defaultHeader("content-type", "application/json")
        .build()

    /**
     * Calls Claude with a regulatory research prompt and returns structured analysis.
     * Returns null if the API key is not configured or the call fails.
     */
    fun analyseRegulatoryHistory(
        recyclerName: String,
        bwmrRegNumber: String?,
        state: String?
    ): ClaudeRegulatoryAnalysis? {
        if (apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY not configured — regulatory history check skipped")
            return null
        }

        val prompt = buildPrompt(recyclerName, bwmrRegNumber, state)

        return try {
            val requestBody = mapOf(
                "model" to model,
                "max_tokens" to 1024,
                "system" to SYSTEM_PROMPT,
                "messages" to listOf(mapOf("role" to "user", "content" to prompt))
            )

            val response = client.post()
                .uri("/v1/messages")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .block(Duration.ofSeconds(30))

            val text = response
                ?.get("content")
                ?.get(0)
                ?.get("text")
                ?.asText()
                ?: return null

            parseResponse(text)
        } catch (ex: Exception) {
            log.error("Claude API call failed for recycler '$recyclerName': ${ex.message}")
            null
        }
    }

    private fun buildPrompt(name: String, bwmrReg: String?, state: String?) = buildString {
        append("Research the regulatory compliance history of this battery waste recycler registered under BWMR 2022:\n\n")
        append("Name: $name\n")
        if (bwmrReg != null) append("BWMR Registration Number: $bwmrReg\n")
        if (state != null) append("State: $state\n")
        append("\nBased on your knowledge of CPCB enforcement actions, NGT (National Green Tribunal) orders, ")
        append("State PCB notices, and any relevant news or legal proceedings involving this entity, ")
        append("provide a structured risk assessment.\n\n")
        append("IMPORTANT: If you have no specific verified information about this particular recycler, ")
        append("state that clearly — do NOT fabricate findings. Report only what you know with reasonable confidence.\n\n")
        append("Return ONLY a JSON object (no markdown, no explanation outside JSON) with this exact structure:\n")
        append("""
{
  "overallRisk": "LOW|MEDIUM|HIGH|UNKNOWN",
  "rationale": "one paragraph explaining the risk level",
  "findings": [
    {
      "source": "CPCB|NGT|SPCB|NEWS|KNOWLEDGE_BASE",
      "findingType": "ENFORCEMENT_NOTICE|COURT_ORDER|SUSPENSION|NEWS_MENTION|NO_RECORD",
      "severity": "HIGH|MEDIUM|LOW|INFO",
      "title": "short title",
      "summary": "2-3 sentence description",
      "confidence": "HIGH|MEDIUM|LOW"
    }
  ],
  "recommendation": "one sentence on what the verifier should do next",
  "caveat": "note about data currency and limitations"
}
        """.trimIndent())
    }

    private fun parseResponse(text: String): ClaudeRegulatoryAnalysis? {
        // Extract JSON from the response — Claude may include preamble text despite instructions
        val jsonStart = text.indexOf('{')
        val jsonEnd   = text.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd < 0) return null

        return try {
            objectMapper.readValue(text.substring(jsonStart, jsonEnd + 1), ClaudeRegulatoryAnalysis::class.java)
        } catch (ex: Exception) {
            log.error("Failed to parse Claude regulatory response: ${ex.message}\nResponse: $text")
            null
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
You are a regulatory research assistant for E-PRid, an EPR (Extended Producer Responsibility)
certificate verification platform in India operating under BWMR 2022 (Battery Waste Management Rules).

Your role is to assess the regulatory compliance history of battery waste recyclers. You have knowledge
of CPCB (Central Pollution Control Board) enforcement actions, NGT (National Green Tribunal) rulings,
State PCB notices, and environmental news up to your training cutoff.

Rules:
1. Only report findings you are reasonably confident about. Never fabricate.
2. When uncertain, set confidence to LOW and note the limitation.
3. Most small/mid-size recyclers will have no public record — that is the most common finding (LOW risk, NO_RECORD).
4. Be specific about dates, case numbers, or order references if you know them.
5. Always include a caveat about your training data cutoff and the absence of real-time search.
6. Return only valid JSON — no markdown code fences, no preamble text.
        """.trimIndent()
    }
}
