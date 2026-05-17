# Puente-Bridge: Offline Any-to-Any Speech-to-Speech Translation Across 13 Languages

## Gemma 4 Good Hackathon Submission | Digital Equity + Global Communication

---

## Summary

Puente-Bridge is a fully on-device, real-time **speech and image** translator spanning **thirteen languages** that together cover approximately four billion speakers — roughly half of humanity. It runs natively on a Google Pixel 10 Pro using **Gemma 4 E2B/E4B** for multimodal speech *and vision* via **LiteRT-LM** with GPU acceleration on the Tensor G5 NPU. Voice translation and photo translation both go through Gemma: photos use Gemma OCR first, then a Gemma text-translation pass for the OCR output. Text-to-speech output uses Android's native TTS engine with locale-specific voices and graceful text-only fallback when a voice isn't installed.

**No data leaves the device.** All speech processing, transcription, OCR, translation, and audio synthesis occur entirely on the phone. No audio, images, or text are recorded, stored, or transmitted. After the Gemma model files are provisioned on-device, airplane mode is fully supported.

Puente-Bridge offers two operating modes that together address the breadth of real-world translation scenarios:

- **Paired mode** for the common case — two known speakers having a back-and-forth conversation in two pre-selected languages. Optimized for fluency, with anchored prompting and binary detection.
- **Auto-detect mode** for the high-stakes case — a single operator who doesn't know in advance which supported language the other person will speak. Gemma transcribes without a language hint, a Kotlin scorer routes the result across the curated language set, and the target is the operator's app language. Designed for disaster response intake, refugee reception, hospital triage, and other "process people of unknown origin" scenarios.

Puente-Bridge is built for anyone who needs to communicate across the world's major language barriers — in disaster zones, at field clinics, across border crossings, or simply between travelers and locals — in places where internet is unavailable, where privacy is non-negotiable, or where professional interpretation is out of reach.

---

## Origin

This project began from the developer's experience working in healthcare in California's Central Valley — a region with one of the most linguistically diverse Limited English Proficient (LEP) populations in the United States. The Central Valley's agricultural economy has drawn generations of Spanish-speaking workers from Mexico and Central America; subsequent decades brought Hmong refugee communities (particularly in Fresno, Merced, and Sacramento), Punjabi and Sikh families (Yuba City, Stockton, Fresno), Vietnamese, Filipino, Arabic-speaking, and growing populations from across the Pacific Rim and South Asia. A primary care clinic in Fresno or Stockton might see patients in eight or ten different first languages over a single day's schedule.

The interpreter-access reality in those settings is starkly different from how it's portrayed in policy documents:

- **Phone interpreter wait times of 15–45 minutes** are routine, sometimes longer for low-frequency languages. A patient in pain or a parent of a sick child waits while the clinician moves to the next room, then returns when the interpreter is finally on the line — disrupting both encounters.
- **Per-minute interpretation costs of $1.50–$3.00+** add up fast. Many small clinics, federally qualified health centers (FQHCs), and rural providers can't sustain full-time interpreter staff or unlimited per-minute contracts. Cost-conscious providers ration interpreter use, often inadvertently.
- **Video Remote Interpretation (VRI) requires reliable broadband and dedicated equipment** — neither of which is universal in the Valley's smaller and more rural facilities.
- **In-person interpreters are the gold standard but the most expensive and least available** — often impossible for off-hours, emergencies, or low-frequency languages.

The result is a documented, widespread pattern: LEP patients wait longer, get less time with their providers, are more likely to be discharged with poorly-understood instructions, and experience worse health outcomes than English-proficient patients with otherwise comparable presentations.

That gap — between what the regulations require ("meaningful access") and what frontline staff can actually provide on a given Tuesday afternoon — is the project's motivating problem. Puente-Bridge is not designed to *replace* qualified human interpreters in any setting where they're appropriate; it's designed to fill the unavoidable gaps where human interpretation is unavailable, delayed, or cost-prohibitive.

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
| Vietnamese | Tiếng Việt | `vi` | Latin | ~85M |
| Italian | Italiano | `it` | Latin | ~65M |

The language set was chosen at the intersection of three criteria: (1) strong multilingual coverage in Gemma 4 E2B/E4B training data, (2) availability as a first-party TTS voice on stock Android / Pixel, and (3) aggregate speaker count globally. Any pairing of the thirteen works — the user selects two dropdowns and the app auto-routes between them. Auto-detect mode generalizes this further: any spoken language from the supported set can be identified and translated into the selected app language without pre-selection.

---

## The Problem

Roughly 7,000 languages are spoken worldwide, but a small set of major languages covers a large share of day-to-day global communication. Offline translation already exists in consumer products, including Google Translate and Apple Translate. Puente-Bridge is not trying to re-prove that offline translation is useful; it explores what becomes possible when Gemma 4 E2B/E4B runs locally as the core engine for speech understanding, photo OCR, and translation.

That creates three design goals:

