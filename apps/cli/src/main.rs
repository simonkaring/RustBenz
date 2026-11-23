//! `eqa` — developer CLI for the RustBenz Mercedes integration.
//!
//! Talks to the live, unofficial Mercedes API using the account you log in
//! with. It only reads data; it issues no remote vehicle commands.

mod token_store;

use std::io::{BufRead, Write};
use std::path::PathBuf;
use std::time::{Duration, SystemTime};

use clap::{Parser, Subcommand};
use mercedes_client::{AuthError, Endpoints, MercedesClient, PkceLogin, TokenSet};
use vehicle_core::{ProviderError, Reading, Vehicle, VehicleProvider, VehicleState};

type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

#[derive(Parser)]
#[command(
    name = "eqa",
    version,
    about = "RustBenz developer CLI (unofficial Mercedes API, Europe)"
)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// Log in, inspect or remove the stored Mercedes session.
    #[command(subcommand)]
    Auth(AuthCommand),
    /// Read vehicle data (read-only; no remote commands).
    #[command(subcommand)]
    Vehicles(VehiclesCommand),
}

#[derive(Subcommand)]
enum AuthCommand {
    /// Sign in via the Mercedes login page in your browser.
    Login,
    /// Show whether a session is stored and when it expires.
    Status,
    /// Remove stored tokens from the OS credential store.
    Logout,
}

#[derive(Subcommand)]
enum VehiclesCommand {
    /// List vehicles on the account (identifiers are redacted).
    List,
    /// Show battery, charging and climate status (one push-channel connection).
    Status {
        /// Last characters of the VIN; required if the account has several vehicles.
        #[arg(long)]
        vehicle: Option<String>,
    },
    /// Show remote features available for a vehicle.
    Capabilities {
        #[arg(long)]
        vehicle: Option<String>,
    },
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .with_writer(std::io::stderr)
        .init();

    if let Err(e) = run(Cli::parse()).await {
        eprint!("error: {e}");
        let mut source = e.source();
        while let Some(s) = source {
            eprint!(": {s}");
            source = s.source();
        }
        eprintln!();
        std::process::exit(1);
    }
}

async fn run(cli: Cli) -> Result<()> {
    let http = mercedes_client::http_client()?;
    let endpoints = Endpoints::europe();
    match cli.command {
        Command::Auth(AuthCommand::Login) => login(&http, &endpoints).await,
        Command::Auth(AuthCommand::Status) => auth_status(),
        Command::Auth(AuthCommand::Logout) => {
            let deleted = token_store::delete()?;
            println!(
                "{}",
                if deleted {
                    "Logged out."
                } else {
                    "No stored session."
                }
            );
            Ok(())
        }
        Command::Vehicles(cmd) => {
            let client = session(&http, &endpoints).await?;
            vehicles(&client, cmd).await
        }
    }
}

async fn login(http: &reqwest::Client, endpoints: &Endpoints) -> Result<()> {
    let login = PkceLogin::new(endpoints)?;
    println!(
        "1. Open DevTools (F12) in your browser, select the Network tab and enable \"Preserve log\".\n\
         2. Open this URL in that tab and sign in to Mercedes me:\n\n   {}\n\n\
         3. After sign-in the browser tries to open rismycar://login-callback?code=… and fails.\n   \
            If you are already signed in, this happens immediately without a login form.\n   \
            In the Network tab, filter for \"login-callback\": copy the rismycar:// request URL,\n   \
            or the `Location` response header of the last authorization/resume request.\n   \
            Use this run's URL; a code from an earlier attempt will not work.\n",
        login.authorization_url()
    );
    let line = read_non_empty_line("Callback URL: ")?;
    let tokens = login.complete(http, endpoints, &line).await?;
    token_store::save(&tokens)?;
    println!("Logged in. Tokens stored in the OS credential store.");
    Ok(())
}

