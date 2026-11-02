package dev.ww40.buildertimer.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L

/** Minute-precision countdown, e.g. "2d 5h 54m", "21h 52m", "8m"; the widget refreshes once a minute. */
fun formatRemaining(seconds: Long): String {
    if (seconds <= 0) return "Done"
    if (seconds < SECONDS_PER_MINUTE) return "<1m"
    val days = seconds / SECONDS_PER_DAY
    val hours = seconds % SECONDS_PER_DAY / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/** Coarse age for the "synced … ago" footer. */
fun formatAgo(seconds: Long): String {
    val clamped = seconds.coerceAtLeast(0)
    return when {
        clamped < SECONDS_PER_MINUTE -> "just now"
        clamped < SECONDS_PER_HOUR -> "${clamped / SECONDS_PER_MINUTE}m ago"
        clamped < SECONDS_PER_DAY -> "${clamped / SECONDS_PER_HOUR}h ago"
        else -> "${clamped / SECONDS_PER_DAY}d ago"
    }
}

private const val DAYS_SHOWN_AS_WEEKDAY = 6L

/** Finish time for the widget: "14:30" today, "Tue 14:30" this week, "12 Nov 14:30" beyond. */
fun formatFinishTime(
    finishEpochSec: Long,
    nowEpochSec: Long,
    zone: ZoneId,
    use24Hour: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    val finish = Instant.ofEpochSecond(finishEpochSec).atZone(zone)
    val today = Instant.ofEpochSecond(nowEpochSec).atZone(zone).toLocalDate()
    val time = if (use24Hour) "HH:mm" else "h:mm a"
    val daysAway = ChronoUnit.DAYS.between(today, finish.toLocalDate())
    val pattern = when {
        daysAway == 0L -> time
        daysAway in 1..DAYS_SHOWN_AS_WEEKDAY -> "EEE $time"
        else -> "d MMM $time"
    }
    return finish.format(DateTimeFormatter.ofPattern(pattern, locale))
}