1. **LLM-centered translation.** Gemma handles the core multimodal work: transcribing speech, reading text in images, and translating extracted or transcribed text. The prompts can explicitly anchor the expected language pair, constrain output language, and separate OCR from translation instead of treating each task as a fixed black-box feature.

2. **Field-oriented control.** The app is shaped around real translation workflows: paired conversation when both languages are known, auto-detect when the operator does not know the speaker's language in advance, manual direction when the operator wants to lock the target language, replayable TTS turns, and photo translation for signs, labels, forms, and menus.

3. **Inspectable local behavior.** The implementation exposes the prompts, routing heuristics, model-size tradeoffs, memory constraints, and failure modes. That makes the project useful as a Gemma 4 Good prototype: it is not only a translation app, but a concrete test of how far on-device Gemma can carry a practical multimodal translation workflow.

Gemma 4's larger context windows are relevant here, but Puente-Bridge uses them conservatively. Each turn is processed in short, independent conversations to keep E4B stable on-device; the app does not yet maintain a long rolling conversational memory across turns. The expectation is that even within a single turn, Gemma's context capacity and instruction-following can improve transcription, OCR cleanup, and language-locked translation compared with narrower task-specific pipelines. Extending that to true multi-turn context is future work, with memory tradeoffs on mobile hardware.

---

## Two Modes for Two Patterns of Use

Roughly 99% of translation needs occur between two speakers who already know which two languages they're using — a clinic, a parent-teacher conference, a tourist asking for directions. The remaining ~1% are scenarios where the responding party doesn't know in advance what language the other person will speak — and that 1% is heavily concentrated in the highest-stakes contexts (disaster response, refugee intake, hospital triage, border crossings, aid work in linguistically diverse regions).

Puente-Bridge addresses both via two operating modes that share a single app, model, and conversation history.

### Paired mode (default)

Two language dropdowns at the bottom of the screen. The operator picks the conversation pair once. Each turn, the app:

1. Tells Gemma in the transcribe prompt: *"The speaker is using either A or B."* This anchors the audio encoder to the two relevant phoneme sets and avoids the well-documented "default to English under ambiguity" bias of multilingual ASR.
2. Scores the resulting transcription against just A and B using a deterministic Kotlin scorer (script range, diacritics, ~100-word stopword lists per Latin-script language sourced from frequency rankings).
3. Routes the translation into whichever of A or B is *not* the detected source. Source ≠ target by construction.

This is the production-grade path: anchoring catches "muy bien" as Spanish rather than letting it drift to "we're being," the binary detector is essentially infallible for distinguishable pairs, and the conversation flows back-and-forth without configuration changes.

### Auto-detect mode (toggle, lower-left)

A small Switch labeled "Auto-detect language" disables the language pair and re-routes the per-turn flow:

1. Gemma transcribes with **no** language hint — just *"Transcribe exactly what was said, in its original language. Use that language's native script."*
2. The transcribed text is scored by the same deterministic Kotlin language scorer used elsewhere in the app.
3. If confidence is high, the detected language is parked in the Language B dropdown so toggling auto-detect off yields a ready-to-go conversation pair.
4. Translation target is the selected app language. The operator gets a usable result without changing the conversation pair first.

Empirical testing during development confirmed the full pipeline works end-to-end across representative supported languages including Spanish, French, Russian (Cyrillic), Mandarin Chinese (Han), Arabic, Japanese, Korean, and Vietnamese. Non-Latin scripts are detected primarily by script range; Latin-script languages use diacritics and stopword scoring to separate English, Spanish, French, German, Portuguese, Italian, and Vietnamese.

### Architectural split: paired anchoring vs broad scoring

The two modes use genuinely different identification strategies, tuned to their respective problems:

- **Paired mode** is a *closed-set classification problem* — given audio, is it A or B? The deterministic Kotlin scorer in `Language.scoreCandidate` is the right tool: fast, auditable, zero external dependencies, and only has to tell two specific things apart. No model-based identification is needed when the answer space is constrained to two user-selected choices.
- **Auto-detect mode** is a broader classification problem across the curated set. The scorer is less powerful than a dedicated external language-ID model, but it avoids extra native libraries and keeps the app fully Gemma-centered.

The same auditable language-scoring layer now owns routing in both modes, while Gemma owns transcription, OCR, and translation.

### Why both modes

Auto-detect is more flexible but loses the safety nets paired mode provides:

- **Anchoring helps short utterances.** "Sí" or "Da" or "Oui" are too short for any identifier to work reliably; without anchoring telling Gemma which language to expect, brief casual responses can be transcribed in the wrong language.
- **Paired conversation is what most users actually want.** A clinic visit, a school meeting, a job-site coordination — those are bilateral by their nature and benefit from the safety net.
- **TTS in auto-detect is intentionally off.** The operator is listening to the other speaker and doesn't want the app talking back. Translation appears as on-screen text only, which also shaves ~1–3 seconds per turn off playback latency — useful for rapid triage.

