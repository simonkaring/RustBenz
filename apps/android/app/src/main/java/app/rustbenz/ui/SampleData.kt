package app.rustbenz.ui

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Design-preview data with realistic EQA 250 figures (66.5 kWh usable, 11 kW AC,
 * 100 kW DC, ~17-19 kWh/100 km). Everything here is tagged [Source.Sample] in the UI.
 */
object SampleData {
    private val now: Instant get() = Instant.now()
    private fun ago(minutes: Long) = now.minus(minutes, ChronoUnit.MINUTES)
    private fun <T> s(v: T) = Shown(v, Source.Sample)

    fun car(name: String, displayId: String) = CarUi(
        name = name,
        displayId = displayId,
        soc = s(64),
        rangeKm = s(268),
        chargeLimit = s(80),
        charging = s(ChargeState.NotCharging),
        pluggedIn = s(false),
        powerKw = null,
        climateOn = s(false),
        locked = s(true),
        openings = s(Openings(doorsClosed = true, windowsClosed = true, trunkClosed = true, sunroofClosed = true)),
        odometerKm = s(48_213),
        cabinC = s(9.5),
        outsideC = s(6.0),
        retrievedAt = null,
    )

    /** Mirrors what Mercedes reported for the target EQA on 2026-11-23 (knowledge/mercedes/capabilities.md). */
    val availability: Map<Control, Availability> = mapOf(
        Control.Climate to Availability.Available,
        Control.Charge to Availability.Available,
        Control.Lock to Availability.Unavailable("Mercedes reports remote locking as unavailable for this car. It may require a Digital Extras package."),
        Control.Windows to Availability.Unavailable("Mercedes reports remote window control as unavailable for this car."),
        Control.Signal to Availability.Unavailable("Mercedes reports horn & lights as unavailable for this car."),
        Control.Locate to Availability.Unavailable("Vehicle location is not supported by RustBenz yet."),
    )

    /** Power curve of a DC session (kW per minute) for the live-session chart. */
    val livePowerCurve: List<Float> = listOf(
        42f, 78f, 96f, 99f, 100f, 98f, 97f, 95f, 92f, 88f, 84f, 80f, 77f, 74f, 71f, 68f, 64f, 61f, 58f, 55f, 52f, 49f,
    )

    val chargeSessions: List<ChargeSession> = listOf(
        ChargeSession(ago(60 * 20), Duration.ofMinutes(372), 34.2, ChargeType.AC, 11.0, 28, 80, "Home", 61.6),
        ChargeSession(ago(60 * 70), Duration.ofMinutes(31), 29.8, ChargeType.DC, 98.4, 14, 62, "Ionity Fredericia", 104.3),
        ChargeSession(ago(60 * 118), Duration.ofMinutes(405), 37.5, ChargeType.AC, 11.0, 24, 80, "Home", 67.5),
        ChargeSession(ago(60 * 166), Duration.ofMinutes(96), 9.6, ChargeType.AC, 7.2, 58, 72, "Office", 0.0),
        ChargeSession(ago(60 * 214), Duration.ofMinutes(358), 33.1, ChargeType.AC, 11.0, 30, 80, "Home", 59.6),
        ChargeSession(ago(60 * 262), Duration.ofMinutes(27), 26.4, ChargeType.DC, 87.9, 19, 61, "Clever Vejle", 89.8),
    )

    val trips: List<Trip> = listOf(
        Trip(ago(95), Duration.ofMinutes(24), 18.6, 17.2, 5, "Home", "Office"),
        Trip(ago(60 * 9), Duration.ofMinutes(31), 21.4, 18.9, 6, "Office", "Home"),
        Trip(ago(60 * 26), Duration.ofMinutes(12), 6.3, 21.5, 2, "Home", "Supermarket"),
        Trip(ago(60 * 27), Duration.ofMinutes(11), 6.1, 16.4, 1, "Supermarket", "Home"),
        Trip(ago(60 * 50), Duration.ofMinutes(148), 192.7, 19.8, 57, "Home", "Aarhus"),
        Trip(ago(60 * 74), Duration.ofMinutes(26), 19.0, 17.8, 5, "Home", "Office"),
    )

    /** Distance per day for the last 7 days, oldest first. */
    val weekDistanceKm: List<Pair<String, Float>> = listOf(
        "Mon" to 38f, "Tue" to 41f, "Wed" to 12f, "Thu" to 199f, "Fri" to 40f, "Sat" to 25f, "Sun" to 40f,
    )

    /** Consumption trend, kWh/100 km per day, oldest first. */
    val weekConsumption: List<Float> = listOf(17.9f, 18.4f, 21.0f, 19.6f, 18.1f, 17.2f, 17.5f)

    val departures: List<Departure> = listOf(
        Departure("Weekday commute", "07:15", "Mon–Fri", enabled = true),
        Departure("Weekend", "09:30", "Sat, Sun", enabled = false),
    )

    val telemetry: List<SignalGroup> = listOf(
        SignalGroup(
            "High-voltage battery",
            listOf(
                SignalRow("State of health", "96 %", Source.Sample),
                SignalRow("Usable capacity", "64.1 kWh", Source.Sample),
                SignalRow("Battery temperature", "14 °C", Source.Sample),
                SignalRow("Max charge limit", "80 %", Source.Sample),
            ),
        ),
        SignalGroup(
            "12 V system",
            listOf(
                SignalRow("Starter battery", "12.6 V · Good", Source.Sample),
            ),
        ),
        SignalGroup(
            "Efficiency",
            listOf(
                SignalRow("Consumption since reset", "18.3 kWh/100 km", Source.Sample),
                SignalRow("Consumption since start", "17.2 kWh/100 km", Source.Sample),
                SignalRow("Eco score", "82 / 100", Source.Sample),
            ),
        ),
        SignalGroup(
            "Service",
            listOf(
                SignalRow("Next service", "In 11,800 km or 214 days", Source.Sample),
                SignalRow("Software", "Up to date", Source.Sample),
            ),
        ),
    )

    /** Tire pressures in bar: front-left, front-right, rear-left, rear-right. */
    val tiresBar: List<Float> = listOf(2.7f, 2.7f, 2.6f, 2.9f)
}
