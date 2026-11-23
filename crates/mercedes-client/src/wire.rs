//! Private JSON wire types and their normalization into `vehicle-core`.
//!
//! Shapes follow what mbapi2020 @ 6976c44 parses (`__init__.py` L54-167,
//! L199-227) and andersdissing/mercedes-explore `api.js`. Every field is
//! optional: unknown fields are ignored and missing ones become `None`.

use serde::Deserialize;
use vehicle_core::{Capabilities, Capability, Vehicle, VehicleId};

use crate::client::CommandCapability;

/// `GET /v2/vehicles`
#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub(crate) struct VehiclesResponse {
    assigned_vehicles: Vec<VehicleEntry>,
    fleets: Vec<Fleet>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
struct Fleet {
    booked_vehicles: Vec<VehicleEntry>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
struct VehicleEntry {
    vin: Option<String>,
    fin: Option<String>,
    sales_related_information: Option<SalesInformation>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(default)]
struct SalesInformation {
    baumuster: Option<Baumuster>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
struct Baumuster {
    baumuster_description: Option<String>,
}

impl VehiclesResponse {
    pub(crate) fn into_vehicles(self) -> Vec<Vehicle> {
        let mut vehicles: Vec<Vehicle> = Vec::new();
        let entries = self
            .fleets
            .into_iter()
            .flat_map(|f| f.booked_vehicles)
            .chain(self.assigned_vehicles);
        for entry in entries {
            // mbapi2020 falls back from `vin` to `fin`; entries with neither are unusable.
            let Some(id) = entry.vin.or(entry.fin).filter(|s| !s.trim().is_empty()) else {
                continue;
            };
            let id = VehicleId::new(id);
            if vehicles.iter().any(|v| v.id == id) {
                continue;
            }
            let model = entry
                .sales_related_information
                .and_then(|s| s.baumuster)
                .and_then(|b| b.baumuster_description)
                .filter(|s| !s.trim().is_empty());
            vehicles.push(Vehicle { id, model });
        }
        vehicles
    }
}

/// `GET /v1/vehicle/{vin}/capabilities/commands`
#[derive(Debug, Default, Deserialize)]
#[serde(default)]
pub(crate) struct CommandsResponse {
    commands: Vec<CommandEntry>,
}

#[derive(Debug, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
struct CommandEntry {
    command_name: Option<String>,
    is_available: Option<bool>,
}

impl CommandsResponse {
    pub(crate) fn into_commands(self) -> Vec<CommandCapability> {
        self.commands
            .into_iter()
            .filter_map(|c| {
                Some(CommandCapability {
                    name: c.command_name?,
                    available: c.is_available.unwrap_or(false),
                })
            })
            .collect()
    }
}

/// Maps Mercedes command names to domain capabilities. A capability is only
/// reported if every command it needs is marked available.
pub fn capabilities(commands: &[CommandCapability]) -> Capabilities {
    let available = |name: &str| commands.iter().any(|c| c.available && c.name == name);
    // Command names: mbapi2020 const.py L690/L706/L1913, switch.py L45, __init__.py L129.
    let table: [(Capability, &[&str]); 3] = [
        (
            Capability::ClimatePreconditioning,
            &["ZEV_PRECONDITIONING_START", "ZEV_PRECONDITIONING_STOP"],
        ),
        (
            Capability::ChargeProgramConfiguration,
            &["CHARGE_PROGRAM_CONFIGURE"],
        ),
        (Capability::RemoteLock, &["DOORS_LOCK"]),
    ];
    table
        .into_iter()
        .filter(|(_, needed)| needed.iter().all(|n| available(n)))
        .map(|(cap, _)| cap)
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    const VEHICLES: &str = include_str!("../tests/fixtures/vehicles.json");
    const COMMANDS: &str = include_str!("../tests/fixtures/capabilities_commands.json");

    #[test]
    fn vehicles_from_fleets_and_assigned_are_deduplicated() {
        let parsed: VehiclesResponse = serde_json::from_str(VEHICLES).expect("parse");
        let vehicles = parsed.into_vehicles();
        let ids: Vec<_> = vehicles.iter().map(|v| v.id.as_str()).collect();
        assert_eq!(
            ids,
            [
                "TESTVIN00000000002",
                "TESTVIN00000000001",
                "TESTFIN00000000003"
            ]
        );
        assert_eq!(vehicles[1].model.as_deref(), Some("EQA 250"));
        assert_eq!(vehicles[2].model, None);
    }

    #[test]
    fn empty_or_unexpected_vehicles_response_yields_no_vehicles() {
        for body in ["{}", r#"{"assignedVehicles": [{}]}"#, r#"{"unknown": 1}"#] {
            let parsed: VehiclesResponse = serde_json::from_str(body).expect("parse");
            assert!(parsed.into_vehicles().is_empty(), "{body}");
        }
    }

    #[test]
    fn command_capabilities_map_to_domain() {
        let parsed: CommandsResponse = serde_json::from_str(COMMANDS).expect("parse");
        let commands = parsed.into_commands();
        assert_eq!(commands.len(), 5, "entry without a name is dropped");
        let caps = capabilities(&commands);
        assert!(caps.contains(Capability::ClimatePreconditioning));
        assert!(
            !caps.contains(Capability::ChargeProgramConfiguration),
            "isAvailable=false"
        );
        assert!(
            !caps.contains(Capability::RemoteLock),
            "missing isAvailable means unavailable"
        );
    }

    #[test]
    fn preconditioning_requires_both_start_and_stop() {
        let commands = vec![CommandCapability {
            name: "ZEV_PRECONDITIONING_START".into(),
            available: true,
        }];
        assert!(!capabilities(&commands).contains(Capability::ClimatePreconditioning));
    }
}
