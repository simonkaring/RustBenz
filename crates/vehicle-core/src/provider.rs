use std::future::Future;

use crate::{Capabilities, ProviderError, Vehicle, VehicleId, VehicleState};

/// Read access to the vehicles of one authenticated account.
///
/// Remote commands are intentionally absent until status retrieval has been
/// verified against a live vehicle (see `knowledge/decisions/`).
pub trait VehicleProvider {
    fn vehicles(&self) -> impl Future<Output = Result<Vec<Vehicle>, ProviderError>> + Send;

    fn state(
        &self,
        id: &VehicleId,
    ) -> impl Future<Output = Result<VehicleState, ProviderError>> + Send;

    fn capabilities(
        &self,
        id: &VehicleId,
    ) -> impl Future<Output = Result<Capabilities, ProviderError>> + Send;
}