The dual-mode design lets the right tool fit the right job, and the handoff path (auto-detect populates B → toggle off → conversation continues in pair mode) means the modes compose naturally rather than fragmenting the experience.

---

## Use Cases

Puente-Bridge's thirteen-language coverage supports several practical offline translation scenarios:

### Humanitarian and Disaster Response (auto-detect mode shines here)
- Field medics and humanitarian workers communicating with people in crisis zones where infrastructure is down
- Border crossing points and refugee reception centers, where multi-language demand is bursty and unpredictable — operators don't know in advance what language the next person will speak
- Search-and-rescue teams working across language lines
- Triage at multilingual hospital intake — staff need to know what language to call an interpreter for, fast

### Travel and Migration
- Travelers in regions where their phone has no roaming or data
- Recent immigrants navigating institutional settings (schools, hospitals, courts) in their new country
- Diplomatic and consular staff conducting initial interviews

### Professional Field Work
- Construction, agriculture, and logistics crews with mixed-language workers and no connectivity on-site
- Field researchers conducting interviews in remote areas
- Oil/gas, mining, and utility technicians coordinating across language lines offshore or underground

### Healthcare (Any Country, Any Pair)
- Clinicians working with patients who speak one of the thirteen supported languages, in settings where an interpreter is not immediately available
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

## Healthcare Regulatory Context

> **Disclaimer up front:** The following is provided for informational and contextual purposes only. It is not legal advice. The developer is not a lawyer, is not endorsing any particular use of this app in any particular healthcare setting, and is not in a position to authorize anyone's use of machine translation under federal language-access regulations. Covered entities considering any clinical, administrative, or patient-facing use of an AI translation tool should consult qualified legal counsel familiar with their specific operations and jurisdictions.

The motivation section above describes the practical reality of interpreter access in high-LEP regions. The regulatory landscape is the formal framework that healthcare providers must operate within — and it is genuinely nuanced about the role of machine translation.

### Section 1557 of the Affordable Care Act

The 2024 Final Rule implementing Section 1557 (45 CFR Part 92) and the HHS Office for Civil Rights Dear Colleague Letter (December 5, 2024) establish requirements for "meaningful access" to individuals with limited English proficiency in covered health programs.

**45 CFR 92.201(a)** requires covered entities to "take reasonable steps to provide meaningful access to each individual with limited English proficiency."

**45 CFR 92.201(b)** requires that language assistance services be "provided free of charge, be accurate and timely, and protect the privacy and the independent decision-making ability of the individual with limited English proficiency."

**45 CFR 92.4** defines "machine translation" as "automated translation, without the assistance of or review by a qualified human translator, that is text based and provides instant translations between various languages, sometimes with an option for audio input or output."

**45 CFR 92.201(c)(3)** addresses machine translation directly. When machine translation is used for **critical** communications, "those translations must be reviewed by a qualified human translator to ensure accuracy" when:
- The underlying text is "critical to the rights, benefits, or meaningful access" of the LEP individual
- "Accuracy is essential"
- The source documents or materials contain "complex, non-literal or technical language"

The OCR Dear Colleague Letter clarifies that when machine translation is used in circumstances where it **does not** require qualified human review — i.e., where it is not critical to the rights, benefits, or meaningful access of an individual with LEP; accuracy is not essential; or the source documents or materials do not contain complex, non-literal or technical language — "the patients should be warned that the translated document may contain errors."

**45 CFR 92.201(e)(2)(i)** permits machine translation as a "temporary measure" in emergencies involving "an imminent threat to the safety or welfare of an individual or the public where there is no qualified interpreter for the individual with limited English proficiency immediately available," provided a qualified interpreter subsequently "confirms or supplements the initial communications."

### What this means in practice

The regulations distinguish between communications where machine translation may be acceptable and those where it is not. The categories below are illustrative, not exhaustive, and the line between them depends on specific clinical context.

| Potentially appropriate for machine translation | Requires a qualified human interpreter |
|---|---|
| General greetings and check-in | Informed consent for procedures or research |
| Appointment logistics and scheduling | Discharge instructions |
| Basic comfort and wayfinding questions | Medication dosage and administration |
| General patient education (non-critical) | Diagnosis communication |
| Supplementing communication while awaiting an interpreter | Surgical or treatment consent |
| Non-critical administrative communication | Legal notices, appeals, grievance rights |

### How Puente-Bridge's design addresses regulatory concerns

Without making any compliance claim for any specific organization or use, Puente-Bridge's architecture addresses several concerns the regulations raise:

- **Privacy (92.201(b)):** All processing is on-device. No patient data, audio, or text is transmitted, recorded, or stored. The app has no `INTERNET` permission declared and cannot reach the network. This eliminates the HIPAA exposure risk that OCR has flagged with consumer-grade cloud machine translation tools.
- **Free of charge (92.201(b)):** No per-use fees, subscriptions, or internet costs after the model file is downloaded once.
- **Independent decision-making (92.201(b)):** Patients are informed (via the in-app disclaimer screen, available in all 13 supported languages) about the tool's limitations and given agency to request alternatives.
- **Reducing reliance on restricted interpreters (92.201(e)):** Provides an alternative to using unqualified adults or minor children as ad-hoc interpreters — a practice the regulations restrict.
- **Disaster-response permitted use (92.201(e)(2)(i)):** Enables machine translation as a temporary measure in emergencies involving imminent threat where no qualified interpreter is immediately available — pending subsequent confirmation by a qualified interpreter.

