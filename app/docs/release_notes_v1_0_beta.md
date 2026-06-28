# OverRead Release Notes v1.0-beta1

Welcome to the OverRead Closed Beta (`versionName 1.0-beta1`)!

OverRead is a local, on-device translation overlay designed to help you read foreign language comics, manga, webtoons, and unselectable app interfaces.

## What's Included in this Beta (MVP)
- **One-Shot Local Screen Capture**: Safely capture exactly what's on your screen with a single tap of the floating button.
- **On-Device OCR & Language Detection**: Text is recognized and identified entirely offline.
- **Translation Overlay**: Translated text appears directly on top of the original elements seamlessly in your current app.
- **Official ML Kit Models**: High-quality translation models powered by Google.
- **Privacy Focus**: Screenshots are never saved to your device and your screen content is never sent to the cloud.

## Known Limitations
- Translation requires an initial language model download (around 30MB).
- Translation relies on clear text formatting; complex handwritten comic fonts or low-contrast backgrounds might yield poor OCR results.
- OverRead currently supports one-shot capture; there is no continuous "always-on" translation mode.
- Speech bubble rendering is simple bounding boxes; advanced bubble shape detection is planned for future releases.

## Privacy Notice
We respect your privacy. The Android system will prompt you for screen capture permission—this is required to grab the current frame for text recognition. This frame is processed locally and discarded instantly. OverRead does not save screenshots and only connects to the internet to download language models.

## How to Test
1. Set your target language in the OverRead app.
2. Grant Overlay and Notification permissions when prompted.
3. Tap "Start Floating Button".
4. Open any app or comic with foreign text.
5. Tap the floating button and grant capture permission.
6. Wait briefly for processing and overlay rendering.

Thank you for helping us polish OverRead!
