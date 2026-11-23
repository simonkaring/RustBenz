use std::time::SystemTime;

use serde::{Deserialize, Serialize};

use crate::VehicleId;

/// A value reported by the vehicle together with when the vehicle observed it.
///
/// Signals are modelled as `Option<Reading<T>>`: `None` means the provider did
/// not report the signal (unavailable / unsupported), which is distinct from a
/// reported zero.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Reading<T> {
    pub value: T,
    /// Vehicle-side timestamp, if the provider supplied one.
    pub observed_at: Option<SystemTime>,
}

impl<T> Reading<T> {
    pub fn new(value: T, observed_at: Option<SystemTime>) -> Self {
        Self { value, observed_at }
    }
}

/// Percentage in the inclusive range 0–100.
#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Serialize, Deserialize)]
#[serde(try_from = "f64", into = "f64")]
pub struct Percent(f64);

impl Percent {
    pub fn new(value: f64) -> Option<Self> {
        (0.0..=100.0).contains(&value).then_some(Self(value))
    }

    pub fn get(self) -> f64 {
        self.0
    }
}

impl TryFrom<f64> for Percent {
    type Error = String;
    fn try_from(value: f64) -> Result<Self, Self::Error> {
        Self::new(value).ok_or_else(|| format!("percentage out of range: {value}"))
    }
}

impl From<Percent> for f64 {
    fn from(p: Percent) -> f64 {
        p.0
    }
}

/// Distance in kilometres (non-negative, finite).
#[derive(Debug, Clone, Copy, PartialEq, PartialOrd, Serialize, Deserialize)]
#[serde(try_from = "f64", into = "f64")]
pub struct Kilometers(f64);

impl Kilometers {
    pub fn new(value: f64) -> Option<Self> {
        (value.is_finite() && value >= 0.0).then_some(Self(value))
    }

    pub fn get(self) -> f64 {
        self.0
    }
}

impl TryFrom<f64> for Kilometers {
    type Error = String;
    fn try_from(value: f64) -> Result<Self, Self::Error> {
        Self::new(value).ok_or_else(|| format!("invalid distance: {value}"))
    }
}

impl From<Kilometers> for f64 {
    fn from(k: Kilometers) -> f64 {
        k.0
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ChargingStatus {
    Charging,
    NotCharging,
    Complete,
    Fault,
    /// Provider reported a value this version does not understand.
    Unrecognized,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct BatteryState {
    pub state_of_charge: Option<Reading<Percent>>,
    pub electric_range: Option<Reading<Kilometers>>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct ChargingState {
    pub status: Option<Reading<ChargingStatus>>,
    pub plugged_in: Option<Reading<bool>>,
    pub power_kw: Option<Reading<f64>>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct ClimateState {
    pub preconditioning_active: Option<Reading<bool>>,
}

/// Snapshot of a vehicle's state as known by the provider.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct VehicleState {
    pub vehicle_id: VehicleId,
    /// When this client received the snapshot. Individual readings carry their
    /// own vehicle-side timestamps; this is not a freshness guarantee.
    pub retrieved_at: SystemTime,
    pub battery: BatteryState,
    pub charging: ChargingState,
    pub climate: ClimateState,
}

impl VehicleState {
    pub fn empty(vehicle_id: VehicleId, retrieved_at: SystemTime) -> Self {
        Self {
            vehicle_id,
            retrieved_at,
            battery: BatteryState::default(),
            charging: ChargingState::default(),
            climate: ClimateState::default(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn percent_rejects_out_of_range() {
        assert!(Percent::new(-0.1).is_none());
        assert!(Percent::new(100.1).is_none());
        assert!(Percent::new(f64::NAN).is_none());
        assert_eq!(Percent::new(0.0).map(Percent::get), Some(0.0));
        assert_eq!(Percent::new(100.0).map(Percent::get), Some(100.0));
    }

    #[test]
    fn kilometers_rejects_negative_and_non_finite() {
        assert!(Kilometers::new(-1.0).is_none());
        assert!(Kilometers::new(f64::INFINITY).is_none());
        assert!(Kilometers::new(0.0).is_some());
    }

    #[test]
    fn deserializing_invalid_percent_fails() {
        assert!(serde_json::from_str::<Percent>("101").is_err());
        assert!(serde_json::from_str::<Percent>("55").is_ok());
    }

    #[test]
    fn unavailable_signal_differs_from_zero() {
        let mut state = VehicleState::empty(VehicleId::new("X"), SystemTime::UNIX_EPOCH);
        assert!(state.battery.state_of_charge.is_none());
        state.battery.state_of_charge = Percent::new(0.0).map(|p| Reading::new(p, None));
        assert_eq!(
            state.battery.state_of_charge.map(|r| r.value.get()),
            Some(0.0)
        );
    }

    #[test]
    fn state_round_trips_through_json() {
        let mut state = VehicleState::empty(VehicleId::new("VIN123"), SystemTime::UNIX_EPOCH);
        state.charging.status = Some(Reading::new(ChargingStatus::Charging, None));
        let json = serde_json::to_string(&state).expect("serialize");
        let back: VehicleState = serde_json::from_str(&json).expect("deserialize");
        assert_eq!(back, state);
    }
}
