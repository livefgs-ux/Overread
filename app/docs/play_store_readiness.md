# Play Store Readiness Checklist

## App Purpose
OverRead is an on-device overlay translation tool designed for reading comics, webtoons, and apps that don't support native text selection. Users tap a floating button to capture the screen, run on-device OCR, and view translations overlaid on the original text.

## Permissions Used and Justification
- `SYSTEM_ALERT_WINDOW` (Display over other apps): Required to show the floating action button and the translated text boxes over other applications.
- `FOREGROUND_SERVICE`: Required to keep the overlay and capture mechanism active while the user navigates other apps.
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Required to capture the screen content for OCR when the user requests a translation.
- `POST_NOTIFICATIONS`: Required to show the foreground service notification, informing the user that the overlay is active.
- `INTERNET`: Required exclusively for downloading official ML Kit language models from Google's servers.

## Data Processing
- **Screen frames**: Processed in memory only, immediately discarded after OCR. Not saved or uploaded.
- **OCR text**: Processed on-device, kept temporarily in memory for translation and rendering. Not uploaded.
- **Translated text**: Processed on-device, kept temporarily in memory for rendering. Not uploaded.
- **Language models**: Downloaded from official ML Kit/Google infrastructure.

## Data Not Collected
- No account data.
- No location.
- No contacts.
- No photos/files from user storage.
- No microphone.
- No camera.
- No analytics.
- No backend storage.
- No persistent reading history.

## Data Safety Draft
- Data Collection: The app does not collect or share user data.
- Screen contents are processed locally on the user's device and never leave the device.
- Internet access is used strictly for fetching required language models for offline translation.

## Package / App ID Note
**STATUS**: The `applicationId` has been finalized to `com.aistudio.overread.bzvz` in `app/build.gradle.kts` before the first Play Store release.

## Known Limitations
- One-shot capture only (no continuous or auto-translation loop yet).
- OCR quality depends heavily on font, background contrast, and resolution.
- First translation for a new target language may require a model download, which can delay the result.
- Overlay positioning may vary depending on device screen density and fullscreen/immersive mode layouts.
- No advanced comic bubble shape detection yet.

## Reviewer Notes
For Google Play Reviewers: OverRead uses `MediaProjection` to capture the screen only when the user explicitly taps the floating action button. The capture is a single frame used for text recognition. All text processing and translation happens entirely on-device to protect user privacy. We request `SYSTEM_ALERT_WINDOW` to display the translation boxes directly where the text was detected on the screen.
