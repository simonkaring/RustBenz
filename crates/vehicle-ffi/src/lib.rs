//! UniFFI surface for iOS and Android.
//!
//! Exported records mirror `vehicle-core` types in FFI-friendly shapes; domain
//! crates stay free of binding attributes. No network-facing functions are
//! exported yet: Swift/Kotlin generation and on-device tests come with the
//! mobile phase (see knowledge/decisions/uniffi-interop.md).

use std::time::SystemTime;

use vehicle_core as core;

uniffi::setup_scaffolding!();

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Vehicle {
    /// Full provider identifier (VIN). Never log it.
    pub id: String,
    pub model: Option<String>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NumberReading {
    pub value: f64,
    pub observed_at: Option<SystemTime>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BoolReading {
    pub value: bool,
    pub observed_at: Option<SystemTime>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ChargingStatus {
    Charging,
    NotCharging,
    Complete,
    Fault,
    Unrecognized,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ChargingStatusReading {
    pub value: ChargingStatus,
    pub observed_at: Option<SystemTime>,
}

/// `None` fields mean the vehicle did not report the signal.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct VehicleState {
    pub vehicle_id: String,
    pub retrieved_at: SystemTime,
    pub state_of_charge_percent: Option<NumberReading>,
    pub electric_range_km: Option<NumberReading>,
    pub charging_status: Option<ChargingStatusReading>,
    pub plugged_in: Option<BoolReading>,
    pub charging_power_kw: Option<NumberReading>,
    pub preconditioning_active: Option<BoolReading>,
}

impl From<core::Vehicle> for Vehicle {
    fn from(v: core::Vehicle) -> Self {
        Self {
            id: v.id.as_str().to_owned(),
            model: v.model,
        }
    }
}

impl From<core::ChargingStatus> for ChargingStatus {
    fn from(s: core::ChargingStatus) -> Self {
        match s {
            core::ChargingStatus::Charging => Self::Charging,
            core::ChargingStatus::NotCharging => Self::NotCharging,
            core::ChargingStatus::Complete => Self::Complete,
            core::ChargingStatus::Fault => Self::Fault,
            core::ChargingStatus::Unrecognized => Self::Unrecognized,
        }
    }
}

fn number<T: Into<f64>>(r: core::Reading<T>) -> NumberReading {
    NumberReading {
        value: r.value.into(),
        observed_at: r.observed_at,
    }
}

fn boolean(r: core::Reading<bool>) -> BoolReading {
    BoolReading {
        value: r.value,
        observed_at: r.observed_at,
    }
}

impl From<core::VehicleState> for VehicleState {
    fn from(s: core::VehicleState) -> Self {
        Self {
            vehicle_id: s.vehicle_id.as_str().to_owned(),
            retrieved_at: s.retrieved_at,
            state_of_charge_percent: s.battery.state_of_charge.map(number),
            electric_range_km: s.battery.electric_range.map(number),
            charging_status: s.charging.status.map(|r| ChargingStatusReading {
                value: r.value.into(),
                observed_at: r.observed_at,
            }),
            plugged_in: s.charging.plugged_in.map(boolean),
            charging_power_kw: s.charging.power_kw.map(number),
            preconditioning_active: s.climate.preconditioning_active.map(boolean),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn converts_state_preserving_absence_and_timestamps() {
        let t = SystemTime::UNIX_EPOCH;
        let mut s = core::VehicleState::empty(core::VehicleId::new("VIN1"), t);
        s.battery.state_of_charge = core::Percent::new(0.0).map(|p| core::Reading::new(p, Some(t)));
        s.charging.status = Some(core::Reading::new(core::ChargingStatus::Complete, None));

        let ffi = VehicleState::from(s);
        assert_eq!(ffi.vehicle_id, "VIN1");
        assert_eq!(
            ffi.state_of_charge_percent,
            Some(NumberReading {
                value: 0.0,
                observed_at: Some(t)
            })
        );
        assert_eq!(
            ffi.charging_status.map(|r| r.value),
            Some(ChargingStatus::Complete)
        );
        assert_eq!(ffi.electric_range_km, None);
        assert_eq!(ffi.preconditioning_active, None);
    }
}
