# Puente-Multi: Offline Any-to-Any Speech-to-Speech Translation Across 12 Languages

## Gemma 4 Good Hackathon Submission | Digital Equity + Global Communication

---

## Summary

Puente-Multi is a fully on-device, real-time speech-to-speech translator spanning **twelve languages** that together cover approximately four billion speakers — roughly half of humanity. It runs natively on a Google Pixel 10 Pro using **Gemma 4 E2B/E4B** for multimodal speech recognition and translation via **LiteRT-LM** with GPU acceleration on the Tensor G5 NPU. Text-to-speech output uses Android's native TTS engine with locale-specific voices and graceful text-only fallback when a voice isn't installed.

**No data leaves the device.** All speech processing, transcription, translation, and audio synthesis occur entirely on the phone. No audio is recorded, stored, or transmitted. The app has no `INTERNET` permission declared in its manifest — it cannot reach the network even if compromised. Airplane mode works identically to full connectivity.

Puente-Multi is a generalization of the earlier EN↔ES translator into a twelve-way any-to-any tool. It is built for anyone who needs to communicate across the world's major language barriers — in disaster zones, at field clinics, across border crossings, or simply between travelers and locals — in places where internet is unavailable, where privacy is non-negotiable, or where professional interpretation is out of reach.

---

## Supported Languages

| Language | Native Name | Code | Script | Approx. Speakers |
|---|---|---|---|---|
| English | English | `en` | Latin | ~1.5B |
| Mandarin Chinese | 中文 | `zh` | Han | ~1.1B |
| Hindi | हिन्दी | `hi` | Devanagari | ~600M |
| Spanish | Español | `es` | Latin | ~590M |
| Arabic | العربية | `ar` | Arabic | ~420M |
| French | Français | `fr` | Latin | ~310M |
| Portuguese | Português | `pt` | Latin | ~260M |
| Russian | Русский | `ru` | Cyrillic | ~260M |
| German | Deutsch | `de` | Latin | ~135M |
| Japanese | 日本語 | `ja` | Kana + Kanji | ~125M |
| Korean | 한국어 | `ko` | Hangul | ~80M |
| Italian | Italiano | `it` | Latin | ~65M |

The language set was chosen at the intersection of three criteria: (1) strong multilingual coverage in Gemma 4 E2B/E4B training data, (2) availability as a first-party TTS voice on stock Android / Pixel, and (3) aggregate speaker count globally. Any pairing of the twelve works — the user selects two dropdowns and the app auto-routes between them.

---

## The Problem

Roughly 7,000 languages are spoken worldwide, but twelve of them cover a majority of human communication. The remaining barrier is not lack of *coverage* from translation services — Google Translate, DeepL, Apple Translate, etc. all handle these languages. The barrier is that **every existing translator requires sending your speech to somebody else's server.**

That creates three compounding problems:

1. **No internet, no translation.** Rural communities, disaster response teams, correctional and medical facilities with restricted networks, developing regions with unreliable infrastructure, airplane cabins, underground transit, remote worksites — all lose access the moment connectivity drops. The people who most need translation in emergencies are often in exactly those settings.

2. **Privacy by default is architecturally impossible.** Cloud services transmit spoken words — medical histories, legal statements, personal disclosures, trade secrets — to third-party servers whose handling you must take on faith. "We don't store your audio" is a policy, not a property. There's no way to audit it.

3. **Cost barriers compound with every use.** Professional interpretation services charge per-minute fees. Cloud translation APIs charge per-character or per-request. The communities who most need translation — refugees, rural LEP patients, underfunded social services — are systematically the ones least able to afford these recurring costs.

Puente-Multi eliminates all three at once by running the entire pipeline — microphone → transcription → translation → speaker — on a single phone, offline, with no marginal cost per conversation and no server to breach.

---

## Use Cases

The bilingual predecessor (Puente-Bridge) was framed around US healthcare and the EN↔ES language access gap. Puente-Multi's twelve-way coverage extends the use cases globally:

### Humanitarian and Disaster Response
- Field medics and humanitarian workers communicating with people in crisis zones where infrastructure is down
- Border crossing points and refugee reception centers, where multi-language demand is bursty and unpredictable
- Search-and-rescue teams working across language lines

### Travel and Migration
- Travelers in regions where their phone has no roaming or data
- Recent immigrants navigating institutional settings (schools, hospitals, courts) in their new country
- Diplomatic and consular staff conducting initial interviews

### Professional Field Work
- Construction, agriculture, and logistics crews with mixed-language workers and no connectivity on-site
- Field researchers conducting interviews in remote areas
- Oil/gas, mining, and utility technicians coordinating across language lines offshore or underground

