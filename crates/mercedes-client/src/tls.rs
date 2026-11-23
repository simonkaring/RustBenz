//! One rustls configuration for both HTTP (reqwest) and the websocket.

use std::sync::Arc;

use rustls::ClientConfig;

pub(crate) fn client_config() -> Result<ClientConfig, rustls::Error> {
    let provider = Arc::new(rustls::crypto::aws_lc_rs::default_provider());
    let builder =
        ClientConfig::builder_with_provider(provider).with_safe_default_protocol_versions()?;

    #[cfg(target_os = "android")]
    {
        // shortcut: bundled Mozilla roots on Android, because the OS verifier needs a JNI init
        // and a Gradle component. Switch to rustls-platform-verifier to honour user/enterprise CAs.
        let roots = rustls::RootCertStore {
            roots: webpki_roots::TLS_SERVER_ROOTS.to_vec(),
        };
        Ok(builder.with_root_certificates(roots).with_no_client_auth())
    }
    #[cfg(not(target_os = "android"))]
    {
        use rustls_platform_verifier::BuilderVerifierExt;
        Ok(builder.with_platform_verifier()?.with_no_client_auth())
    }
}