/// Pastes often start with a line break; skip blank lines instead of failing.
fn read_non_empty_line(prompt: &str) -> Result<String> {
    let stdin = std::io::stdin();
    loop {
        print!("{prompt}");
        std::io::stdout().flush()?;
        let mut line = String::new();
        if stdin.lock().read_line(&mut line)? == 0 {
            return Err("no input received".into());
        }
        if !line.trim().is_empty() {
            return Ok(line);
        }
    }
}

fn auth_status() -> Result<()> {
    match token_store::load()? {
        None => println!("Not logged in. Run `eqa auth login`."),
        Some(tokens) => {
            let now = SystemTime::now();
            match tokens.expires_at.duration_since(now) {
                Ok(left) => println!(
                    "Logged in. Access token valid for {} min.",
                    left.as_secs() / 60
                ),
                Err(_) => println!("Access token expired."),
            }
            println!(
                "Refresh token: {}",
                if tokens.can_refresh() {
                    "stored"
                } else {
                    "none (log in again when expired)"
                }
            );
        }
    }
    Ok(())
}

/// Loads stored tokens, refreshing (and re-storing) them if they are about to expire.
async fn session(http: &reqwest::Client, endpoints: &Endpoints) -> Result<MercedesClient> {
    let tokens = token_store::load()?.ok_or(ProviderError::NotAuthenticated)?;
    let tokens = if tokens.expires_within(SystemTime::now(), Duration::from_secs(60)) {
        refresh(http, endpoints, &tokens).await?
    } else {
        tokens
    };
    Ok(MercedesClient::new(
        http.clone(),
        endpoints.clone(),
        &tokens,
    ))
}

async fn refresh(
    http: &reqwest::Client,
    endpoints: &Endpoints,
    tokens: &TokenSet,
) -> Result<TokenSet> {
    match mercedes_client::refresh(http, endpoints, tokens).await {
        Ok(fresh) => {
            token_store::save(&fresh)?;
            Ok(fresh)
        }
        Err(AuthError::Rejected(_) | AuthError::NoRefreshToken) => {
            Err(ProviderError::SessionExpired.into())
        }
        Err(e) => Err(e.into()),
    }
}

async fn vehicles(client: &MercedesClient, cmd: VehiclesCommand) -> Result<()> {
    let all = client.vehicles().await?;
    match cmd {
        VehiclesCommand::List => {
            if all.is_empty() {
                println!("No vehicles on this account.");
            }
            for v in &all {
                println!(
                    "{}  {}",
                    v.id,
                    v.model.as_deref().unwrap_or("(unknown model)")
                );
            }
        }
        VehiclesCommand::Capabilities { vehicle } => {
            let v = select(&all, vehicle.as_deref())?;
            let commands = client.command_capabilities(&v.id).await?;
            let caps = mercedes_client::capabilities_from_commands(&commands);
            println!("Vehicle {}:", v.id);
            for cap in caps.iter() {
                println!("  supported: {cap:?}");
            }
            let available = commands.iter().filter(|c| c.available).count();
            println!(
                "Raw Mercedes commands ({available}/{} available):",
                commands.len()
            );
            for c in &commands {
                println!("  [{}] {}", if c.available { "x" } else { " " }, c.name);
            }
        }
        VehiclesCommand::Status { vehicle } => {
            let v = select(&all, vehicle.as_deref())?;
            let state = client.state(&v.id).await?;
            print_state(&state, SystemTime::now());
            let path = snapshot_db()?;
            vehicle_storage::Store::open(&path)?.save_snapshot(&state)?;
            println!("\nSnapshot saved to {}", path.display());
        }
    }
    Ok(())
}

fn snapshot_db() -> Result<PathBuf> {
    let dir = dirs::data_local_dir()
        .ok_or("no local data directory on this platform")?
        .join("rustbenz");
    std::fs::create_dir_all(&dir)?;
    Ok(dir.join("snapshots.sqlite"))
}

