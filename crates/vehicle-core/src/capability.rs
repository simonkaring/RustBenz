use std::collections::BTreeSet;

use serde::{Deserialize, Serialize};

/// A feature the provider reports as available for a specific vehicle and
/// account. Availability can depend on hardware, region and subscriptions.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
pub enum Capability {
    /// Start and stop remote climate preconditioning.
    ClimatePreconditioning,
    /// Configure charge program / maximum state of charge.
    ChargeProgramConfiguration,
    /// Remote locking.
    RemoteLock,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Capabilities(BTreeSet<Capability>);

impl Capabilities {
    pub fn contains(&self, capability: Capability) -> bool {
        self.0.contains(&capability)
    }

    pub fn iter(&self) -> impl Iterator<Item = Capability> + '_ {
        self.0.iter().copied()
    }
}

impl FromIterator<Capability> for Capabilities {
    fn from_iter<I: IntoIterator<Item = Capability>>(iter: I) -> Self {
        Self(iter.into_iter().collect())
    }
}
