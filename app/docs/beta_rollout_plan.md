# Beta Rollout Plan

## Objective
The goal of this closed beta test is to validate the OverRead MVP in real-world scenarios across various devices and Android versions. We want to ensure that the core translation loop (capture -> OCR -> translate -> overlay) is stable, respects user privacy expectations, and does not crash or drain the battery excessively under normal use.

## Tester Profile
- Users who read foreign language comics, webtoons, manga, or use un-selectable UI apps.
- Users comfortable installing apps via Firebase App Distribution, direct APK, or Google Play Console Internal/Closed tracks.

## Cohort Size & Duration
- **Minimum Testers**: 10-20 active beta testers.
- **Recommended Duration**: 1 to 2 weeks.

## Target Devices & Android Versions
- **OS Focus**: Android 12, 13, 14, and 15 (if available).
- **OEM Focus**: A mix of Pixel, Samsung, Xiaomi, and other popular manufacturers to test `MediaProjection` and overlay stability across different Android skins.
- **Form Factors**: Phones and foldable/tablets.

## Targeted Test Scenarios (Apps)
Testers should evaluate OverRead over the following environments:
1. **Web Browsers**: (e.g., Chrome) translating foreign news sites or web-based comics.
2. **Webtoon/Manga Apps**: (e.g., Line Webtoon, Tachiyomi/Mihon) testing mixed image/text scenarios and speech bubbles.
3. **Gallery Apps**: Translating saved screenshots or photos.
4. **Secure Apps (FLAG_SECURE)**: Attempting to translate Netflix or Banking apps (App should gracefully fail without crashing).

## Success Criteria
- Over 90% crash-free sessions.
- Users report that the overlay permission and capture permission prompts are clear.
- Overlays render legibly on the screen.
- Models download successfully when requested.
- No reports of massive battery drain or CPU throttling.

## Failure Criteria
- Recurrent crashes during screen capture handling.
- `MediaProjection` token invalidation forcing the user to repeatedly grant permission *during a single session*.
- OOM (Out Of Memory) errors due to bitmap processing.
- The app failing to stop correctly via the "Stop OverRead" or Quick Menu.

## How to Report Bugs
Testers should use the designated Tester Feedback Form. For crashes, please provide the Android version, device model, and exactly what app was underneath OverRead when it crashed.
