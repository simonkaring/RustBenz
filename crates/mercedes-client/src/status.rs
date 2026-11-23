//! Normalizes Mercedes status protobuf into [`VehicleState`].
//!
//! Rules: an attribute whose status is not VALID becomes `None`; unknown enum
//! codes become `Unrecognized`; nothing is defaulted. Codes and units come from
//! the schema enums (see `proto.rs`) and mbapi2020 `vsu_enums.py` L93-109.

use std::time::{Duration, SystemTime, UNIX_EPOCH};

use vehicle_core::{ChargingStatus, Kilometers, Percent, Reading, VehicleId, VehicleState};

use crate::proto::{self, vehicle_attribute_status as vas};

const STATUS_VALID: i32 = 0;
const DISTANCE_UNSPECIFIED: i32 = 0;
const DISTANCE_KILOMETERS: i32 = 1;
const RATIO_UNSPECIFIED: i32 = 0;
const RATIO_PERCENT: i32 = 1;

fn charging_status(code: i32) -> ChargingStatus {
    // proto.Chargingstatus; legacy VEP ints use the same numbers.
    match code {
        0 | 5 | 6 | 9 | 10 | 11 | 13 | 14 => ChargingStatus::Charging,
        1 => ChargingStatus::Complete,
        2 | 3 | 8 | 12 => ChargingStatus::NotCharging,
        4 => ChargingStatus::Fault,
        // 7 DISCHARGING, 15 SOH calibration, 16 UNKNOWN, and anything new.
        _ => ChargingStatus::Unrecognized,
    }
}

/// Distances are only accepted in kilometres. shortcut: whether `value` is in
/// the display `unit` or always km is unverified, so miles are dropped rather
/// than guessed; revisit when non-EU regions are added.
fn kilometers(value: f64, unit: i32) -> Option<Kilometers> {
    matches!(unit, DISTANCE_UNSPECIFIED | DISTANCE_KILOMETERS)
        .then(|| Kilometers::new(value))
        .flatten()
}

fn timestamp(ts: Option<&proto::Timestamp>) -> Option<SystemTime> {
    let ts = ts?;
    let secs = u64::try_from(ts.seconds).ok().filter(|s| *s > 0)?;
    let nanos = u32::try_from(ts.nanos).unwrap_or(0);
    Some(UNIX_EPOCH + Duration::new(secs, nanos))
}

/// Returns the observation time if the attribute is valid, `None` otherwise.
fn valid(metadata: Option<&proto::Metadata>) -> Option<Option<SystemTime>> {
    match metadata {
        // proto3 omits default fields; a missing metadata block means VALID with no time.
        None => Some(None),
        Some(m) if m.status == STATUS_VALID => Some(timestamp(m.timestamp.as_ref())),
        Some(_) => None,
    }
}

pub(crate) fn from_vsu(
    id: VehicleId,
    retrieved_at: SystemTime,
    u: &proto::VehicleStatusUpdate,
) -> VehicleState {
    let mut s = VehicleState::empty(id, retrieved_at);

    s.battery.state_of_charge = u.soc.as_ref().and_then(|a| {
        let at = valid(a.metadata.as_ref())?;
        matches!(a.unit, RATIO_UNSPECIFIED | RATIO_PERCENT)
            .then(|| Percent::new(a.value as f64))
            .flatten()
            .map(|p| Reading::new(p, at))
    });
    s.battery.electric_range = u.rangeelectric.as_ref().and_then(|a| {
        let at = valid(a.metadata.as_ref())?;
        kilometers(a.value as f64, a.unit).map(|k| Reading::new(k, at))
    });
    s.charging.status = u.chargingstatus.as_ref().and_then(|a| {
        let at = valid(a.metadata.as_ref())?;
        Some(Reading::new(charging_status(a.value), at))
    });
    s.charging.power_kw = u.charging_power.as_ref().and_then(|a| {
        let at = valid(a.metadata.as_ref())?;
        a.value.is_finite().then(|| Reading::new(a.value, at))
    });
    s.charging.plugged_in = u.charge_inlets.as_ref().and_then(|a| {
        let at = valid(a.metadata.as_ref())?;
        let states = || a.value.iter().map(|e| e.coupler_state);
        let plugged = if states().any(|c| c == 1 || c == 2) {
            true
        } else if states().any(|c| c == 3) {
            false
        } else {
            return None; // only UNKNOWN / DEFECT inlets
        };
        Some(Reading::new(plugged, at))
    });
    s.climate.preconditioning_active = u.precond_active.as_ref().and_then(|a| {
        let at = valid(a.metadata.as_ref())?;
        Some(Reading::new(a.value, at))
    });
    s
}

