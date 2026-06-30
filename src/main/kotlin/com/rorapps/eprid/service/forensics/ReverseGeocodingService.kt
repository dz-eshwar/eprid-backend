package com.rorapps.eprid.service.forensics

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/**
 * Calls the Nominatim (OpenStreetMap) reverse-geocoding API to resolve
 * GPS coordinates → Indian state name.
 *
 * Nominatim is free and requires no API key, but mandates a descriptive
 * User-Agent header and a rate limit of max 1 request/second.
 * For production volume, host a private Nominatim instance or switch to
 * a paid provider (Google Maps, HERE, Mapbox).
 */
@Service
class ReverseGeocodingService {

    private val log = LoggerFactory.getLogger(javaClass)

    private val client = WebClient.builder()
        .baseUrl("https://nominatim.openstreetmap.org")
        .defaultHeader("User-Agent", "E-PRid/1.0 (EPR certificate verification; contact: admin@eprid.in)")
        .defaultHeader("Accept", "application/json")
        .build()

    data class GeoState(
        val stateName: String?,
        val countryCode: String?,
        val displayName: String?
    )

    /**
     * Returns the Indian state name at the given coordinates, or null if unavailable.
     * Silently returns null on any network/parse error — caller treats as UNVERIFIABLE.
     */
    fun resolveState(latitude: Double, longitude: Double): GeoState? {
        return runCatching {
            val response = client.get()
                .uri { builder ->
                    builder.path("/reverse")
                        .queryParam("lat", latitude)
                        .queryParam("lon", longitude)
                        .queryParam("format", "json")
                        .queryParam("zoom", 5)  // zoom=5 returns state-level detail
                        .queryParam("addressdetails", 1)
                        .build()
                }
                .retrieve()
                .bodyToMono(NominatimResponse::class.java)
                .block(Duration.ofSeconds(5))

            GeoState(
                stateName = response?.address?.state,
                countryCode = response?.address?.countryCode?.uppercase(),
                displayName = response?.displayName
            )
        }.onFailure { ex ->
            log.warn("Reverse geocoding failed for ($latitude, $longitude): ${ex.message}")
        }.getOrNull()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class NominatimResponse(
        @JsonProperty("display_name") val displayName: String?,
        @JsonProperty("address") val address: NominatimAddress?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class NominatimAddress(
        @JsonProperty("state") val state: String?,
        @JsonProperty("country_code") val countryCode: String?,
        @JsonProperty("country") val country: String?
    )
}