### Healthcare (Any Country, Any Pair)
- Clinicians working with patients who speak one of the twelve languages, in settings where an interpreter is not immediately available
- Community health workers doing home visits in multilingual communities
- International health organizations deploying in regions with limited infrastructure

### Education and Social Services
- Teachers communicating with parents across language lines at conferences or in emergencies
- Social workers conducting home visits in multilingual neighborhoods
- Court interpreters as a fallback when a qualified human interpreter is unavailable (with appropriate disclaimers)

### Any Two-Language Conversation, Anywhere
- Two people with no shared language trying to have a real-time conversation
- Classrooms, workplaces, homes, public spaces — any setting where the language barrier is the friction and cloud translation is unavailable or untrusted

---

## Technical Architecture

### Per-Turn Pipeline

```
Microphone
   │  16 kHz / 16-bit / mono PCM via AudioRecord (VOICE_RECOGNITION source)
   │  Hardware NoiseSuppressor + AcousticEchoCanceler attached
   │
   ▼
WAV encoding (44-byte RIFF header)
   │
   ▼
Gemma 4 E2B / E4B via LiteRT-LM  (Conversation #1, drained after use)
   │  Prompt: "The speaker is using either <A> or <B>. Transcribe in original language."
   │  Output: raw transcription text
   │
   ▼
Language.detectFromText(text, A, B)   ← pure Kotlin, no model call
   │  Scores each candidate by script range, diacritics, stopwords.
   │  Returns whichever of A/B scored higher.
   │
   ▼
Gemma 4 E2B / E4B via LiteRT-LM  (Conversation #2, drained after use)
   │  Prompt: "Translate into <target>. Respond in <target> ONLY."
   │  Output: translation in target language
   │
   ▼
Android TextToSpeech
   │  Locale-specific voice (Locale.JAPANESE, Locale("ar"), etc.)
   │  Falls back to text-only display if no voice installed
   │
   ▼
Speaker
```

### Components

| Component | Technology | Role |
|---|---|---|
| **ASR + Translation** | Gemma 4 E2B (2.6 GB) or E4B (3.7 GB) via LiteRT-LM 0.10.0 | On-device multimodal speech understanding and translation |
| **Hardware acceleration** | Tensor G5 GPU via `Backend.GPU()` | Low-latency inference on Pixel 10 Pro |
| **Audio encoder** | LiteRT-LM's `miniaudio` on CPU via `Backend.CPU()` | Required by E2B/E4B audio encoder architecture |
| **Audio capture** | Android `AudioRecord` + `NoiseSuppressor` + `AcousticEchoCanceler` | 16 kHz/16-bit/mono PCM with hardware noise/echo reduction |
| **Text-to-speech** | Android `TextToSpeech` with per-locale `Locale` | Native voices: English, Spanish, French, German, Portuguese, Italian, Chinese, Japanese, Korean, Hindi, Arabic, Russian |
| **Language detection** | Kotlin-side heuristic in `Language.kt` | Script-range + diacritic + stopword scoring — no model call required |
| **UI** | Jetpack Compose + Material 3 | Two-dropdown language picker + push-to-talk + conversation history |
| **Storage** | Shared `/sdcard/Download/litertlm-models/` with `MANAGE_EXTERNAL_STORAGE` | Single multi-GB model file serves both bilingual and multilingual apps |

### Key Design Decisions

**The model doesn't identify languages — Kotlin does.** Early prototypes asked Gemma to both transcribe AND report a `LANG:` tag. Under acoustic ambiguity, Gemma's LANG tag defaulted to `en` even when it correctly transcribed Spanish phonemes into Spanish text — we saw logs like `LANG: en` sitting above `TEXT: Hola, ¿cómo estás?`. So we pulled language identification out of the model entirely. Detection is now a deterministic Kotlin scoring function over:
- **Script range** (10 points per char) for the six non-Latin languages. Han characters → Chinese, Hangul → Korean, Hiragana/Katakana → Japanese, Devanagari → Hindi, Arabic block → Arabic, Cyrillic → Russian. These are trivially unambiguous.
- **Language-specific diacritics** (4 points per char) for the Latin-script group. `ñ/¿/¡` for Spanish, `ß/ä/ö/ü` for German, `ã/õ/ç` for Portuguese, `ç/œ/àâêî` for French, `àèéìòù` for Italian.
- **Stopwords** (2 points per match). `el/la/los` for Spanish, `der/die/das` for German, `le/la/les` for French, `o/a/os/as` for Portuguese, `il/lo/gli` for Italian, plain English function words for English.

The candidate pair (the two dropdown selections) is scored and the higher-scoring language wins. Because the comparison is always binary (A vs B, not twelve-way), the heuristic only has to distinguish the two specific languages the user actually configured — a much easier problem than open-set identification.

