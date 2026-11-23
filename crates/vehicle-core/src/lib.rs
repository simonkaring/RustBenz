//! Manufacturer-independent vehicle domain.
//!
//! Provider crates (e.g. `mercedes-client`) normalize their API responses into
//! these types. Nothing in here may depend on a specific manufacturer.

mod capability;
mod error;
mod provider;
mod state;
mod vehicle;

pub use capability::{Capabilities, Capability};
pub use error::ProviderError;
pub use provider::VehicleProvider;
pub use state::{
    BatteryState, ChargingState, ChargingStatus, ClimateState, Kilometers, Percent, Reading,
    VehicleState,
};
pub use vehicle::{Vehicle, VehicleId};