pub(crate) fn from_vep(
    id: VehicleId,
    retrieved_at: SystemTime,
    u: &proto::VepUpdate,
) -> VehicleState {
    let attr = |name: &str| {
        let a = u.attributes.get(name)?;
        if a.status != STATUS_VALID {
            return None;
        }
        let at = u64::try_from(a.timestamp_in_ms)
            .ok()
            .filter(|ms| *ms > 0)
            .map(|ms| UNIX_EPOCH + Duration::from_millis(ms));
        Some((a, at))
    };
    let number = |a: &proto::VehicleAttributeStatus| match a.value {
        Some(vas::Value::Int(v)) => Some(v as f64),
        Some(vas::Value::Double(v)) => Some(v),
        _ => None,
    };
    let boolean = |a: &proto::VehicleAttributeStatus| match a.value {
        Some(vas::Value::Bool(v)) => Some(v),
        _ => None,
    };

    let mut s = VehicleState::empty(id, retrieved_at);
    s.battery.state_of_charge =
        attr("soc").and_then(|(a, at)| Percent::new(number(a)?).map(|p| Reading::new(p, at)));
    s.battery.electric_range = attr("rangeelectric").and_then(|(a, at)| {
        let unit = match a.display_unit {
            Some(vas::DisplayUnit::DistanceUnit(u)) => u,
            None => DISTANCE_UNSPECIFIED,
        };
        kilometers(number(a)?, unit).map(|k| Reading::new(k, at))
    });
    s.charging.status = attr("chargingstatus").and_then(|(a, at)| match a.value {
        Some(vas::Value::Int(v)) => {
            let code = i32::try_from(v).unwrap_or(i32::MAX);
            Some(Reading::new(charging_status(code), at))
        }
        _ => None,
    });
    s.charging.power_kw = attr("chargingPower").and_then(|(a, at)| {
        number(a)
            .filter(|v| v.is_finite())
            .map(|v| Reading::new(v, at))
    });
    s.climate.preconditioning_active =
        attr("precondActive").and_then(|(a, at)| boolean(a).map(|v| Reading::new(v, at)));
    s
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::{
        BoolAttribute, ChargeInletsArrayAttribute, ChargeInletsEntry, DoubleAttribute,
        EnumAttribute, Int64DistanceAttribute, Int64RatioAttribute, Metadata, Timestamp,
        VehicleAttributeStatus, VehicleStatusUpdate, VepUpdate,
    };

    fn meta(status: i32) -> Option<Metadata> {
        Some(Metadata {
            timestamp: Some(Timestamp {
                seconds: 1_800_000_000,
                nanos: 0,
            }),
            status,
        })
    }

    fn id() -> VehicleId {
        VehicleId::new("TESTVIN00000000001")
    }

    fn full_vsu() -> VehicleStatusUpdate {
        VehicleStatusUpdate {
            fin_or_vin: "TESTVIN00000000001".into(),
            full_update: true,
            soc: Some(Int64RatioAttribute {
                value: 80,
                metadata: meta(0),
                unit: 1,
            }),
            rangeelectric: Some(Int64DistanceAttribute {
                value: 312,
                metadata: meta(0),
                unit: 1,
            }),
            chargingstatus: Some(EnumAttribute {
                value: 1,
                metadata: meta(0),
            }),
            charging_power: Some(DoubleAttribute {
                value: 0.0,
                metadata: meta(0),
            }),
            charge_inlets: Some(ChargeInletsArrayAttribute {
                value: vec![ChargeInletsEntry { coupler_state: 2 }],
                metadata: meta(0),
            }),
            precond_active: Some(BoolAttribute {
                value: false,
                metadata: meta(0),
            }),
        }
    }

    #[test]
    fn vsu_maps_all_signals_with_timestamps() {
        let s = from_vsu(id(), UNIX_EPOCH, &full_vsu());
        let t = Some(UNIX_EPOCH + Duration::from_secs(1_800_000_000));
        assert_eq!(
            s.battery
                .state_of_charge
                .as_ref()
                .map(|r| (r.value.get(), r.observed_at)),
            Some((80.0, t))
        );
        assert_eq!(s.battery.electric_range.map(|r| r.value.get()), Some(312.0));
        assert_eq!(
            s.charging.status.map(|r| r.value),
            Some(ChargingStatus::Complete)
        );
        assert_eq!(
            s.charging.power_kw.map(|r| r.value),
            Some(0.0),
            "valid zero is kept"
        );
        assert_eq!(s.charging.plugged_in.map(|r| r.value), Some(true));
        assert_eq!(
            s.climate.preconditioning_active.map(|r| r.value),
            Some(false)
        );
    }

    #[test]
    fn vsu_invalid_or_missing_attributes_are_unavailable() {
        let mut u = full_vsu();
        u.soc = Some(Int64RatioAttribute {
            value: 0,
            metadata: meta(4),
            unit: 1,
        }); // NOT_AVAILABLE
        u.rangeelectric = Some(Int64DistanceAttribute {
            value: 0,
            metadata: meta(3),
            unit: 1,
        }); // INVALID
        u.chargingstatus = None;
        u.charge_inlets = Some(ChargeInletsArrayAttribute {
            value: vec![ChargeInletsEntry { coupler_state: 0 }],
            metadata: meta(0),
        });
        let s = from_vsu(id(), UNIX_EPOCH, &u);
        assert!(s.battery.state_of_charge.is_none());
        assert!(s.battery.electric_range.is_none());
        assert!(s.charging.status.is_none());
        assert!(s.charging.plugged_in.is_none(), "only UNKNOWN inlets");
    }

    #[test]
    fn vsu_proto3_default_charging_status_is_charging() {
        // value 0 is omitted on the wire; present attribute + VALID means CHARGING.
        let mut u = full_vsu();
        u.chargingstatus = Some(EnumAttribute {
            value: 0,
            metadata: meta(0),
        });
        let bytes = prost::Message::encode_to_vec(&u);
        let decoded: VehicleStatusUpdate =
            prost::Message::decode(bytes.as_slice()).expect("decode");
        let s = from_vsu(id(), UNIX_EPOCH, &decoded);
        assert_eq!(
            s.charging.status.map(|r| r.value),
            Some(ChargingStatus::Charging)
        );
    }

    #[test]
    fn miles_and_out_of_range_values_are_dropped() {
        let mut u = full_vsu();
        u.rangeelectric = Some(Int64DistanceAttribute {
            value: 200,
            metadata: meta(0),
            unit: 2,
        });
        u.soc = Some(Int64RatioAttribute {
            value: 140,
            metadata: meta(0),
            unit: 1,
        });
        let s = from_vsu(id(), UNIX_EPOCH, &u);
        assert!(s.battery.electric_range.is_none());
        assert!(s.battery.state_of_charge.is_none());
    }

    #[test]
    fn charging_status_codes() {
        assert_eq!(charging_status(14), ChargingStatus::Charging);
        assert_eq!(charging_status(3), ChargingStatus::NotCharging);
        assert_eq!(charging_status(4), ChargingStatus::Fault);
        assert_eq!(charging_status(7), ChargingStatus::Unrecognized);
        assert_eq!(charging_status(99), ChargingStatus::Unrecognized);
    }

    #[test]
    fn vep_maps_named_attributes() {
        let attr = |value, status| VehicleAttributeStatus {
            timestamp_in_ms: 1_800_000_000_000,
            status,
            display_unit: None,
            value: Some(value),
        };
        let mut u = VepUpdate {
            full_update: true,
            ..Default::default()
        };
        u.attributes
            .insert("soc".into(), attr(vas::Value::Int(55), 0));
        u.attributes
            .insert("rangeelectric".into(), attr(vas::Value::Int(210), 0));
        u.attributes
            .insert("chargingstatus".into(), attr(vas::Value::Int(3), 0));
        u.attributes
            .insert("precondActive".into(), attr(vas::Value::Bool(true), 0));
        u.attributes
            .insert("chargingPower".into(), attr(vas::Value::Nil(true), 0));

        let s = from_vep(id(), UNIX_EPOCH, &u);
        assert_eq!(s.battery.state_of_charge.map(|r| r.value.get()), Some(55.0));
        assert_eq!(s.battery.electric_range.map(|r| r.value.get()), Some(210.0));
        assert_eq!(
            s.charging.status.map(|r| r.value),
            Some(ChargingStatus::NotCharging)
        );
        assert_eq!(
            s.climate.preconditioning_active.map(|r| r.value),
            Some(true)
        );
        assert!(s.charging.power_kw.is_none(), "nil_value is unavailable");
        assert!(s.charging.plugged_in.is_none());
    }
}
