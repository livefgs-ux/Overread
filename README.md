# OverRead - Webtoon & Comic Real-Time Translator

<div align="center">

**Real-time screen translation for webtoons, comics, and any app - no screenshots needed!**

</div>

## Features

### Real-Time Live Translation
- **Automatic detection**: When you stop scrolling, OverRead automatically detects text, translates it, and displays translation bubbles over the original speech bubbles
- **Auto-hide while scrolling**: Translation bubbles disappear smoothly while you scroll and reappear when you stop
- **Smart content detection**: If a page has no text (only images), translations automatically disappear
- **No screenshots required**: Continuous screen capture with intelligent frame sampling

### Multi-Language OCR Support
OverRead supports text recognition for multiple writing systems:
- **Latin** (English, Spanish, French, Portuguese, etc.)
- **Chinese** (Simplified & Traditional)
- **Japanese** (Kanji, Hiragana, Katakana)
- **Korean** (Hangul)
- **Devanagari** (Hindi, Marathi, Sanskrit)

### Automatic Language Detection
- Detects the source language automatically - no need to manually select it
- Identifies any language that appears on screen
- Translates to your chosen target language

### Webtoon-Style Translation Bubbles
- Beautiful rounded bubble overlays that mimic comic speech bubbles
- Auto-sized text that fits perfectly within each bubble
- Semi-transparent white background with subtle borders
- Smart positioning that covers the original text naturally

### Two Translation Modes
1. **Live Mode** (NEW): Continuous real-time translation as you read
   - Start Live Reading → scroll through your webtoon → translations appear automatically
   - Perfect for reading long webtoon episodes

2. **Manual Mode**: Tap the floating button for one-shot translation
   - Tap to translate the current screen
   - Good for specific frames or when you want full control

### Privacy-First
- All processing is done **on-device** using Google ML Kit
- No text or images are uploaded to any server
- Internet is only used to download language translation models (one-time)

## How to Use

### Setup
1. Open OverRead app
2. Grant Overlay Permission (to show the floating button and translation bubbles)
3. Choose your **Target Language** (the language you want to read in)
4. Download the translation model for your language pair (one-time)

### Live Translation Mode (Recommended for Webtoons)
1. Tap **"Start Live Reading"** in the app
2. Grant screen capture permission
3. Open your webtoon/comic app
4. **Just scroll normally** - OverRead will:
   - Detect when you stop scrolling
   - Automatically recognize and translate text
   - Show translation bubbles over speech bubbles
   - Hide translations when you scroll again
   - Re-translate when new text appears

### Manual Translation Mode
1. Tap **"Prepare Screen Capture"** then **"Start Floating Button"**
2. Go to any app with text
3. Tap the floating button to translate the current screen

## Quick Setup Options

| Setting | Description |
|---------|-------------|
| Target Language | The language you want translations in (e.g., English, Portuguese, Spanish) |
| Reading Mode | Optimized layout for webtoons and comics |
| Button Size | Small / Medium / Large floating button |
| Overlay Opacity | Transparency of translation bubbles (20% - 100%) |

## Supported Languages

OverRead can translate **from** any of these languages **to** any other:

**Asian Languages:** Chinese (Simplified & Traditional), Japanese, Korean, Hindi, Vietnamese, Indonesian, Thai, Filipino, Malay

**European Languages:** English, Spanish, French, German, Italian, Portuguese, Dutch, Polish, Russian, Turkish, Czech, Hungarian, Romanian, Greek, Swedish, Danish, Norwegian, Finnish, Croatian, Slovak, Slovenian, Lithuanian, Latvian, Estonian

**Others:** Arabic, Hebrew, Urdu, Bengali, Tamil, Telugu, Marathi, Kannada, Gujarati, Punjabi, Malayalam

## Technical Details

### Architecture
- **Capture**: MediaProjection API for continuous screen capture
- **OCR**: Google ML Kit Text Recognition v2 (multi-script)
- **Language ID**: Google ML Kit Language Identification
- **Translation**: Google ML Kit On-Device Translation
- **Overlay**: System alert window with custom webtoon-style bubbles

### Performance
- Frame sampling at 600ms intervals (configurable)
- Screen stability detection (800ms) before triggering OCR
- 10-second pipeline timeout for safety
- Efficient bitmap recycling to minimize memory usage

## Requirements
- Android 7.0+ (API 24+)
- Overlay permission
- Screen capture permission (for Live Mode)
- ~50MB free space per language model

## Building from Source

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17+

### Steps
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Run on emulator or physical device

## Privacy Policy

OverRead processes all data on your device:
- Screen captures are processed in-memory and immediately discarded
- Text is recognized and translated locally using ML Kit
- No data is sent to external servers
- Internet connection is only used to download ML Kit language models

## License

This project is for personal use.

---

<div align="center">

**Read any webtoon in any language. Automatically.**

</div>
