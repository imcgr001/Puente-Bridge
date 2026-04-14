# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Modules

Two Android application modules share one Gradle build:

- **`:app`** — bilingual (EN↔ES) translator with a first-run Spanish disclaimer, auto-detected direction, and healthcare-aware positioning (package `com.pixeltranslator.app`).
- **`:app-multi`** — general-purpose translator targeting 12 languages (EN, ES, FR, DE, PT, IT, ZH, JA, KO, HI, AR, RU). Target is picked from a dropdown and persisted in SharedPreferences; source is auto-detected in the transcribe prompt itself (response format: `LANG: xx\nTEXT: ...`). TTS availability is checked per-target; unavailable voices fall back to text-only display (package `com.pixeltranslator.multi`).

Both apps install side-by-side. They share the same LiteRT-LM setup, memory-hardening patches, and model files (see below).

## Build & Run

```bash
./gradlew :app:assembleDebug        :app-multi:assembleDebug   # build both APKs
./gradlew :app:installDebug         :app-multi:installDebug    # build + install both
./gradlew :app:compileDebugKotlin   :app-multi:compileDebugKotlin  # fast Kotlin check
./gradlew lint
```

Apps target `arm64-v8a` only, developed against a Pixel 10 Pro (Tensor G5). `minSdk=26`, `targetSdk=35`, Kotlin 2.3.0, AGP 8.7.3, JVM 17. No unit or instrumentation tests.

## Model files (shared between both apps)

Gemma 4 `.litertlm` weights are **not bundled**. Both apps read from a **shared directory on external storage** rather than their own per-app sandboxes, so a single multi-GB copy serves both:

```bash
adb shell mkdir -p /sdcard/Download/litertlm-models/
adb push gemma-4-E2B-it.litertlm /sdcard/Download/litertlm-models/
# optional: also push gemma-4-E4B-it.litertlm for the E4B toggle
```

This path is defined as `GemmaTranslatorManager.SHARED_MODEL_DIR` in both modules — keep them in sync if you change it. Android sandboxing means `/sdcard/Android/data/<other-app>/` is unreadable even with `MANAGE_EXTERNAL_STORAGE`; that's why models live under `/sdcard/Download/` instead. Both apps declare `MANAGE_EXTERNAL_STORAGE` and gate initial model loading on `Environment.isExternalStorageManager()`. On first launch each app shows a `StorageAccessScreen` that deep-links to the per-app "All files access" settings toggle; `tryLoadModels()` is re-invoked on `ON_RESUME` so returning from Settings automatically proceeds to model loading.

`GemmaTranslatorManager.initialize()` throws with the exact adb commands if the file is missing. The model paths map to `GemmaTranslatorManager.ModelSize` — update both if you rename.

The `app/src/main/assets/` `.onnx` and `.bin` files (kokoro, model_q8f16, voice bins) are **legacy from a prior Kokoro-ONNX TTS implementation** that has since been replaced by Android's built-in `TextToSpeech` (see `KokoroTTSManager` — the class kept its name but now wraps `android.speech.tts.TextToSpeech`; in `app-multi` the same wrapper is honestly named `TtsManager`). These assets are gitignored and unused at runtime.

## Architecture

Single-activity Compose app. The translation pipeline is a straight line through three managers owned by one ViewModel:

```
MainActivity (Compose UI, push-to-talk gesture)
   └── TranslatorViewModel
         ├── AudioCaptureManager    → 16kHz/16-bit/mono PCM via AudioRecord (VOICE_RECOGNITION source)
         ├── GemmaTranslatorManager → LiteRT-LM Engine running Gemma 4 E2B/E4B on Tensor G5 GPU
         └── KokoroTTSManager       → android.speech.tts.TextToSpeech (name is legacy)
```

### Things that are load-bearing and non-obvious

- **Transcribe and translate run in SEPARATE `Conversation`s.** Both modules: `translateSpeech` creates one conversation for transcription, closes it (with a native-finalizer drain), then creates a second conversation for translation. Keeping them in one conversation leaves the audio-token KV cache resident during translate, which OOM-kills E4B after a few turns. Don't merge them back.
- **Native-finalizer drain between engine/conversation teardowns.** Between model switches, `initialize()` runs three `runFinalization()` + `gc()` + `delay(200)` cycles (total ~600 ms) *after* `close()`; between turns, each conversation's `finally` block runs one cycle (~150 ms). This is not superstitious — LiteRT-LM releases GPU/native memory asynchronously via finalizers, and without the drain the next allocation stomps the still-resident previous one (symptom: `<unused48>`-token spam, session errors, or hard native crashes).
- **`MAX_TOKENS = 1536`.** This is the per-Conversation KV-cache budget. Audio tokens from a single utterance can exceed 700 on their own, so 512 was too small; pushing higher trades memory for headroom.
- **Gemma's audio encoder needs a WAV container**, not raw PCM. `wrapPcmAsWav` prepends a 44-byte RIFF header before `Content.AudioBytes`. Removing this breaks transcription silently.
- **Backend split is intentional.** `EngineConfig` uses `Backend.GPU()` for the LLM but `Backend.CPU()` for the audio encoder — the E2B audio encoder has constraints that require CPU. `libOpenCL.so` is declared as an optional native lib in the manifest for the GPU path.
- **Source-language detection differs per module.**
  - `app`: `detectLanguage()` is a heuristic over Spanish-specific characters and function words; suitable only for EN↔ES.
  - `app-multi`: the transcribe prompt instructs Gemma to emit `LANG: xx\nTEXT: ...` and the response is parsed with a regex that falls back gracefully if the format is missing. This leans on the model instead of a hand-rolled heuristic precisely because hand-rolled heuristics don't generalize to 12 languages.
- **Translate prompt has a hard language-lock + one-shot example.** Gemma 4 (especially E2B) has a known drift where it substitutes same-meaning tokens from other languages (e.g., Hindi Devanagari inside Spanish output). The prompt explicitly says "every word must be a valid $targetName word; do not mix in words, characters, or scripts from any other language," and includes a paired example. Don't weaken this.
- **Hardware AEC/NoiseSuppressor are attached to the AudioRecord session** (`AudioCaptureManager`) so TTS playback doesn't bleed into the mic during rapid back-and-forth. TTS requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` so notifications can't interrupt playback.
- **Push-to-talk uses `awaitEachGesture` + `waitForUpOrCancellation`**, not a toggling click — recording starts on press-down and ends on release/cancel. The `recordingJob` is joined in `onPushToTalkReleased` before the audio is read, so cancellation during capture is safe.
- **Disclaimer screen** (`app` only): first-run gate with a Spanish-language text (`DISCLAIMER_TEXT` in `TranslatorViewModel.kt`) that can be played aloud via TTS. Tied to the healthcare positioning in `WRITEUP.md` — if you change the disclaimer copy, update both places. `app-multi` does not have a disclaimer; it's positioned as a general-purpose translator.
- **TTS availability is checked per-target-locale in `app-multi`** (`TtsManager.isAvailable`). Targets without an installed voice fall back to text-only display with a "(text only)" hint on the conversation bubble, rather than silently producing nothing.
