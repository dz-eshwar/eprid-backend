package com.rorapps.eprid.service.forensics

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.rorapps.eprid.constants.DateTolerancePolicy
import com.rorapps.eprid.constants.EvidenceType
import com.rorapps.eprid.dto.forensics.ForensicsCheckResult
import com.rorapps.eprid.dto.forensics.ForensicsCheckStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class StateMatchStatus { MATCH, MISMATCH, UNVERIFIABLE }

data class ExifResult(
    val latitude: Double?,
    val longitude: Double?,
    val datetime: Instant?,
    val device: String?,
    val resolvedState: String?,
    val stateMatch: StateMatchStatus,
    val checks: List<ForensicsCheckResult>
)

@Service
class ExifForensicsService(
    private val reverseGeocodingService: ReverseGeocodingService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val INDIA_LAT = 6.5..37.1
    private val INDIA_LON = 68.1..97.4

    /**
     * @param recyclerState The recycler's registered Indian state — used for
     *                      state-level geo matching (Fix 2). Null means we skip state matching.
     * @param evidenceType  Determines which date tolerance window to apply (Fix 1).
     */
    fun analyze(
        file: File,
        claimedProcessingDate: LocalDate,
        evidenceType: EvidenceType,
        recyclerState: String?
    ): ExifResult {
        val checks = mutableListOf<ForensicsCheckResult>()
        var latitude: Double? = null
        var longitude: Double? = null
        var datetime: Instant? = null
        var device: String? = null
        var resolvedState: String? = null
        var stateMatch = StateMatchStatus.UNVERIFIABLE

        runCatching {
            val metadata = ImageMetadataReader.readMetadata(file)

            // ── GPS: Fix 2 — now resolves to state, not just India bounding box ──
            val gps = metadata.getFirstDirectoryOfType(GpsDirectory::class.java)
            if (gps != null) {
                val loc = gps.geoLocation
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude

                    val inIndia = loc.latitude in INDIA_LAT && loc.longitude in INDIA_LON

                    if (!inIndia) {
                        // Clearly outside India — no need to call reverse geocoding
                        stateMatch = StateMatchStatus.MISMATCH
                        checks += ForensicsCheckResult(
                            checkName = "GPS location",
                            status = ForensicsCheckStatus.FAIL,
                            detail = "Coordinates (%.4f, %.4f) are outside India — unexpected for a BWMR-registered recycler"
                                .format(loc.latitude, loc.longitude)
                        )
                    } else {
                        // Inside India — now resolve to actual state
                        val geo = reverseGeocodingService.resolveState(loc.latitude, loc.longitude)
                        resolvedState = geo?.stateName

                        if (geo == null || resolvedState == null) {
                            stateMatch = StateMatchStatus.UNVERIFIABLE
                            checks += ForensicsCheckResult(
                                checkName = "GPS location — state verification",
                                status = ForensicsCheckStatus.UNVERIFIABLE,
                                detail = "Coordinates (%.4f, %.4f) are within India but state could not be resolved from reverse geocoding"
                                    .format(loc.latitude, loc.longitude)
                            )
                        } else if (recyclerState == null) {
                            // No registered state on record — can only confirm India
                            stateMatch = StateMatchStatus.UNVERIFIABLE
                            checks += ForensicsCheckResult(
                                checkName = "GPS location — state verification",
                                status = ForensicsCheckStatus.PASS,
                                detail = "Coordinates (%.4f, %.4f) are within India, resolved to $resolvedState. " +
                                         "No registered state on file for this recycler — state matching skipped."
                                    .format(loc.latitude, loc.longitude)
                            )
                        } else {
                            // Compare resolved state with recycler's registered state
                            val matched = statesMatch(resolvedState!!, recyclerState)
                            stateMatch = if (matched) StateMatchStatus.MATCH else StateMatchStatus.MISMATCH
                            checks += ForensicsCheckResult(
                                checkName = "GPS location — state verification",
                                status = if (matched) ForensicsCheckStatus.PASS else ForensicsCheckStatus.FAIL,
                                detail = if (matched)
                                    "Photo taken in $resolvedState — matches recycler's registered state ($recyclerState) ✓"
                                else
                                    "Photo taken in $resolvedState but recycler is registered in $recyclerState — state mismatch is a significant red flag"
                            )
                        }
                    }
                } else {
                    checks += unverifiable(
                        "GPS location — state verification",
                        "GPS directory present but no coordinates embedded"
                    )
                }
            } else {
                checks += unverifiable(
                    "GPS location — state verification",
                    "No GPS data in image — geotag was not set or stripped (common when photos are shared via WhatsApp or email)"
                )
            }

            // ── Timestamp: Fix 1 — uses evidence-type-specific date tolerance ──
            val exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            if (exif != null) {
                val date = exif.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                if (date != null) {
                    datetime = date.toInstant()
                    val photoDate = date.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                    val verdict = DateTolerancePolicy.evaluate(photoDate, claimedProcessingDate, evidenceType)

                    checks += ForensicsCheckResult(
                        checkName = "Timestamp check (${evidenceType.label})",
                        status = if (verdict.pass) ForensicsCheckStatus.PASS else ForensicsCheckStatus.FAIL,
                        detail = verdict.detail
                    )
                } else {
                    checks += unverifiable(
                        "Timestamp check (${evidenceType.label})",
                        "No DateTimeOriginal tag in EXIF — timestamp was not recorded or was stripped"
                    )
                }
            } else {
                checks += unverifiable(
                    "Timestamp check (${evidenceType.label})",
                    "No EXIF data found — metadata may have been stripped from this image"
                )
            }

            // ── Device identification ──────────────────────────────────────────
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            if (ifd0 != null) {
                val make = ifd0.getString(ExifIFD0Directory.TAG_MAKE)
                val model = ifd0.getString(ExifIFD0Directory.TAG_MODEL)
                device = listOfNotNull(make, model).joinToString(" ").ifBlank { null }
                checks += ForensicsCheckResult(
                    checkName = "Device identification",
                    status = ForensicsCheckStatus.PASS,
                    detail = "Captured with: ${device ?: "unknown device"}"
                )
            } else {
                checks += unverifiable(
                    "Device identification",
                    "No IFD0 directory — device metadata was not recorded or was stripped"
                )
            }

        }.onFailure { ex ->
            log.warn("EXIF extraction failed for ${file.name}: ${ex.message}")
            checks += unverifiable("EXIF extraction", "Could not read image metadata: ${ex.message}")
        }

        return ExifResult(latitude, longitude, datetime, device, resolvedState, stateMatch, checks)
    }

    /**
     * Normalises both strings and checks if they refer to the same Indian state.
     * Handles common variations: "Tamil Nadu" vs "tamilnadu", "Odisha" vs "Orissa", etc.
     */
    private fun statesMatch(resolved: String, registered: String): Boolean {
        val a = normalise(resolved)
        val b = normalise(registered)
        if (a == b || a.contains(b) || b.contains(a)) return true

        // Known alternate name pairs
        val aliases = mapOf(
            "odisha" to "orissa",
            "uttarakhand" to "uttaranchal",
            "telangana" to "andhra pradesh",
            "jharkhand" to "bihar",
            "chhattisgarh" to "madhya pradesh"
        )
        return aliases[a] == b || aliases[b] == a
    }

    private fun normalise(s: String) = s.lowercase().replace(Regex("[^a-z]"), "")

    private fun unverifiable(name: String, detail: String) =
        ForensicsCheckResult(name, ForensicsCheckStatus.UNVERIFIABLE, detail)
}
