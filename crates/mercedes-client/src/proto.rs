//! Hand-transcribed subset of the Mercedes mobile protobuf schema (package `proto`).
//!
//! Field numbers and types were read from the generated descriptors in
//! ReneNulschDE/mbapi2020 @ 6976c44 (`proto/client_pb2.py`,
//! `proto/vehicle_events_pb2.py`; MIT). Only fields RustBenz reads or sends
//! are declared; prost skips everything else, including unknown `oneof` tags.
//! See knowledge/mercedes/vehicle-state.md.

use std::collections::HashMap;

// ---- PushMessage (server -> client) ----

#[derive(Clone, PartialEq, prost::Message)]
pub struct PushMessage {
    #[prost(oneof = "push_message::Msg", tags = "2, 24")]
    pub msg: Option<push_message::Msg>,
}

pub mod push_message {
    #[derive(Clone, PartialEq, prost::Oneof)]
    pub enum Msg {
        /// Legacy attribute updates.
        #[prost(message, tag = "2")]
        VepUpdates(super::VepUpdatesByVin),
        /// Typed attribute updates (current).
        #[prost(message, tag = "24")]
        VehicleStatusUpdates(super::VehicleStatusUpdates),
    }
}

// ---- ClientMessage (client -> server) ----

#[derive(Clone, PartialEq, prost::Message)]
pub struct ClientMessage {
    #[prost(oneof = "client_message::Msg", tags = "22, 28")]
    pub msg: Option<client_message::Msg>,
}