### What Puente-Bridge explicitly does NOT do

- **It does not certify itself as a "qualified translator"** under 45 CFR 92.4. It is a machine translation tool by the regulation's own definition.
- **It does not authorize use for critical communications** where 92.201(c)(3) requires qualified human review. Operators must independently judge what counts as critical in their context.
- **It does not replace the obligation to provide qualified interpretation services** under any circumstance where the regulations require them.
- **It does not provide legal cover** to any organization deploying it. Compliance determinations are the responsibility of covered entities and their legal counsel.

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
| **Text-to-speech** | Android `TextToSpeech` with per-locale `Locale` | Native voices: English, Spanish, French, German, Portuguese, Italian, Chinese, Japanese, Korean, Hindi, Arabic, Russian, Vietnamese |
| **Language detection (paired mode)** | Kotlin-side heuristic in `Language.kt` | Script-range + diacritic + stopword scoring — deterministic binary A-vs-B decision, no model call required |
| **Language detection (auto-detect mode)** | Kotlin-side heuristic in `Language.kt` | Broad scoring across the curated supported set |
| **UI** | Jetpack Compose + Material 3 | Two-dropdown language picker + push-to-talk + conversation history |
| **Storage** | Shared `/sdcard/Download/litertlm-models/` with `MANAGE_EXTERNAL_STORAGE` | Single multi-GB model file serves both bilingual and multilingual apps |

### Key Design Decisions

**The model doesn't identify languages — Kotlin does.** Early prototypes asked Gemma to both transcribe AND report a `LANG:` tag. Under acoustic ambiguity, Gemma's LANG tag defaulted to `en` even when it correctly transcribed Spanish phonemes into Spanish text — we saw logs like `LANG: en` sitting above `TEXT: Hola, ¿cómo estás?`. So we pulled language identification out of the model entirely. Detection is now a deterministic Kotlin scoring function over:
- **Script range** (10 points per char) for the six non-Latin languages. Han characters → Chinese, Hangul → Korean, Hiragana/Katakana → Japanese, Devanagari → Hindi, Arabic block → Arabic, Cyrillic → Russian. These are trivially unambiguous.
- **Language-specific diacritics** (4 points per char) for the Latin-script group. `ñ/¿/¡` for Spanish, `ß/ä/ö/ü` for German, `ã/õ/ç` for Portuguese, `ç/œ/àâêî` for French, `àèéìòù` for Italian.
- **Stopwords** (2 points per match). `el/la/los` for Spanish, `der/die/das` for German, `le/la/les` for French, `o/a/os/as` for Portuguese, `il/lo/gli` for Italian, plain English function words for English.

The candidate pair (the two dropdown selections) is scored and the higher-scoring language wins. Because the comparison is always binary (A vs B, not thirteen-way), the heuristic only has to distinguish the two specific languages the user actually configured — a much easier problem than broad language identification.

**Audio encoder is anchored via the prompt.** Gemma is told in the transcribe prompt that the audio is one of the two selected languages. This pre-biases the audio encoder toward the right phoneme set and noticeably improves transcription quality for non-English audio — raw "transcribe this" prompts tend to force foreign phonemes into English-shaped tokens (`"muy bien"` → `"we're being"`) under the model's English training bias.

**Transcribe and translate are in separate Conversations.** Keeping both in one Conversation left the audio-token KV cache resident during translate and OOM-killed E4B after three or four turns. Splitting them with a native-finalizer drain between (150 ms of `runFinalization()` + `gc()` + delay) lets each step reclaim memory before the next allocates.

**Model switching drains finalizers before re-allocating.** On Pixel 10 Pro, switching from E4B back to E2B after inference requires three explicit GC/finalization passes (~600 ms total) to reliably release native GPU memory before the new engine's constructor runs. Without it, the new engine stomps the old one's still-resident buffers and produces `<unused48>`-token garbage or hard native crashes.

**Target auto-flips based on source.** Per turn, if the detected source is language A, the target is B — and vice versa. The user never needs to change the dropdowns between turns to have a back-and-forth conversation. `source == target` is impossible by construction.

**Translation prompt has a hard language-lock.** Gemma 4 (especially E2B) has a documented tendency to drop foreign-language tokens into otherwise-correct output (we observed Russian `иначе` appearing mid-Spanish output, Hindi Devanagari mid-English). The translate prompt explicitly states "respond in `<target>` ONLY; every word must be a valid `<target>` word; do not mix in words, characters, or scripts from any other language" and includes a one-shot example demonstrating the expected purity.

