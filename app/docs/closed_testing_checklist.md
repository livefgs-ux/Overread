# Closed Testing Checklist

## Environments to Test
- [ ] **Android 15**: Verify edge-to-edge UI updates and strict foreground service requirements.
- [ ] **Android 14**: Verify `MediaProjection` foreground service flow and user consent.
- [ ] **Android 13**: Verify notification permission flows.
- [ ] **Android 10-12**: Verify legacy overlay and service behaviors.

## Installation & Setup
- [ ] Clean install behaves correctly (no crashes, clean prefs).
- [ ] Onboarding tutorial appears on the first launch.

## Permission Flows
- [ ] **Overlay Permission Denied**: App gracefully informs user, floating button does not start.
- [ ] **Notification Denied (Android 13+)**: Service can still run, or app falls back gracefully depending on system rules.
- [ ] **Capture Permission Denied**: Android system prompt appears, user taps "Cancel", app shows "Screen capture was blocked by this app or the system."

## Translation Scenarios
- [ ] **Model Missing**: First run prompts user to download the translation model.
- [ ] **Model Downloaded**: Translation occurs smoothly.
- [ ] **Same Source/Target**: Detects that text is already in the target language and skips translation gracefully.
- [ ] **Webtoon / Browser / Gallery**: Successfully captures from various 3rd party apps.
- [ ] **Protected Screen (FLAG_SECURE)**: Capturing Netflix/banking app results in a black screen; app handles error gracefully without a crash.

## Lifecycle & Interaction
- [ ] **Rotation Test**: Overlay content remains stable or handles rotation gracefully.
- [ ] **Clear Boxes**: Tapping "Clear translation boxes" in the quick menu clears only translations, keeping the floating button.
- [ ] **Stop Service**: Tapping "Stop OverRead" removes everything (button, menu, translations) and stops the service.
