use std::fmt;

use serde::{Deserialize, Serialize};

/// Provider-scoped vehicle identifier. For Mercedes this is the VIN/FIN.
///
/// Treated as sensitive: `Debug` and `Display` redact all but the last four
/// characters so identifiers do not leak through logs. Use [`VehicleId::as_str`]
/// where the full value is genuinely required (API calls, storage keys).
#[derive(Clone, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(transparent)]
pub struct VehicleId(String);

impl VehicleId {
    pub fn new(id: impl Into<String>) -> Self {
        Self(id.into())
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    pub fn redacted(&self) -> String {
        let tail: String = self
            .0
            .chars()
            .rev()
            .take(4)
            .collect::<Vec<_>>()
            .into_iter()
            .rev()
            .collect();
        format!("…{tail}")
    }
}

impl fmt::Debug for VehicleId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "VehicleId({})", self.redacted())
    }
}

impl fmt::Display for VehicleId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.redacted())
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Vehicle {
    pub id: VehicleId,
    /// Human-readable model name as reported by the provider, if any.
    pub model: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn debug_and_display_redact_identifier() {
        let id = VehicleId::new("W1N2437011J000001");
        assert_eq!(id.to_string(), "…0001");
        assert_eq!(format!("{id:?}"), "VehicleId(…0001)");
        assert_eq!(id.as_str(), "W1N2437011J000001");
    }

    #[test]
    fn short_identifier_is_not_padded() {
        assert_eq!(VehicleId::new("AB").redacted(), "…AB");
    }
}
