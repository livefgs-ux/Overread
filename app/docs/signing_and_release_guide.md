# Signing and Release Guide

This guide explains how to prepare OverRead for publishing on the Google Play Store.

## Debug Build vs. Release Build
- **Debug Build**: Used during development. It is automatically signed with a debug keystore and contains debugging symbols.
- **Release Build**: Used for publishing. It must be minified, stripped of debugging symbols, and signed with a unique private upload key specifically created for this app.

## Android App Bundle (.aab)
Google Play requires new apps to be submitted as an Android App Bundle (.aab). The Play Store uses this bundle to generate and serve optimized APKs for each user's specific device configuration.

## Play App Signing
OverRead uses **Play App Signing**. This means you sign your Android App Bundle with an **upload key** before submitting it to the Play Console. Google Play then verifies this upload key and signs your APKs with the actual app signing key (which they manage and protect).

## Handling Your Upload Key & Secrets
**Crucial Rules:**
1. **Never commit your keystore (`.jks` or `.keystore`) to version control.**
2. **Never commit your passwords or aliases in `build.gradle.kts` or any versioned file.**

### Where to store secrets locally
Store your keystore credentials in a local, non-versioned file. This project is configured to read from `gradle.properties` (either in the project root or your `~/.gradle/gradle.properties`), which must not be versioned, or environment variables.

In your `gradle.properties` or `local.properties`:
```properties
OVERREAD_STORE_FILE=[KEYSTORE_PATH]
OVERREAD_STORE_PASSWORD=[STORE_PASSWORD]
OVERREAD_KEY_ALIAS=[KEY_ALIAS]
OVERREAD_KEY_PASSWORD=[KEY_PASSWORD]
```

### How to generate an Upload Key
Run the following command in your terminal to generate a new key:
```bash
keytool -genkey -v -keystore [KEYSTORE_PATH].jks -keyalg RSA -keysize 2048 -validity 10000 -alias [KEY_ALIAS]
```

## How to Build the Release AAB
1. Make sure your local properties are set with your keystore credentials.
2. Run the bundle command:
```bash
./gradlew clean assembleDebug testDebugUnitTest bundleRelease
```
3. Your bundle will be located at:
`app/build/outputs/bundle/release/app-release.aab`
