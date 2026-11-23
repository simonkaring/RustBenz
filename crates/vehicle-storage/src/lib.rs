//! Local-first persistence of vehicle state snapshots in SQLite.
//!
//! Snapshots are stored as JSON documents of [`VehicleState`] so the schema
//! does not need a migration every time a domain signal is added.

use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

use rusqlite::{Connection, OptionalExtension, params};
use vehicle_core::{VehicleId, VehicleState};

#[derive(Debug, thiserror::Error)]
pub enum StorageError {
    #[error("database error")]
    Database(#[from] rusqlite::Error),
    #[error("corrupt snapshot")]
    Corrupt(#[from] serde_json::Error),
    #[error("database schema version {0} is newer than this build supports")]
    UnsupportedSchema(i64),
}

/// Ordered list of migrations; index + 1 is the resulting `user_version`.
const MIGRATIONS: &[&str] = &["CREATE TABLE snapshots (
        id           INTEGER PRIMARY KEY,
        vehicle_id   TEXT    NOT NULL,
        retrieved_ms INTEGER NOT NULL,
        state_json   TEXT    NOT NULL
    );
    CREATE INDEX snapshots_by_vehicle ON snapshots (vehicle_id, retrieved_ms);"];

pub struct Store {
    conn: Connection,
}

impl Store {
    pub fn open(path: &Path) -> Result<Self, StorageError> {
        Self::init(Connection::open(path)?)
    }

    pub fn open_in_memory() -> Result<Self, StorageError> {
        Self::init(Connection::open_in_memory()?)
    }

    fn init(mut conn: Connection) -> Result<Self, StorageError> {
        let version: i64 = conn.pragma_query_value(None, "user_version", |r| r.get(0))?;
        let known = MIGRATIONS.len() as i64;
        if version > known {
            return Err(StorageError::UnsupportedSchema(version));
        }
        for (i, sql) in MIGRATIONS.iter().enumerate().skip(version as usize) {
            let tx = conn.transaction()?;
            tx.execute_batch(sql)?;
            tx.pragma_update(None, "user_version", i as i64 + 1)?;
            tx.commit()?;
        }
        Ok(Self { conn })
    }

    pub fn save_snapshot(&self, state: &VehicleState) -> Result<(), StorageError> {
        self.conn.execute(
            "INSERT INTO snapshots (vehicle_id, retrieved_ms, state_json) VALUES (?1, ?2, ?3)",
            params![
                state.vehicle_id.as_str(),
                unix_ms(state.retrieved_at),
                serde_json::to_string(state)?
            ],
        )?;
        Ok(())
    }

    pub fn latest_snapshot(&self, id: &VehicleId) -> Result<Option<VehicleState>, StorageError> {
        Ok(self.history(id, 1)?.pop())
    }

    /// Most recent snapshots first.
    pub fn history(&self, id: &VehicleId, limit: u32) -> Result<Vec<VehicleState>, StorageError> {
        let mut stmt = self.conn.prepare(
            "SELECT state_json FROM snapshots WHERE vehicle_id = ?1
             ORDER BY retrieved_ms DESC, id DESC LIMIT ?2",
        )?;
        let rows = stmt.query_map(params![id.as_str(), limit], |r| r.get::<_, String>(0))?;
        rows.map(|json| Ok(serde_json::from_str(&json?)?)).collect()
    }

    pub fn schema_version(&self) -> Result<i64, StorageError> {
        Ok(self
            .conn
            .query_row("PRAGMA user_version", [], |r| r.get(0))
            .optional()?
            .unwrap_or(0))
    }
}

fn unix_ms(t: SystemTime) -> i64 {
    // Pre-epoch timestamps cannot come from a real vehicle; clamp to 0.
    t.duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use vehicle_core::{Percent, Reading};

    use super::*;

    fn state(vin: &str, secs: u64, soc: f64) -> VehicleState {
        let mut s =
            VehicleState::empty(VehicleId::new(vin), UNIX_EPOCH + Duration::from_secs(secs));
        s.battery.state_of_charge = Percent::new(soc).map(|p| Reading::new(p, None));
        s
    }

    #[test]
    fn latest_snapshot_is_most_recent_for_vehicle() -> Result<(), StorageError> {
        let store = Store::open_in_memory()?;
        store.save_snapshot(&state("A", 10, 50.0))?;
        store.save_snapshot(&state("A", 20, 60.0))?;
        store.save_snapshot(&state("B", 30, 70.0))?;

        let latest = store.latest_snapshot(&VehicleId::new("A"))?;
        assert_eq!(latest, Some(state("A", 20, 60.0)));
        assert_eq!(store.history(&VehicleId::new("A"), 10)?.len(), 2);
        assert_eq!(store.latest_snapshot(&VehicleId::new("C"))?, None);
        Ok(())
    }

    #[test]
    fn migrations_are_idempotent_across_reopen() -> Result<(), Box<dyn std::error::Error>> {
        let dir = std::env::temp_dir().join(format!("rustbenz-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir)?;
        let path = dir.join("store.sqlite");
        let _ = std::fs::remove_file(&path);

        Store::open(&path)?.save_snapshot(&state("A", 1, 1.0))?;
        let reopened = Store::open(&path)?;
        assert_eq!(reopened.schema_version()?, MIGRATIONS.len() as i64);
        assert!(reopened.latest_snapshot(&VehicleId::new("A"))?.is_some());
        drop(reopened);
        let _ = std::fs::remove_dir_all(&dir);
        Ok(())
    }

    #[test]
    fn newer_schema_is_rejected() -> Result<(), StorageError> {
        let conn = Connection::open_in_memory()?;
        conn.pragma_update(None, "user_version", 99)?;
        assert!(matches!(
            Store::init(conn),
            Err(StorageError::UnsupportedSchema(99))
        ));
        Ok(())
    }
}
