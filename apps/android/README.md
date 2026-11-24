# Android (Kotlin + Jetpack Compose)

Read-only dashboard on top of the Rust core (`crates/vehicle-ffi`, UniFFI).

Requirements: JDK 17+, Android SDK with NDK 28.2, `cargo-ndk`, and the Rust
targets `x86_64-linux-android` and `aarch64-linux-android`.

```sh
# from apps/android, with JAVA_HOME, ANDROID_HOME and ANDROID_NDK_HOME set
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Gradle build compiles the Rust library with `cargo ndk` and generates the
Kotlin bindings into `app/build/generated/uniffi`, so no generated code is
committed.

Login opens Mercedes' own page in a Custom Tab. The app receives the
`rismycar://login-callback` redirect through an intent filter. Tokens are
stored AES-GCM-encrypted with an Android Keystore key.