**Shared external model directory.** The app loads the multi-GB Gemma weights from `/sdcard/Download/litertlm-models/` rather than bundling them into the APK. This required `MANAGE_EXTERNAL_STORAGE` permission and a first-run "grant access" screen. Keeping the models outside app-private storage makes hackathon provisioning practical and avoids duplicating large model files across rebuilds or local test installs.

### Photo Translation Pipeline

Voice is the primary modality, but a lot of the communication barrier isn't spoken. Signs, menus, pharmacy labels, warning placards, intake forms, handwritten notes — all text, most of it static, none of it audible. The app adds a second pipeline for these cases, reusing Gemma's multimodal vision tower plus a different second-stage translator.

```
Camera (TakePicturePreview) or Android PhotoPicker
   │  JPEG bytes. Camera capture uses the system camera app in its own
   │  process — no CAMERA runtime permission needed. PhotoPicker requires
   │  no storage permission on API 33+.
   │
   ▼
Gemma 4 vision tower via LiteRT-LM  (visionBackend = Backend.GPU())
   │  Prompt: "Read any text visible in this image and output it verbatim,
   │           in its original language and native script."
   │  Output: OCR text in the source script
   │
   ▼
Language identification
   │  Paired mode → Language.scoreCandidate(text, A) vs (text, B), tiebreak
   │                to B (foreign-language assumption for the image use case)
   │  Auto mode  → broad Kotlin scorer across supported languages
   │
   ▼
Gemma 4 text translation
   │  OCR text → target-language translation, fully on-device.
   │  Skipped entirely when detected source == target (a menu already in the
   │  operator's app language shows OCR text unchanged).
   │
   ▼
Conversation bubble (thumbnail preview + native-script text + translation)
```

### Key Design Decisions (photo pipeline)

**Two Gemma stages — deliberately.** E4B can do "read and translate" in a single call; E2B often collapses the same combined prompt into OCR and returns the source text unchanged. Splitting into Gemma OCR followed by a Gemma text-translation pass makes the task boundary explicit while keeping the implementation LLM-only.

**Binary scorer in paired mode.** Single-word signs (`ALTO`, `STOP`, `SALIDA`) don't give any detector much context. In paired mode we already know the answer space is just {A, B}, so the same Kotlin scorer used for voice turns runs here too, with one extension: a tie or near-tie breaks to B rather than A. Reasoning — operators almost never photograph text in their own language.

**Thumbnail shows instantly.** A placeholder turn carrying the thumbnail is inserted into the conversation the moment the user picks the image; the translation text swaps in when Gemma returns. Users don't stare at a blank card while the vision encoder runs.

**No TTS on photo turns.** Photo output is read, not heard. Reading "(no text detected)" aloud for an accidentally-blank photo would be user-hostile; the "(text only — no TTS voice installed)" hint that fires for voice turns is suppressed for photos.

### Offline-First by Default

Every inference path — voice ASR, voice translation, image OCR, image translation, language identification — runs without network once the Gemma model files are provisioned. Airplane mode is a supported operating condition. No telemetry, no cloud fallback, no silent upload. The app is built for contexts where connectivity is absent by design — field clinics, disaster response, remote classrooms, travel in areas without data coverage — and the offline-first assumption is load-bearing everywhere in the architecture.

The one remaining manual step is the Gemma model itself. The `.litertlm` file is 2.6 GB (E2B) or 3.7 GB (E4B), which exceeds the Google Play APK and AAB limits, and side-loading via `adb push` or equivalent pre-provisioning is the practical distribution path for the hackathon. Organizations deploying the app at scale would typically pre-image devices before handing them out; the code already supports reading the model from a shared external-storage path precisely to make that workflow clean.

---

## Direct Translation Mode (Gemma 4 AST)

A Settings toggle exposes a second voice pipeline: **Gemma 4 AST** (audio-to-translated-text in a single inference). Compared to the default transcribe→translate path it cuts one full Gemma decode per turn — roughly 2× faster wall-clock — at the cost of not producing an intermediate transcription.

The wrinkle direct mode introduces is direction selection. With a fixed-target AST call, the model performs best when it knows up front which language to translate *into*. Puente-Bridge supports two operator choices:

1. **One-mic paired direct mode by default.** The default direct-mode UI stays consistent with paired transcription mode: one central mic, one turn at a time, with the app routing the turn across the selected pair. This keeps the common conversation flow simple.
2. **Manual direction when Auto-detect is off.** If the operator enables Manual direction, the one-mic control becomes two target-specific buttons. This locks the target language per turn and can improve accuracy when it is clear which direction the next utterance should go.

Auto-detect + direct mode keeps the single mic because auto already targets the selected app language regardless of source, so direction is unambiguous.

---

## Localized UI Chrome

The app's surface — buttons, status messages, screen titles, empty-state placeholders — is translated into the operator's selected language (the language A dropdown). Switching the dropdown re-renders the entire chrome instantly, in-process, without an app restart. All thirteen supported languages are covered:

