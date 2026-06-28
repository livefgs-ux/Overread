# Release Candidate Status

## Overall Status: GO 🟢

**OverRead MVP Phase 8B is complete and ready for closed testing.** The core translation overlay loop is robust and highly respectful of user privacy.

## Implemented Phases
- **Phases 1-4**: Basic UI, floating window, local data store, text normalization.
- **Phase 5**: MediaProjection integrated without continuous capture.
- **Phase 6 & 7**: On-device OCR, Text Merging, ML Kit Translation, Overlay rendering.
- **Phase 8**: Hardening, Privacy Review, Play Store Documentation.

## Permissions Audit
- `SYSTEM_ALERT_WINDOW`: Validated. Used for floating button and result overlay.
- `FOREGROUND_SERVICE`: Validated.
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Validated. Triggered only upon user interaction.
- `POST_NOTIFICATIONS`: Validated. Notifies the user of the active background service.
- `INTERNET`: Validated. Specifically restricted to downloading language models.
- **CRITICAL RESULT**: No AccessibilityService, no invasive storage/camera/location/contacts permissions.

## Privacy Guarantees
1. No screenshots are saved to disk.
2. No OCR text or translated data leaves the device.
3. No analytics platforms or backend servers are integrated.
4. Continuous capture is disabled; it is strictly a one-shot process.

## Known Risks
- Depending on the OEM UI/Hardware, MediaProjection tokens might be invalidated more aggressively on Android 14/15, prompting the user for capture rights more often.
- Extremely large text or poor contrast backgrounds might lead to subpar translation quality due to local ML Kit OCR limits.

## Next Recommended Actions
- Perform the Manual Test Plan (`manual_test_plan.md`) across at least two major Android system versions (e.g., 12 and 14).
- Execute closed beta testing with designated testers.

## Play Console Preparation (Phase 9C)
- **Ready for signing**: Yes (signing template and guide created)
- **Ready for AAB build**: Yes (configured for `./gradlew bundleRelease`)
- **Ready for Play Console setup**: Yes

## Remaining Publishing Blockers
- Real Keystore creation locally.
- Finalizing Privacy Policy URL hosting.
- Generating final Store Screenshots.
- Google Play Console Account creation & fee payment.
- Submitting the Data Safety form.
- Adding closed testers.