fn print_state(s: &VehicleState, now: SystemTime) {
    println!("Vehicle {}", s.vehicle_id);
    row(
        "Battery",
        s.battery.state_of_charge.as_ref(),
        |p| format!("{:.0} %", p.get()),
        now,
    );
    row(
        "Electric range",
        s.battery.electric_range.as_ref(),
        |k| format!("{:.0} km", k.get()),
        now,
    );
    row(
        "Charging",
        s.charging.status.as_ref(),
        |c| format!("{c:?}"),
        now,
    );
    row(
        "Plugged in",
        s.charging.plugged_in.as_ref(),
        |b| yes_no(*b),
        now,
    );
    row(
        "Charging power",
        s.charging.power_kw.as_ref(),
        |kw| format!("{kw:.1} kW"),
        now,
    );
    row(
        "Preconditioning",
        s.climate.preconditioning_active.as_ref(),
        |b| yes_no(*b),
        now,
    );
}

fn row<T>(label: &str, r: Option<&Reading<T>>, value: impl Fn(&T) -> String, now: SystemTime) {
    match r {
        None => println!("  {label:<16} unavailable"),
        Some(r) => println!(
            "  {label:<16} {:<10} ({})",
            value(&r.value),
            age(r.observed_at, now)
        ),
    }
}

fn yes_no(b: bool) -> String {
    if b { "yes" } else { "no" }.to_owned()
}

/// Vehicle-side age of a reading, so stale data is never shown as live.
fn age(observed_at: Option<SystemTime>, now: SystemTime) -> String {
    let Some(t) = observed_at else {
        return "time unknown".to_owned();
    };
    let secs = now.duration_since(t).map(|d| d.as_secs()).unwrap_or(0);
    match secs {
        0..60 => "just now".to_owned(),
        60..3600 => format!("{} min ago", secs / 60),
        3600..86_400 => format!("{} h ago", secs / 3600),
        _ => format!("{} days ago", secs / 86_400),
    }
}

fn select<'a>(vehicles: &'a [Vehicle], suffix: Option<&str>) -> Result<&'a Vehicle> {
    let matches: Vec<_> = vehicles
        .iter()
        .filter(|v| {
            suffix.is_none_or(|s| v.id.as_str().to_uppercase().ends_with(&s.to_uppercase()))
        })
        .collect();
    match matches.as_slice() {
        [one] => Ok(one),
        [] => Err("no matching vehicle; see `eqa vehicles list`".into()),
        _ => Err("several vehicles match; pass --vehicle <last VIN characters>".into()),
    }
}

#[cfg(test)]
mod tests {
    use vehicle_core::VehicleId;

    use super::*;

    fn v(id: &str) -> Vehicle {
        Vehicle {
            id: VehicleId::new(id),
            model: None,
        }
    }

    #[test]
    fn age_is_relative_to_vehicle_timestamp() {
        let now = SystemTime::UNIX_EPOCH + Duration::from_secs(1_000_000);
        let ago = |s| Some(now - Duration::from_secs(s));
        assert_eq!(age(None, now), "time unknown");
        assert_eq!(age(ago(5), now), "just now");
        assert_eq!(age(ago(600), now), "10 min ago");
        assert_eq!(age(ago(7200), now), "2 h ago");
        assert_eq!(age(ago(3 * 86_400), now), "3 days ago");
        assert_eq!(
            age(Some(now + Duration::from_secs(30)), now),
            "just now",
            "clock skew"
        );
    }

    #[test]
    fn select_by_suffix_or_single_vehicle() {
        let one = [v("AAA111")];
        assert!(select(&one, None).is_ok());

        let two = [v("AAA111"), v("BBB222")];
        assert!(select(&two, None).is_err(), "ambiguous without suffix");
        assert_eq!(
            select(&two, Some("b222")).map(|x| x.id.as_str()).ok(),
            Some("BBB222")
        );
        assert!(select(&two, Some("999")).is_err());
        assert!(select(&[], None).is_err());
    }
}
