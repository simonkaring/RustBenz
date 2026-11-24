package app.rustbenz.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Vehicle-side age, so stale data is never presented as live. */
internal fun age(observedAt: Instant?, now: Instant): String {
    observedAt ?: return "time unknown"
    val secs = Duration.between(observedAt, now).seconds.coerceAtLeast(0)
    return when {
        secs < 60 -> "just now"
        secs < 3_600 -> "${secs / 60} min ago"
        secs < 86_400 -> "${secs / 3_600} h ago"
        else -> "${secs / 86_400} days ago"
    }
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

internal fun clock(t: Instant): String = clockFormat.format(t.atZone(zone))

internal fun dayLabel(t: Instant, now: Instant = Instant.now()): String {
    val day = t.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayFormat.format(day)
    }
}

internal fun duration(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutesPart()
    return if (h > 0) "${h} h ${m} min" else "$m min"
}

internal fun km(v: Double): String = if (v >= 100) "%.0f".format(v) else "%.1f".format(v)

internal fun thousands(v: Int): String = "%,d".format(Locale.ENGLISH, v)