- Top-bar buttons (About, Disclaimer, Settings, Clear)
- Model selector chip labels (Faster, Higher Accuracy)
- Status row (Loading…, Listening…, Translating…, Speaking…, Ready)
- Camera / Upload-photo button labels
- Hold-to-speak label
- Settings-screen toggles (Auto-detect, Direct translation) including descriptive subtitles
- About and Disclaimer screen chrome
- Conversation card empty-state placeholder

Implementation is a single `UiStrings` data class with one entry per language in `Strings.kt`, looked up via `Strings.forLanguage(languageA)`. The disclaimer body and About body are separately localized inside their own files (`Disclaimer.kt`, `AboutText.kt`) since those are paragraphs, not chrome.

This means a Spanish-speaking aid worker, a Vietnamese clinician, or a Russian field translator each see an interface in their own language from the moment they install the app — without depending on the device's system locale and without needing the operator to be literate in English to use the tool. The same translation tool is itself accessible across the language barriers it's designed to bridge.

---

## Gemma 4 Model Usage

Puente-Bridge leverages three distinct capabilities of the Gemma 4 architecture:

1. **Multimodal audio understanding.** The model processes raw 16 kHz PCM audio directly via `Content.AudioBytes`, eliminating the need for a separate ASR pipeline. One model handles both speech recognition and translation across all thirteen supported languages.

2. **Broad multilingual coverage.** All thirteen languages in the supported set are well-represented in Gemma 4's training data — quality drops significantly for lower-resource languages but remains high across our chosen set.

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

Summing the speaker counts (L1 + L2) of the thirteen supported languages gives roughly **5.5 billion speaker-slots**. That figure overcounts substantially because most multilingual people are listed in every language they speak — Spanish-English bilinguals appear in both buckets, Mandarin-English bilinguals in both, Hindi-English bilinguals in both, and so on. After deduplicating those overlaps, the realistic number of *distinct individuals* the app can serve in some language they speak is approximately **4 billion** — roughly half of humanity at 2026 world population (~8.1 B).

| Language | Speakers (incl. L2) |
|---|---:|
| English | ~1.5 B |
| Mandarin Chinese | ~1.1 B |
| Hindi | ~600 M |
| Spanish | ~590 M |
| Arabic | ~420 M |
| French | ~310 M |
| Portuguese | ~260 M |
| Russian | ~260 M |
| German | ~135 M |
| Japanese | ~125 M |
| Vietnamese | ~85 M |
| Korean | ~80 M |
| Italian | ~65 M |

The app is designed to support translation between any two selected languages in this set on a single phone, offline after model provisioning, with no per-conversation cost.

### Who This Helps

- **Humanitarian workers and disaster responders** in regions where infrastructure is down and translation is life-critical
- **Clinicians and community health workers** serving LEP patients in any country, in any setting where a qualified interpreter isn't immediately available
- **Teachers, social workers, and school staff** communicating with families across language lines
- **Refugees and migrants** navigating institutional systems in their new country
- **Travelers and their hosts** in regions where cloud translation is blocked, monitored, or unavailable
- **Mixed-language crews** in construction, agriculture, logistics, and field operations
- **Journalists, researchers, and interviewers** conducting conversations where cloud transmission of the audio would be inappropriate or dangerous
- **Anyone who simply doesn't want their conversation uploaded to somebody else's server**

### What Gemma On-Device Translation Adds

Offline translation already exists in major consumer tools. Puente-Bridge is not built on the claim that offline translation is unavailable; it explores a different architecture: a local multimodal LLM handling speech transcription, photo OCR, translation, and routing inside one inspectable app.

Unlike many offline translation workflows that require downloading specific language packs ahead of time, Puente-Bridge provisions one local Gemma model for all thirteen supported languages. Once the model is on-device, every supported pair is available without choosing or downloading additional per-language packs.

This matters in field settings where the operator may not know who will walk in next. Auto-detect mode scores Gemma's transcription across the curated supported set and translates into the selected app language, while paired mode uses the selected two-language constraint for better reliability in normal back-and-forth conversation.

The resulting system can:

- Run the full demo pipeline locally after model provisioning: speech input, transcription, translation, photo OCR, and TTS output.
- Avoid transmitting conversation audio, photos, or translated text to a remote service during use.
- Avoid per-request or per-character API costs once deployed on a device.
- Expose the prompts, routing heuristics, model choices, memory constraints, and failure modes for inspection and adaptation.
- Support field workflows that are awkward in many consumer translators: paired conversation mode, manual direction, replayable turns, direct speech translation, and Gemma-based photo translation.
- Continue operating in low-connectivity or no-connectivity settings, assuming the Gemma model files and Android TTS voices are already installed.

---

## Limitations and Responsible Use

Puente-Bridge is an AI translation tool. It will make mistakes, and it leaves significant portions of humanity uncovered. Honest scope-bounding:

### Quality limitations within the supported set

