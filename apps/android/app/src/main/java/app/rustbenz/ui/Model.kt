package app.rustbenz.ui

import app.rustbenz.core.ChargingStatus
import app.rustbenz.core.VehicleState
import java.time.Duration
import java.time.Instant

/** Where a displayed value came from. Always rendered, so cached or sample data never looks live. */
sealed interface Source {
    /** Reported by the car; [at] is the vehicle-side measurement time. */
    data class Reported(val at: Instant?) : Source

    /** Design-preview data for features not wired to the car yet. */
    data object Sample : Source
}

data class Shown<T>(val value: T, val source: Source)

enum class ChargeState { Charging, NotCharging, Complete, Fault, Unknown }

data class Openings(val doorsClosed: Boolean, val windowsClosed: Boolean, val trunkClosed: Boolean, val sunroofClosed: Boolean) {
    val allClosed get() = doorsClosed && windowsClosed && trunkClosed && sunroofClosed
}

/** Everything the Home/Vehicle screens show about the car. `null` = not reported. */
data class CarUi(
    val name: String,
    val displayId: String,
    val soc: Shown<Int>?,
    val rangeKm: Shown<Int>?,
    val chargeLimit: Shown<Int>?,
    val charging: Shown<ChargeState>?,
    val pluggedIn: Shown<Boolean>?,
    val powerKw: Shown<Double>?,
    val climateOn: Shown<Boolean>?,
    val locked: Shown<Boolean>?,
    val openings: Shown<Openings>?,
    val odometerKm: Shown<Int>?,
    val cabinC: Shown<Double>?,
    val outsideC: Shown<Double>?,
    val retrievedAt: Instant?,
)

enum class Control(val label: String) { Climate("Climate"), Lock("Lock"), Charge("Charging"), Locate("Locate"), Windows("Vent"), Signal("Horn & lights") }

sealed interface Availability {
    data object Available : Availability
    data class Unavailable(val reason: String) : Availability
}

enum class ChargeType { AC, DC }

data class ChargeSession(
    val start: Instant,
    val duration: Duration,
    val energyKwh: Double,
    val type: ChargeType,
    val peakKw: Double,
    val socFrom: Int,
    val socTo: Int,
    val place: String,
    val costDkk: Double,
)

data class Trip(
    val start: Instant,
    val duration: Duration,
    val distanceKm: Double,
    val kwhPer100: Double,
    val socUsed: Int,
    val from: String,
    val to: String,
)

data class Departure(val label: String, val time: String, val days: String, val enabled: Boolean)

data class SignalRow(val label: String, val value: String, val source: Source)

data class SignalGroup(val title: String, val rows: List<SignalRow>)

/**
 * Builds the UI model. In preview mode everything is sample data. In live mode
 * every car field comes from [state]; fields the Rust core does not provide yet
 * are `null` ("not reported") so untagged sample values never mix with real ones.
 */
fun carUi(name: String, displayId: String, state: VehicleState?, preview: Boolean): CarUi {
    if (preview) return SampleData.car(name, displayId)
    return CarUi(
        name = name,
        displayId = displayId,
        chargeLimit = null,
        locked = null,
        openings = null,
        odometerKm = null,
        cabinC = null,
        outsideC = null,
        soc = state?.stateOfChargePercent?.let { Shown(it.value.toInt(), Source.Reported(it.observedAt)) },
        rangeKm = state?.electricRangeKm?.let { Shown(it.value.toInt(), Source.Reported(it.observedAt)) },
        charging = state?.chargingStatus?.let { Shown(it.value.toUi(), Source.Reported(it.observedAt)) },
        pluggedIn = state?.pluggedIn?.let { Shown(it.value, Source.Reported(it.observedAt)) },
        powerKw = state?.chargingPowerKw?.let { Shown(it.value, Source.Reported(it.observedAt)) },
        climateOn = state?.preconditioningActive?.let { Shown(it.value, Source.Reported(it.observedAt)) },
        retrievedAt = state?.retrievedAt,
    )
}

private fun ChargingStatus.toUi() = when (this) {
    ChargingStatus.CHARGING -> ChargeState.Charging
    ChargingStatus.NOT_CHARGING -> ChargeState.NotCharging
    ChargingStatus.COMPLETE -> ChargeState.Complete
    ChargingStatus.FAULT -> ChargeState.Fault
    ChargingStatus.UNRECOGNIZED -> ChargeState.Unknown
}
