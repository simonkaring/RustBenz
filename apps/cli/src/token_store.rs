//! Token persistence in the OS credential store (Windows Credential Manager,
//! macOS Keychain, Linux Secret Service). Tokens never touch the filesystem.
//!
//! Windows limits one credential to 2560 bytes and Mercedes tokens are larger,
//! so the serialized token set is split across `<account>.<n>` entries. The
//! `<account>` entry holds the chunk count and is written last.

use mercedes_client::TokenSet;

const SERVICE: &str = "rustbenz";
const ACCOUNT: &str = "mercedes-europe";
/// Below the 2560-byte Windows `CRED_MAX_CREDENTIAL_BLOB_SIZE`.
const CHUNK_BYTES: usize = 2048;

type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

pub fn load() -> Result<Option<TokenSet>> {
    let Some(bytes) = load_bytes(SERVICE, ACCOUNT)? else {
        return Ok(None);
    };
    serde_json::from_slice(&bytes)
        .map(Some)
        .map_err(|_| CORRUPT.into())
}

pub fn save(tokens: &TokenSet) -> Result<()> {
    save_bytes(SERVICE, ACCOUNT, &serde_json::to_vec(tokens)?)
}

/// Returns whether anything was deleted.
pub fn delete() -> Result<bool> {
    delete_bytes(SERVICE, ACCOUNT)
}

const CORRUPT: &str = "stored session is unreadable; run `eqa auth logout` and log in again";

fn entry(service: &str, account: &str) -> Result<keyring::Entry> {
    Ok(keyring::Entry::new(service, account)?)
}

fn chunk_entry(service: &str, account: &str, i: usize) -> Result<keyring::Entry> {
    entry(service, &format!("{account}.{i}"))
}

fn chunk_count(service: &str, account: &str) -> Result<Option<usize>> {
    match entry(service, account)?.get_secret() {
        Ok(raw) => {
            let count = std::str::from_utf8(&raw)
                .ok()
                .and_then(|s| s.parse().ok())
                .ok_or(CORRUPT)?;
            Ok(Some(count))
        }
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(e) => Err(e.into()),
    }
}

fn load_bytes(service: &str, account: &str) -> Result<Option<Vec<u8>>> {
    let Some(count) = chunk_count(service, account)? else {
        return Ok(None);
    };
    let mut bytes = Vec::new();
    for i in 0..count {
        match chunk_entry(service, account, i)?.get_secret() {
            Ok(chunk) => bytes.extend_from_slice(&chunk),
            Err(keyring::Error::NoEntry) => return Err(CORRUPT.into()),
            Err(e) => return Err(e.into()),
        }
    }
    Ok(Some(bytes))
}

fn save_bytes(service: &str, account: &str, bytes: &[u8]) -> Result<()> {
    delete_bytes(service, account)?;
    let chunks: Vec<&[u8]> = bytes.chunks(CHUNK_BYTES).collect();
    for (i, chunk) in chunks.iter().enumerate() {
        chunk_entry(service, account, i)?.set_secret(chunk)?;
    }
    entry(service, account)?.set_secret(chunks.len().to_string().as_bytes())?;
    Ok(())
}

fn delete_bytes(service: &str, account: &str) -> Result<bool> {
    let count = match chunk_count(service, account) {
        Ok(Some(n)) => n,
        Ok(None) => return Ok(false),
        // Unreadable header: still remove it so the user can recover.
        Err(_) => 0,
    };
    for i in 0..count {
        ignore_missing(chunk_entry(service, account, i)?.delete_credential())?;
    }
    ignore_missing(entry(service, account)?.delete_credential())?;
    Ok(true)
}

fn ignore_missing(result: keyring::Result<()>) -> Result<()> {
    match result {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(e.into()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Touches the real OS credential store, so it is opt-in:
    /// `cargo test -p eqa -- --ignored`.
    #[test]
    #[ignore = "uses the OS credential store"]
    fn large_secret_round_trips_through_os_store() -> Result<()> {
        let (service, account) = ("rustbenz-selftest", "chunking");
        let data: Vec<u8> = (0..7000u32).map(|i| (i % 251) as u8).collect();

        save_bytes(service, account, &data)?;
        assert_eq!(load_bytes(service, account)?, Some(data.clone()));

        // Overwriting with a smaller value must not leave stale chunks behind.
        save_bytes(service, account, b"small")?;
        assert_eq!(load_bytes(service, account)?, Some(b"small".to_vec()));

        assert!(delete_bytes(service, account)?);
        assert_eq!(load_bytes(service, account)?, None);
        assert!(!delete_bytes(service, account)?);
        Ok(())
    }
}