**Audio encoder is anchored via the prompt.** Gemma is told in the transcribe prompt that the audio is one of the two selected languages. This pre-biases the audio encoder toward the right phoneme set and noticeably improves transcription quality for non-English audio — raw "transcribe this" prompts tend to force foreign phonemes into English-shaped tokens (`"muy bien"` → `"we're being"`) under the model's English training bias.

**Transcribe and translate are in separate Conversations.** Keeping both in one Conversation left the audio-token KV cache resident during translate and OOM-killed E4B after three or four turns. Splitting them with a native-finalizer drain between (150 ms of `runFinalization()` + `gc()` + delay) lets each step reclaim memory before the next allocates.

**Model switching drains finalizers before re-allocating.** On Pixel 10 Pro, switching from E4B back to E2B after inference requires three explicit GC/finalization passes (~600 ms total) to reliably release native GPU memory before the new engine's constructor runs. Without it, the new engine stomps the old one's still-resident buffers and produces `<unused48>`-token garbage or hard native crashes.

**Target auto-flips based on source.** Per turn, if the detected source is language A, the target is B — and vice versa. The user never needs to change the dropdowns between turns to have a back-and-forth conversation. `source == target` is impossible by construction.

**Translation prompt has a hard language-lock.** Gemma 4 (especially E2B) has a documented tendency to drop foreign-language tokens into otherwise-correct output (we observed Russian `иначе` appearing mid-Spanish output, Hindi Devanagari mid-English). The translate prompt explicitly states "respond in `<target>` ONLY; every word must be a valid `<target>` word; do not mix in words, characters, or scripts from any other language" and includes a one-shot example demonstrating the expected purity.

**Shared model directory across app variants.** The bilingual (Puente-Bridge) and multilingual (Puente-Multi) apps are separate `applicationId`s but share a single copy of the multi-GB Gemma weights stored at `/sdcard/Download/litertlm-models/`. This required `MANAGE_EXTERNAL_STORAGE` permission and a first-run "grant access" screen — because Android's sandbox prevents app A from reading app B's `Android/data/` directory even with that permission, so the models have to live outside any app-private sandbox.

---

## Gemma 4 Model Usage

Puente-Multi leverages three distinct capabilities of the Gemma 4 architecture:

1. **Multimodal audio understanding.** The model processes raw 16 kHz PCM audio directly via `Content.AudioBytes`, eliminating the need for a separate ASR pipeline. One model handles both speech recognition and translation across all twelve languages.

2. **Broad multilingual coverage.** All twelve languages in the supported set are well-represented in Gemma 4's training data — quality drops significantly for lower-resource languages but remains high across our chosen set.