pub mod client_message {
    #[derive(Clone, PartialEq, prost::Oneof)]
    pub enum Msg {
        #[prost(message, tag = "22")]
        AcknowledgeVepUpdatesByVin(super::AcknowledgeVepUpdatesByVin),
        #[prost(message, tag = "28")]
        AcknowledgeVehicleStatusUpdates(super::AcknowledgeVehicleStatusUpdates),
    }
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct AcknowledgeVepUpdatesByVin {
    #[prost(int32, tag = "1")]
    pub sequence_number: i32,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct AcknowledgeVehicleStatusUpdates {
    #[prost(int64, tag = "1")]
    pub sequence_number: i64,
}

// ---- Typed status updates (VSU) ----

#[derive(Clone, PartialEq, prost::Message)]
pub struct VehicleStatusUpdates {
    #[prost(int64, tag = "1")]
    pub sequence_number: i64,
    /// Keyed by VIN/FIN.
    #[prost(map = "string, message", tag = "2")]
    pub vehicle_status_updates: HashMap<String, VehicleStatusUpdate>,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct VehicleStatusUpdate {
    #[prost(string, tag = "1")]
    pub fin_or_vin: String,
    #[prost(bool, tag = "2")]
    pub full_update: bool,
    #[prost(message, optional, tag = "26")]
    pub charge_inlets: Option<ChargeInletsArrayAttribute>,
    #[prost(message, optional, tag = "37")]
    pub charging_power: Option<DoubleAttribute>,
    #[prost(message, optional, tag = "50")]
    pub chargingstatus: Option<EnumAttribute>,
    #[prost(message, optional, tag = "167")]
    pub precond_active: Option<BoolAttribute>,
    #[prost(message, optional, tag = "183")]
    pub rangeelectric: Option<Int64DistanceAttribute>,
    #[prost(message, optional, tag = "196")]
    pub soc: Option<Int64RatioAttribute>,
}

/// `VSUMetadata`
#[derive(Clone, PartialEq, prost::Message)]
pub struct Metadata {
    #[prost(message, optional, tag = "1")]
    pub timestamp: Option<Timestamp>,
    /// `AttributeStatus`: VALID=0, NOT_RECEIVED=1, INVALID=3, NOT_AVAILABLE=4.
    #[prost(int32, tag = "2")]
    pub status: i32,
}

/// `google.protobuf.Timestamp`
#[derive(Clone, PartialEq, prost::Message)]
pub struct Timestamp {
    #[prost(int64, tag = "1")]
    pub seconds: i64,
    #[prost(int32, tag = "2")]
    pub nanos: i32,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct BoolAttribute {
    #[prost(bool, tag = "1")]
    pub value: bool,
    #[prost(message, optional, tag = "2")]
    pub metadata: Option<Metadata>,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct DoubleAttribute {
    #[prost(double, tag = "1")]
    pub value: f64,
    #[prost(message, optional, tag = "2")]
    pub metadata: Option<Metadata>,
}

/// Any `*EnumAttribute` (value is the raw enum number).
#[derive(Clone, PartialEq, prost::Message)]
pub struct EnumAttribute {
    #[prost(int32, tag = "1")]
    pub value: i32,
    #[prost(message, optional, tag = "2")]
    pub metadata: Option<Metadata>,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct Int64RatioAttribute {
    #[prost(int64, tag = "1")]
    pub value: i64,
    #[prost(message, optional, tag = "2")]
    pub metadata: Option<Metadata>,
    /// `RatioUnit`: UNSPECIFIED=0, PERCENT=1.
    #[prost(int32, tag = "3")]
    pub unit: i32,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct Int64DistanceAttribute {
    #[prost(int64, tag = "1")]
    pub value: i64,
    #[prost(message, optional, tag = "2")]
    pub metadata: Option<Metadata>,
    /// `DistanceUnit`: UNSPECIFIED=0, KILOMETERS=1, MILES=2.
    #[prost(int32, tag = "3")]
    pub unit: i32,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct ChargeInletsArrayAttribute {
    #[prost(message, repeated, tag = "1")]
    pub value: Vec<ChargeInletsEntry>,
    #[prost(message, optional, tag = "2")]
    pub metadata: Option<Metadata>,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct ChargeInletsEntry {
    /// `ChargeInletsCouplerState`: UNKNOWN=0, PLUGGED=1, VEHICLE_PLUGGED=2,
    /// VEHICLE_NOT_PLUGGED=3, DEFECT=5.
    #[prost(int32, tag = "2")]
    pub coupler_state: i32,
}

// ---- Legacy attribute updates (VEP) ----

#[derive(Clone, PartialEq, prost::Message)]
pub struct VepUpdatesByVin {
    #[prost(map = "string, message", tag = "1")]
    pub updates: HashMap<String, VepUpdate>,
    #[prost(int32, tag = "2")]
    pub sequence_number: i32,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct VepUpdate {
    #[prost(bool, tag = "15")]
    pub full_update: bool,
    #[prost(map = "string, message", tag = "11")]
    pub attributes: HashMap<String, VehicleAttributeStatus>,
}

#[derive(Clone, PartialEq, prost::Message)]
pub struct VehicleAttributeStatus {
    #[prost(int64, tag = "10")]
    pub timestamp_in_ms: i64,
    /// Same numbering as `AttributeStatus` (vsu_helper.py L61-71).
    #[prost(int32, tag = "3")]
    pub status: i32,
    #[prost(oneof = "vehicle_attribute_status::DisplayUnit", tags = "26")]
    pub display_unit: Option<vehicle_attribute_status::DisplayUnit>,
    #[prost(oneof = "vehicle_attribute_status::Value", tags = "4, 5, 7, 8")]
    pub value: Option<vehicle_attribute_status::Value>,
}

pub mod vehicle_attribute_status {
    #[derive(Clone, Copy, PartialEq, prost::Oneof)]
    pub enum DisplayUnit {
        #[prost(int32, tag = "26")]
        DistanceUnit(i32),
    }

    #[derive(Clone, PartialEq, prost::Oneof)]
    pub enum Value {
        #[prost(int64, tag = "4")]
        Int(i64),
        #[prost(bool, tag = "5")]
        Bool(bool),
        #[prost(double, tag = "7")]
        Double(f64),
        #[prost(bool, tag = "8")]
        Nil(bool),
    }
}
