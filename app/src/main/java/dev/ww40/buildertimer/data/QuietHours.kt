package dev.ww40.buildertimer.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Hours (0–23) during which finish notifications are held back; the window may wrap past midnight. */
data class QuietHours(val startHour: Int, val endHour: Int) {

    init {
        require(startHour in HOURS && endHour in HOURS) { "Quiet hours must be 0–23, got $startHour–$endHour" }
    }

    fun contains(hour: Int): Boolean = when {
        startHour == endHour -> false
        startHour < endHour -> hour in startHour until endHour
        else -> hour >= startHour || hour < endHour
    }

    /** When to notify for an upgrade finishing at [finishEpochSec]: unchanged, or the end of the quiet window. */
    fun deliveryEpochSec(finishEpochSec: Long, zone: ZoneId): Long {
        val finish = ZonedDateTime.ofInstant(Instant.ofEpochSecond(finishEpochSec), zone)
        if (!contains(finish.hour)) return finishEpochSec
        var end = finish.withHour(endHour).withMinute(0).withSecond(0).withNano(0)
        if (!end.isAfter(finish)) end = end.plusDays(1)
        return end.toEpochSecond()
    }

    private companion object {
        val HOURS = 0..23
    }
}