Translation quality is not uniform across the thirteen languages, but it's important to be clear about what we know and don't know: **we did not benchmark Gemma 4 E2B/E4B against reference translations for this project.** The expectations below are extrapolated from published multilingual LLM research on models of similar scale (mT5, NLLB, XGLM, earlier Gemma variants, and related work), combined with the well-documented fact that multilingual training corpora skew heavily toward English, Romance languages, and Mandarin. They should be read as *priors about how models like this tend to behave*, not as verified measurements of this specific deployment.

With that caveat, rough expectations:

- **Strongest quality is expected for English and Spanish** — both have strong representation in public multilingual corpora and are common anchor languages in translation data.
- **Good-to-usable quality is expected for the other major European languages** (French, German, Portuguese, Italian) and Mandarin Chinese (Simplified), all of which have substantial multilingual training coverage.
- **Weaker output is expected for Hindi and Vietnamese**, which are typically lower-resource in multilingual LLM training data even among major world languages. Translations will generally convey meaning but may read more mechanically and miss idiomatic register.
- **Japanese, Korean, Russian, and Arabic fall somewhere in the middle.** Well-represented in training but with structural features (honorific systems, noun declension, Arabic diglossia) that smaller multilingual models historically handle less gracefully. Expect "accurate but stiff" rather than "native-sounding."

None of the above has been validated against reference translations in this codebase. Users deploying the app for anything beyond casual communication should empirically evaluate quality for their specific language pair and use case.

**Language-specific patterns that tend to appear across multilingual models at this scale** (again, priors rather than verified behavior):

- **Chinese:** Simplified-only output (`zh-CN`). Traditional-Chinese-script regions (Taiwan, Hong Kong) may see Simplified characters. Cantonese-spoken input typically transcribes worse than Mandarin because audio training in multilingual models skews Mandarin.
- **Arabic:** Multilingual models tend to output Modern Standard Arabic (MSA) regardless of regional dialect input. Colloquial Egyptian/Levantine/Maghrebi/Gulf speakers will often receive grammatically-correct-but-stilted MSA back.
- **Japanese:** Politeness-level systems (です・ます vs plain form) are commonly flattened. Gendered speech particles tend to be neutralized.
- **Korean:** Honorific levels (해요체, 합니다체, 반말) typically default to mid-formal 해요 regardless of context.
- **Hindi:** Code-mixing with English ("Hinglish") is a common artifact when the model isn't sure of a term. Formal Hindi vs everyday Hindustani register may blend.
- **Vietnamese:** Tone marks usually preserve but ambiguous words occasionally get wrong tones. Northern vs Southern regional variants typically aren't distinguished.
- **German:** Compound-word construction and case agreement can be unreliable on complex sentences.
- **Portuguese:** Brazilian and European Portuguese are usually not distinguished. Output tends to lean Brazilian.

Across all thirteen, consistent limitations:

- **Regional dialects, slang, and technical vocabulary may not translate accurately.** The model is trained on standardized language data; heavily dialectal speech is noisier.
- **TTS pronunciation uses the device's installed voice.** Accents and prosody will not capture all regional variations. On devices without a particular language's voice installed, output falls back to on-screen text only.
- **Proper nouns and named entities** (places, brand names, uncommon surnames) are often transliterated inaccurately or left untranslated in the source script.
- **Numbers, dates, times, and units** translate reliably in simple sentences but may be garbled in complex utterances (e.g., "the meeting is at quarter past three on the nineteenth" → expect awkward output).
- **Humor, sarcasm, cultural references, wordplay** do not translate. The output will be technically accurate and completely flat.
- **High-stakes use cases still need human interpretation.** Medical consent, legal proceedings, surgical instructions, safety-critical communication — this tool is not a substitute for a qualified human interpreter in those contexts.
- **The paired-mode A/B constraint is by design.** If a speaker uses a third language (say, French when the pair is set to English/Spanish), the app will incorrectly route the turn. Auto-detect mode addresses this, but introduces its own failure modes.
- **Auto-detect mode has residual failure modes.** Without language anchoring during transcription, Gemma's audio encoder can occasionally force foreign phonemes into English-shaped tokens before the scorer ever sees the text. Languages outside the curated set are forced into the closest supported candidate. The app surfaces a low-confidence label when scorer confidence is weak.
- **E2B vs E4B matters.** The "Faster" (E2B) model is noticeably weaker than "Higher Accuracy" (E4B) on lower-tier languages. If translation quality for Japanese, Korean, Hindi, or Arabic feels wrong, switching to E4B often helps — at the cost of 1.5× latency and significantly more memory.

The overall honest framing: this tool is an offline communication aid, not a professional translation system. It lets people who don't share a language get an *approximate* understanding of each other in contexts where the alternative is no communication at all. For the contexts where approximate isn't good enough (surgical consent, legal testimony, diplomatic negotiation), the regulations and professional norms that require human interpreters exist for good reasons — and this tool doesn't change that.

### Languages NOT supported, and why

