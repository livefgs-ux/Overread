# Manual Test Plan

## Pre-requisites
- Device or emulator running Android 10+ (recommend testing on Android 13/14 for granular notification and media projection rules).
- Internet connection (for initial language model downloads).

## Test Cases

### 1. First Launch & Onboarding
- **Action**: Launch the app for the first time.
- **Expected**: User sees the clean onboarding/tutorial. Explanations of local processing and required permissions are clear.

### 2. Permissions Flow
- **Action**: Deny Overlay permission when asked.
- **Expected**: Graceful fallback, user is prompted again if they try to start the service.
- **Action**: Grant Overlay permission.
- **Expected**: "Start Floating Button" becomes available.
- **Action**: Deny Notifications (Android 13+).
- **Expected**: Service should still run, but without the persistent notification (or system might enforce it).
- **Action**: Tap Floating Button, then Deny "Screen Recording" system prompt.
- **Expected**: Shows error message "Screen capture was denied" (or similar), no crash.
- **Action**: Tap Floating Button, then Grant "Screen Recording" prompt.
- **Expected**: Screen is captured and processing begins.

### 3. Capture Scenarios
- **Action**: Capture over a web browser.
- **Expected**: Text is detected and translated.
- **Action**: Capture over a comic/image.
- **Expected**: Speech bubbles are detected, translated boxes appear.
- **Action**: Capture over a screen with no text.
- **Expected**: Graceful message "No text found on this screen."
- **Action**: Capture over a secure screen (FLAG_SECURE, e.g., Banking app or Netflix).
- **Expected**: System prevents capture (black screen). App should handle it gracefully or timeout with a friendly error.

### 4. Language & Translation Scenarios
- **Action**: Translate text matching the target language.
- **Expected**: Message "Source is same as target language", no boxes rendered.
- **Action**: Translate an unknown language or gibberish.
- **Expected**: ML Kit fails to identify, fallback or graceful error.
- **Action**: First time translating From English to Spanish (model not downloaded).
- **Expected**: Message "Translation model required." Prompts user to open app to download.
- **Action**: Prepare model in HomeScreen (success).
- **Expected**: Shows "Downloading..." then "Ready ✓".
- **Action**: Prepare model in HomeScreen (airplane mode).
- **Expected**: Model download fails with friendly network error message.
- **Action**: Successful translation after model is ready.
- **Expected**: Translation boxes appear on screen.

### 5. Overlay Controls
- **Action**: Open Quick Menu and select "Clear translation boxes".
- **Expected**: Only the translated boxes vanish. Floating button remains.
- **Action**: Open Quick Menu and select "Stop OverRead".
- **Expected**: Floating button, menu, translated boxes disappear. Foreground service stops. Notification disappears.
- **Action**: Rotate device while overlay is active.
- **Expected**: Floating button stays on screen. Translated boxes might misalign, but app shouldn't crash. (Subsequent capture will realign).

### 6. Edge Cases
- **Action**: Put app in background while downloading model.
- **Expected**: Download continues or fails gracefully.
- **Action**: Revoke Overlay permission from Android Settings while service is running.
- **Expected**: Service stops drawing or crashes gracefully (system kills app usually).
- **Action**: Tap floating button multiple times rapidly.
- **Expected**: Message "Processing already running", ignores extra taps.
- **Action**: Translation fails (e.g., unexpected error).
- **Expected**: Message "Translation failed", no residual broken boxes.
