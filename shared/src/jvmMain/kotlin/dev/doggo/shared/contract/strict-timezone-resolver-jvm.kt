package dev.doggo.shared.contract

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal object JvmStrictTimeZoneResolver : StrictTimeZoneResolver {
    private val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val timeRegex = Regex("""^\d{2}:\d{2}$""")

    override fun resolveOffset(zoneId: String, date: String, time: String): StrictZoneOffsetResult {
        val zone = parseZone(zoneId)
        val ldt = parseLocalDateTime(date, time)
        val rules = zone.rules
        val validOffsets = rules.getValidOffsets(ldt)

        return when (validOffsets.size) {
            0 -> {
                val transition = rules.getTransition(ldt)
                    ?: throw IllegalStateException("No valid offsets or transition found for $ldt in $zoneId")
                StrictZoneOffsetResult.Gap(
                    gapBeforeOffsetSeconds = transition.offsetBefore.totalSeconds.toLong(),
                    gapAfterOffsetSeconds = transition.offsetAfter.totalSeconds.toLong()
                )
            }
            1 -> {
                val offset = validOffsets[0]
                val epoch = ldt.atOffset(offset).toInstant().toEpochMilli()
                StrictZoneOffsetResult.Single(
                    offsetSeconds = offset.totalSeconds.toLong(),
                    epochMs = epoch
                )
            }
            2 -> {
                val off1 = validOffsets[0]
                val off2 = validOffsets[1]
                val (earlier, later) = if (off1.totalSeconds >= off2.totalSeconds) {
                    off1 to off2
                } else {
                    off2 to off1
                }
                val earlierEpoch = ldt.atOffset(earlier).toInstant().toEpochMilli()
                val laterEpoch = ldt.atOffset(later).toInstant().toEpochMilli()
                StrictZoneOffsetResult.Overlap(
                    earlierOffsetSeconds = earlier.totalSeconds.toLong(),
                    earlierEpochMs = earlierEpoch,
                    laterOffsetSeconds = later.totalSeconds.toLong(),
                    laterEpochMs = laterEpoch
                )
            }
            else -> throw IllegalStateException("Unexpected offset count (${validOffsets.size}) for $ldt in $zoneId")
        }
    }

    override fun resolveInstant(
        zoneId: String,
        date: String,
        time: String,
        chosenOffsetSeconds: Long?
    ): Long {
        return when (val res = resolveOffset(zoneId, date, time)) {
            is StrictZoneOffsetResult.Single -> {
                if (chosenOffsetSeconds != null) {
                    require(chosenOffsetSeconds == res.offsetSeconds) {
                        "Supplied offset ($chosenOffsetSeconds) does not match single valid offset (${res.offsetSeconds})"
                    }
                }
                res.epochMs
            }
            is StrictZoneOffsetResult.Overlap -> {
                requireNotNull(chosenOffsetSeconds) {
                    "Ambiguous local time $date $time in $zoneId requires explicit chosenOffsetSeconds (${res.earlierOffsetSeconds} or ${res.laterOffsetSeconds})"
                }
                when (chosenOffsetSeconds) {
                    res.earlierOffsetSeconds -> res.earlierEpochMs
                    res.laterOffsetSeconds -> res.laterEpochMs
                    else -> throw IllegalArgumentException(
                        "Chosen offset $chosenOffsetSeconds is neither earlier (${res.earlierOffsetSeconds}) nor later (${res.laterOffsetSeconds}) valid offset"
                    )
                }
            }
            is StrictZoneOffsetResult.Gap -> {
                throw IllegalArgumentException(
                    "Non-existent local time $date $time in $zoneId falls in DST gap (${res.gapBeforeOffsetSeconds} -> ${res.gapAfterOffsetSeconds})"
                )
            }
        }
    }

    override fun computeDayStartEpochMs(zoneId: String, date: String): Long {
        val zone = parseZone(zoneId)
        val localDate = parseLocalDate(date)
        return localDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    override fun computeNextCalendarDayStartEpochMs(zoneId: String, date: String): Long {
        val zone = parseZone(zoneId)
        val localDate = parseLocalDate(date)
        val nextLocalDate = localDate.plusDays(1)
        return nextLocalDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    override fun isZoneSupported(zoneId: String): Boolean {
        return try {
            ZoneId.of(zoneId)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseZone(zoneId: String): ZoneId {
        require(zoneId.isNotBlank()) { "zoneId must not be blank" }
        return try {
            ZoneId.of(zoneId)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid or unsupported IANA zone ID: '$zoneId'", e)
        }
    }

    private fun parseLocalDate(date: String): LocalDate {
        require(dateRegex.matches(date)) { "date must match YYYY-MM-DD: '$date'" }
        return try {
            LocalDate.parse(date)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid calendar date: '$date'", e)
        }
    }

    private fun parseLocalDateTime(date: String, time: String): LocalDateTime {
        val localDate = parseLocalDate(date)
        require(timeRegex.matches(time)) { "time must match HH:mm: '$time'" }
        val localTime = try {
            LocalTime.parse(time)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid local time: '$time'", e)
        }
        return LocalDateTime.of(localDate, localTime)
    }
}

actual fun getStrictTimeZoneResolver(): StrictTimeZoneResolver = JvmStrictTimeZoneResolver