Significant populations are deliberately left out of the supported set. The decision criteria were strict: a language was only included if (1) Gemma 4's audio encoder produces honest native-script transcription for it, (2) Pixel ships a stock TTS voice for it, and (3) we could empirically verify both during development. Languages that *might* work but couldn't be tested were excluded — paper support for a language nobody can verify is worse than no support, because it creates false confidence.

| Language | Speakers | Why excluded |
|---|---:|---|
| Bengali | ~270 M | Limited Gemma audio coverage; no stock Pixel TTS voice |
| Indonesian / Malay | ~280 M | Untested audio encoder behavior; significant overlap with Malay we couldn't disambiguate |
| Urdu | ~230 M | Heavy mutual intelligibility with Hindi but uses Perso-Arabic script; routing ambiguity |
| Persian (Farsi) | ~120 M | Reasonable text quality but no testable TTS voice — couldn't verify the speech-output side |
| Turkish | ~85 M | Untested |
| Punjabi | ~125 M | Two-script problem (Gurmukhi vs Shahmukhi); inconsistent Pixel TTS coverage |
| Tamil, Telugu, Marathi | ~70-100 M each | Lower Gemma coverage; uncertain TTS support |
| Pashto, Dari, Tigrinya, Amharic, Swahili, Yoruba, Hausa | varies | Lower-resource for Gemma 4; would degrade to "translation in name only" |

These exclusions matter especially for the disaster-response use case discussed earlier. A Mediterranean refugee landing site might see Pashto, Dari, or Tigrinya speakers — and the app cannot help them. Adding these languages requires both (a) better audio encoder coverage in a future Gemma release, and (b) on-device TTS voices that don't currently ship with Android. Until then, the app is honest about what it does *not* do.

### Architectural extensibility

The `Language` enum is the single source of truth for the supported set. Adding a language is a matter of adding its metadata (display name, native name, locale, stopwords, diacritic set or script range) plus pushing a Gemma 4 model that covers it. No changes to the translation or TTS pipelines are required. This means the supported list can grow as Gemma's multilingual coverage and Android's TTS catalog improve, without any architectural rework.

---

## Technology Stack

- **Language**: Kotlin 2.3.0
- **UI framework**: Jetpack Compose + Material 3
- **ML runtime**: LiteRT-LM 0.10.0 (Google AI Edge)
- **Language identification**: Kotlin scoring over scripts, diacritics, and stopwords in `Language.kt`
- **Model**: Gemma 4 E2B / E4B (`.litertlm` format)
- **TTS**: Android `TextToSpeech` API
- **Audio**: Android `AudioRecord` (16 kHz / 16-bit / mono PCM) with hardware `NoiseSuppressor` + `AcousticEchoCanceler`
- **Build**: Gradle 8.11.1, AGP 8.7.3, JVM 17
- **Target**: Android API 26+, optimized for Pixel 10 Pro (Tensor G5)
- **Min hardware**: Any arm64 Android device with ~4 GB free external storage and a compatible GPU driver

The hackathon app lives in the `:app-multi` Gradle module. The repository intentionally contains the submitted Puente-Bridge app only; legacy prototype modules and older writeups were removed to keep the public submission focused.

---

## Why "Puente-Bridge"

*Puente* is Spanish for "bridge." The name fits the goal: a practical bridge across language barriers, spanning thirteen high-impact languages on a device that fits in a pocket, with no cloud underneath it.

---

## References

1. Ethnologue (2024), *Languages of the World* — speaker counts and language family data.
2. Google AI Edge, *LiteRT-LM documentation*, version 0.10.0.
3. Google, *Gemma 4 Technical Report*, 2026.
4. U.S. Census Bureau, 2021 American Community Survey, *Languages Spoken at Home*.
5. Pew Research Center, *Language Diversity and the Internet*, 2024.
6. Android Developers, *TextToSpeech* and `MANAGE_EXTERNAL_STORAGE` permission documentation.
7. 45 CFR Part 92, *Nondiscrimination in Health Programs and Activities*, 89 Fed. Reg. 37,522 (May 6, 2024).
8. 45 CFR 92.4, Definitions ("machine translation," "qualified interpreter," "qualified translator").
9. 45 CFR 92.201, "Meaningful access for individuals with limited English proficiency."
10. HHS Office for Civil Rights, *Dear Colleague Letter: Language Access Provisions of the Final Rule Implementing Section 1557 of the Affordable Care Act*, December 5, 2024.
11. Diamond, L. et al., "A Systematic Review of the Impact of Patient-Physician Non-English Language Concordance on Quality of Care and Outcomes," *J. Gen. Internal Med.* 34(8):1591 (2019).
12. Gonzalez-Barrera, A. et al., "Language Barriers in Health Care," KFF Survey on Racism, Discrimination, and Health (May 16, 2024).

---

## Repository

**Source Code**: [github.com/imcgr001/Puente-Bridge](https://github.com/imcgr001/Puente-Bridge)

The submitted Android app is in the `:app-multi` module.

**License**: Apache 2.0

---
