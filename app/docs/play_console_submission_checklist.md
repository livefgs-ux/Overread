# Play Console Submission Checklist

Use this checklist to ensure all assets and configurations are ready before submitting OverRead to the Google Play Console for Closed Testing or Production.

## App Configuration
- [x] **Package Name / ApplicationId**: Ensure the `applicationId` in `build.gradle.kts` is FINAL. It cannot be changed after the first release. (`com.aistudio.overread.bzvz` finalized)
- [x] **App Name**: Finalized in `strings.xml`.
- [x] **Version Code & Name**: Updated to the correct release version (`versionCode 1`, `versionName "1.0-beta1"`).
- [ ] **App Signing**: Keystore created, secured locally, and configured for release builds.

## Store Listing Assets
- [ ] **App Icon**: High-res 512x512 PNG.
- [ ] **Feature Graphic**: High-res 1024x500 PNG.
- [ ] **Screenshots**: Specific plan in `store_screenshot_plan.md` ensures no copyright or PII issues.
- [x] **Short Description**: "Translate text on your screen with a private floating overlay."
- [x] **Full Description**: Contains clear privacy notes confirming local processing, one-shot capture, and the reason for the selected permissions.

## Privacy & Policy
- [ ] **Privacy Policy URL**: Hosted link to the Privacy Policy (which must state that screen frames and OCR text are not uploaded or saved).
- [ ] **Data Safety Form**: Completed in Play Console indicating:
    - Data collected: None.
    - Data shared: None.
    - Internet usage restricted solely to ML Kit language model downloads.
    - No analytics.
    - No account data.
    - No location/contact/storage/camera/microphone data.
    - Evaluate ML Kit / Google Play Services disclosures if prompted by Play Console.

## App Content Declarations
- [ ] **Ads**: Declare if the app contains ads (Currently: NO).
- [ ] **Target Audience**: Confirm target age group (e.g., 13+ or 18+ depending on target content reading).
- [ ] **Permissions Declaration**: Justify `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, and `POST_NOTIFICATIONS`.
- [ ] **News/COVID**: Declare app is not a news or COVID-19 related app.

## Release Management
- [ ] **AAB Build Instructions**:
  - Mac/Linux: `./gradlew clean assembleDebug testDebugUnitTest bundleRelease`
  - Windows: `.\gradlew clean assembleDebug testDebugUnitTest bundleRelease`
  - Expected output: `app/build/outputs/bundle/release/app-release.aab`
- [ ] **Closed Testing Track**: Create a new release in the Closed Testing track.
- [ ] **Tester Emails**: Add the list of beta tester email addresses or enable Google Group joining.
- [ ] **Release Notes**: Add `<en-US>` release notes.