3. **Conversation API with independent contexts.** Each turn uses two fresh Conversations with independent KV caches. This both enforces memory hygiene (the audio-token context doesn't bleed into the translate step) and keeps each sub-task's attention focused on its specific job.

The app supports both **E2B** (optimized for mobile, ~2.6 GB, faster) and **E4B** (higher quality, ~3.7 GB) model variants, selectable at runtime without restarting the app. E4B delivers noticeably better translation fidelity for harder pairs (e.g., Japanese ↔ Arabic) at the cost of ~1.5x latency and ~40% more memory.

---

## Privacy Architecture

| Property | Implementation |
|---|---|
| Data transmission | None. `INTERNET` permission not declared in the manifest. |
| Audio storage | Never persisted. Exists only in volatile memory during capture. |
| Transcription/translation storage | In-memory only. Cleared on app close or "Clear" button. |
| Cloud dependencies | None. All inference runs on-device. |
| Third-party services | None. No analytics, telemetry, or external APIs. |
| Network requirement | None. Airplane mode works identically. |
| Model acquisition | User pushes `.litertlm` weights to `/sdcard/Download/litertlm-models/` via adb. No in-app download. |

This is not "privacy by policy" — it is **privacy by architecture**. There is no server to breach, no API key to leak, no data retention window to read. The conversation exists only in the room where it happens.

---

## Impact Potential

### Scale

The twelve supported languages together cover the first language or working language of approximately **four billion people** — over half of humanity. Any conversation between any two speakers of these languages is translatable on a single phone, offline, at zero marginal cost.

### Who This Helps

- **Humanitarian workers and disaster responders** in regions where infrastructure is down and translation is life-critical
- **Clinicians and community health workers** serving LEP patients in any country, in any setting where a qualified interpreter isn't immediately available
- **Teachers, social workers, and school staff** communicating with families across language lines
- **Refugees and migrants** navigating institutional systems in their new country
- **Travelers and their hosts** in regions where cloud translation is blocked, monitored, or unavailable
- **Mixed-language crews** in construction, agriculture, logistics, and field operations
- **Journalists, researchers, and interviewers** conducting conversations where cloud transmission of the audio would be inappropriate or dangerous
- **Anyone who simply doesn't want their conversation uploaded to somebody else's server**

### What Makes On-Device Different at Scale

Cloud translation services handle these languages already — that's not the gap. What they cannot do:

- Work in a disaster zone with no connectivity
- Work on an airplane, in a basement, in a rural area with no cell signal, in a correctional facility, in a secure government building
- Guarantee that a sensitive conversation is not transmitted to any third party
- Operate at zero marginal cost per conversation
- Function in a country whose government blocks or monitors cloud translation services
- Operate under data sovereignty requirements that prohibit sending audio to overseas servers

Puente-Multi does all of these because the entire pipeline — from microphone to speaker — runs on a single phone, with weights loaded from local storage and inference running on the Tensor G5 NPU.

---

## Limitations and Responsible Use

Puente-Multi is an AI translation tool. It will make mistakes.

- **Translation quality varies by pair.** English ↔ Spanish / French / German / Portuguese / Italian is generally very strong; pairs involving Japanese, Korean, Chinese, Arabic, or Hindi are typically good but weaker, and quality drops further for non-English pairs between lower-resource languages (e.g., Korean ↔ Arabic).
- **Regional dialects, slang, and technical vocabulary may not translate accurately.** The model is trained on standardized language data; heavily dialectal speech is noisier.
- **TTS pronunciation uses the device's installed voice.** Accents and prosody will not capture all regional variations. On devices without a particular language's voice installed, output falls back to on-screen text only.
- **High-stakes use cases still need human interpretation.** Medical consent, legal proceedings, surgical instructions, safety-critical communication — this tool is not a substitute for a qualified human interpreter in those contexts.
- **The two-language-pair constraint is by design.** If a speaker uses a third language (say, French when the pair is set to English/Spanish), the app will incorrectly route the turn. This is a deliberate tradeoff: a constrained binary choice is vastly more reliable than open-set language identification at this model scale.

The app architecture supports future extensions — the `Language` enum is the single source of truth for the supported set, and adding a language is a matter of adding its metadata (display name, native name, locale, stopwords, diacritic set) plus pushing a Gemma 4 model that covers it. No changes to the translation or TTS pipelines are required.

---

## Technology Stack

- **Language**: Kotlin 2.3.0
- **UI framework**: Jetpack Compose + Material 3
- **ML runtime**: LiteRT-LM 0.10.0 (Google AI Edge)
- **Model**: Gemma 4 E2B / E4B (`.litertlm` format)
- **TTS**: Android `TextToSpeech` API
- **Audio**: Android `AudioRecord` (16 kHz / 16-bit / mono PCM) with hardware `NoiseSuppressor` + `AcousticEchoCanceler`
- **Build**: Gradle 8.11.1, AGP 8.7.3, JVM 17
- **Target**: Android API 26+, optimized for Pixel 10 Pro (Tensor G5)
- **Min hardware**: Any arm64 Android device with ~4 GB free external storage and a compatible GPU driver

The codebase comprises two Gradle modules: `:app` (bilingual EN↔ES, the original Puente-Bridge) and `:app-multi` (this twelve-language generalization). Both can be installed side-by-side on one device; they share the same `.litertlm` model files via a shared external-storage directory to avoid duplicating multi-GB weights.

---

## Why "Puente-Multi"

*Puente* is Spanish for "bridge" — the name of the original bilingual predecessor. This version extends that bridge across twelve languages. It is a single small bridge spanning most of humanity's linguistic distance, running on a device that fits in a pocket, with no cloud underneath it.

---

## References

1. Ethnologue (2024), *Languages of the World* — speaker counts and language family data.
2. Google AI Edge, *LiteRT-LM documentation*, version 0.10.0.
3. Google, *Gemma 4 Technical Report*, 2026.
4. U.S. Census Bureau, 2021 American Community Survey, *Languages Spoken at Home*.
5. Pew Research Center, *Language Diversity and the Internet*, 2024.
6. Android Developers, *TextToSpeech* and `MANAGE_EXTERNAL_STORAGE` permission documentation.
7. 45 CFR Part 92, *Nondiscrimination in Health Programs and Activities* (context for the healthcare use case, from the bilingual predecessor's writeup).

---

## Repository

**Source Code**: [github.com/your-repo/pixel-ai-translator](https://github.com/your-repo/pixel-ai-translator)

Both `:app` (bilingual) and `:app-multi` (twelve-language) modules in the same repository, sharing build infrastructure and model storage.

**License**: Apache 2.0

---

*Built with Gemma 4 on Pixel 10 Pro. Twelve languages. Four billion speakers. No cloud. No recording. No barriers.*
