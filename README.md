# Puente-Multi

Offline speech and photo translation on Android using Gemma 4 E2B/E4B.

Puente-Multi is an on-device translation app built for the Gemma 4 Good Hackathon. It runs on a Pixel-class Android device and uses Gemma 4 Edge models through LiteRT-LM for multimodal speech and image understanding. The goal is practical digital equity: real-time translation that still works when connectivity is poor, privacy matters, or cloud translation is not appropriate.

## What It Does

- Translates speech between two selected languages in paired conversation mode.
- Supports a one-mic paired flow that auto-routes each turn to the other language.
- Supports Manual direction mode with two target-specific mics when the operator wants to lock direction per turn.
- Supports direct audio-to-translation mode for lower latency.
- Supports open-set Auto-detect mode for unknown speakers, translating into the app language.
- Translates visible text in photos such as signs, menus, labels, notes, and forms.
- Runs speech, OCR, translation routing, and TTS locally on the phone.

## Supported Languages

The app supports paired conversation across:

- English
- Spanish
- French
- German
- Portuguese
- Italian
- Chinese
- Japanese
- Korean
- Hindi
- Arabic
- Russian
- Vietnamese

Auto-detect mode uses the app's lightweight Kotlin language scorer, then Gemma translates into the selected app language.

## Why Gemma 4

Gemma 4 E2B/E4B is used because the project needs local multimodal intelligence:

- Audio input for speech transcription and direct speech translation.
- Image input for OCR over camera/gallery photos.
- On-device execution for privacy and low-connectivity settings.
- Edge model sizes that can run on a modern Pixel device.

The app supports both:

- **Gemma 4 E2B**: faster and lower memory.
- **Gemma 4 E4B**: higher quality, with extra memory safeguards.

## Architecture

```text
Microphone PCM
  -> WAV wrapper
  -> Gemma 4 via LiteRT-LM
  -> transcription and/or direct translation
  -> language routing
  -> Android TextToSpeech

Photo bytes
  -> adaptive resize for inference
  -> Gemma 4 OCR
  -> Gemma 4 text translation
  -> conversation bubble
```

Key implementation details:

- LiteRT-LM: `com.google.ai.edge.litertlm:litertlm-android:0.10.0`
- Main LLM backend: GPU
- Audio backend: CPU
- E4B vision backend: CPU to reduce GPU/native memory pressure
- Kotlin language scoring helpers for routing and sanity checks

## Privacy

The app is designed so conversation content stays on the device.

- No speech audio is uploaded.
- No photos are uploaded.
- No conversation text is uploaded.
- No conversation history is intentionally persisted during normal use.
- The app disables Android backup with `android:allowBackup="false"`.

After the Gemma model files are provisioned on-device, inference runs offline.

## Project Structure

- `app-multi/`: multilingual hackathon app.
- `app/`: earlier bilingual app.
- `WRITEUP_MULTILINGUAL.md`: longer project write-up for submission/storytelling.
- `ENGINEERING_CHALLENGES.md`: implementation notes and postmortems.

## Requirements

- Android Studio or command-line Android build tools.
- JDK 17+.
- Android SDK with compile SDK 35.
- ARM64 Android device.
- Gemma 4 E2B/E4B `.litertlm` model files.
- All-files access permission on-device so the app can read shared model files.

The debug APK has been tested against a Pixel 10 Pro-class device.

## Model Setup

Model files are not committed to this repository. Place them in the shared external storage directory used by both app variants:

```bash
adb shell mkdir -p /sdcard/Download/litertlm-models
adb push gemma-4-E2B-it.litertlm /sdcard/Download/litertlm-models/
adb push gemma-4-E4B-it.litertlm /sdcard/Download/litertlm-models/
```

Expected filenames:

```text
gemma-4-E2B-it.litertlm
gemma-4-E4B-it.litertlm
```

On first launch, grant all-files access when prompted.

## Build

Build the multilingual debug APK:

```bash
./gradlew :app-multi:assembleDebug
```

Install it:

```bash
adb install -g app-multi/build/outputs/apk/debug/app-multi-debug.apk
```

If multiple devices are connected:

```bash
adb -s <device-id> install -g app-multi/build/outputs/apk/debug/app-multi-debug.apk
```

## Usage

1. Push the Gemma model files to `/sdcard/Download/litertlm-models/`.
2. Install and open the app.
3. Grant microphone permission.
4. Grant all-files access when prompted.
5. Choose Faster or Higher Accuracy.
6. Choose two paired languages, or use Auto-detect from Settings.
7. Hold the mic, speak, then release.
8. Use Take photo or Upload photo for image text translation.

## Modes

**Paired mode**

Pick two languages. The app transcribes the speaker, detects which of the pair was spoken, and translates into the other language.

**Manual direction**

Available when Auto-detect is off. Shows two mics so the operator chooses the target language. This locks direction per turn and can improve accuracy when it is clear who is speaking.

**Direct translation**

Skips the visible transcription step and asks Gemma to translate speech directly. In paired mode, one mic is the default; Manual direction can be enabled for target-specific mics.

**Auto-detect**

For unknown-language situations. The app detects the spoken language and translates into the app language. Manual direction is disabled because any-language mode has a fixed target.

## Memory Notes

E4B is large enough that native memory management matters on Android. The app includes several safeguards:

- Lower E4B LiteRT-LM token/cache budget than E2B.
- Single-flight Gemma inference via a mutex.
- CPU vision backend for E4B.
- Adaptive photo downsampling before Gemma OCR.

These changes are documented in `ENGINEERING_CHALLENGES.md`.

## Limitations

- This is a prototype, not a certified interpreter.
- Translation may be inaccurate, especially for noisy audio, dialects, slang, technical terms, or long/complex text.
- Photo OCR quality depends on image clarity, angle, lighting, and text size.
- Some Android TTS voices may not be installed; the app falls back to text-only output.
- E4B has higher memory pressure and may be slower than E2B.
- Auto-detect is limited to the curated language scorer rather than a broad external language-ID model.

## Healthcare and Safety Note

Puente-Multi does not replace qualified human interpreters where law, policy, or clinical risk requires one. It is intended as a practical aid for low-risk communication, temporary bridging, field contexts, and situations where no interpreter is immediately available. Users should treat output as potentially imperfect and escalate to qualified human interpretation for critical communication.

## Hackathon Submission Assets

Recommended public submission package:

- This repository.
- `WRITEUP_MULTILINGUAL.md` as the longer technical/project write-up.
- Demo video showing offline speech translation, Manual direction, Auto-detect, Direct translation, and photo translation.
- APK release artifact or clear build instructions.
- Screenshots/cover image for the Kaggle project page.

## License

This project is licensed under the Apache License 2.0. See `LICENSE`.

Gemma model files are not included in this repository and are governed by their own model terms.
