# Multilingual Engineering Challenges

A postmortem of the non-obvious problems hit while building the multilingual (12-language) extension of the on-device Gemma 4 translator, and the solutions that stuck. Each section states the symptom, the root cause, and the fix — including things we tried that didn't work, because those are instructive too.

---

## 1. Language Detection

### Problem

The bilingual app (EN↔ES) relied on a Kotlin heuristic over Spanish-specific characters and stopwords to decide which direction to translate. That works for two languages but doesn't generalize. We initially tried asking Gemma itself to identify the spoken language as part of the transcribe step, emitting a structured response like:

```
LANG: es
TEXT: Hola, ¿cómo estás?
```

### What went wrong

Gemma's self-reported `LANG` tag was unreliable. Under acoustic ambiguity — which happens often for short utterances or accented speech — the model defaulted to `en` regardless of what it actually transcribed. We saw log lines like:

```
Transcribe raw response: LANG: en
TEXT: Hola, ¿cómo estás?
```

The transcription was perfect Spanish. The language tag said English. The downstream routing logic then sent Spanish text through an English → Spanish translation, producing "Spanish → Spanish" dead-ends (or silent no-ops).

### Root cause

Multilingual instruction-tuned models of this size have a strong English prior for classification/identification tasks. When the instruction is split across two sub-tasks ("transcribe AND identify"), the model's attention to the identification half suffers. Asking only for transcription produces reliable Spanish text; asking for the language label as well produces reliable English labels regardless of audio content.

### Solution

Two-stage detection, with only one stage running on the model:

**Stage 1 (Gemma)** — transcribe with a prompt that *anchors* the audio encoder to the A/B language pair:

> The speaker is using either English (English) or Spanish (Español). Listen carefully for that language's phonemes. Transcribe exactly what was said, in the original language. Output ONLY the transcription.

This pre-biases the encoder toward the right phoneme set without asking it to label.

**Stage 2 (Kotlin)** — score the transcription against the two candidate languages and return the winner. Three weighted signals:

| Signal | Weight | Coverage |
|---|---|---|
| Script range (e.g. `U+4E00–9FFF` for Han, `U+AC00–D7AF` for Hangul) | **10 pts/char** | Chinese, Japanese, Korean, Hindi, Arabic, Russian |
| Language-specific diacritics (`ñ/¿/¡`, `ß`, `ã/õ/ç`, `ă/ơ/ư/đ`, …) | **4 pts/char** | Latin-script languages |
| Common function words (`muy/bien/pero`, `the/and/is`, …) | **2 pts/match** | Short casual utterances with no diacritics |

Higher score wins. Ties go to A.

### Why this works

- **Binary comparison only.** Never one-of-twelve — always A-vs-B. Distinguishing English from Japanese is trivial (one Kana character ends the decision).
- **Deterministic.** Same input, same output; no model "having a bad day."
- **Cheap.** Regex passes over a short string. Zero model tokens.
- **Debuggable.** Scores are logged per turn: `Detected es (A=en score=0, B=es score=14)`.

### Known failure modes

- **Short, all-ASCII utterances.** "Ok" or a proper noun alone can score 0/0; the A default is a guess. Mitigated by aggressive stopword lists that cover casual register.
- **Third-language speech.** If the pair is EN/ES and the speaker uses French, we force it into one of the two. By design — the pair constraint is the price of reliable binary detection.
- **Code-switching.** Whichever language dominates the utterance wins. Usually right; occasionally not.

### Tried and rejected

- **Open-set model-based identification.** Described above. Unreliable.
- **Constraining Gemma's LANG tag to {A, B}.** Gemma still emitted `en` outside the allowed set under ambiguity; constraining the parser let through the same bad answer.
- **Per-pair hardcoded rules.** Would have meant O(N²) rules for 12 languages. Didn't scale.

---

## 2. Model-Switch Native Crashes

### Problem

Tapping a different model chip (E2B ↔ E4B) mid-session hard-crashed the app. Screen disappeared with no stack trace in logcat beyond "Loading Gemma 4 E2B (2.6 GB) from…" followed by silence and a new PID on the next launch.

### Symptoms

- Switching *to* the larger model (E2B → E4B) usually worked.
- Switching *back* to the smaller model (E4B → E2B) after any inference reliably crashed.
- Crash was a native SIGSEGV, not a Kotlin exception — our `try/catch` around `initialize()` never ran.
- Before the hard crash we also observed a softer failure mode: the new model would emit `<unused48>` token spam (reserved placeholder tokens in Gemma's vocabulary) — a tell for GPU memory stomping.

### Root cause

LiteRT-LM's `Engine.close()` is non-blocking. The JNI-owned native allocations (GPU textures, KV cache buffers, model weight arenas) are released via Java finalizers — which run on a separate thread at the JVM's discretion, not synchronously. When we called `close()` and then immediately tried to construct a new `Engine()` for the other model, the native memory from the previous engine was still resident. The new engine started writing weights into addresses the old one still held references to, corrupting GPU state.

### What we tried (in order)

1. **`engine?.close(); engine = null`.** No effect — finalizers haven't run yet.
2. **One `System.gc() + System.runFinalization() + delay(300)` cycle.** Partially worked. Reduced crash frequency but didn't eliminate.
3. **Three cycles × 200 ms (~600 ms total), each `runFinalization → gc → delay`.** Fixed the bilingual app's bilingual pair. The multi app still crashed — slightly more memory pressure, tighter margins.
4. **Five cycles × 300 ms (~1500 ms total), `gc → runFinalization → gc → delay`** (the canonical "double-gc-with-finalization-between" pattern). Still crashed.
5. **`isLoading` guard on `switchModel`.** Closed a concurrent-init race — `init()` and the `ON_RESUME` observer could both fire `initialize()` simultaneously, native-crashing on two `Engine()` constructors at once. Necessary, but not sufficient.
6. **Process restart.** Final solution. Described below.

### Solution: process restart on model change

When the user taps a different model chip:
1. Synchronously write the new choice to SharedPreferences (`commit()`, not `apply()`).
2. Serialize the current conversation turns to a cache file (`cacheDir/pending_turns.json`) so history survives.
3. Show a "Switching to …" status for 400 ms.
4. Queue a `packageManager.getLaunchIntentForPackage(...)` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`.
5. Call `Runtime.getRuntime().exit(0)`.

Android's activity manager relaunches the app within a second. The new process reads the persisted model choice and cold-loads it — no prior engine, no teardown, no stomping. The turns file is read and immediately deleted on init, preserving the privacy story for ordinary app closes.

### Why this works

Process-level teardown is the only deterministic memory-reclaim mechanism Android offers to an app. Everything below that is best-effort.

### Tradeoff

~1 second of "app blink" on model switch. Acceptable for a setting users change once per session. The in-process drain code is still there as defense-in-depth but is now unreachable in practice.

---

## 3. `MAX_TOKENS` Exceeded

### Problem

Longer recordings (~15+ seconds of speech) threw:

```
INVALID_ARGUMENT: exceeding the maximum number of tokens allowed 737 >= 512
```

### Root cause

`EngineConfig.maxNumTokens` sets the per-`Conversation` KV cache budget. We had it at 512. A single audio clip's tokenized representation plus the transcribe prompt can easily exceed 700 tokens — the audio encoder produces roughly 10 tokens per second of audio.

### Solution

Bumped `MAX_TOKENS` to 1536. Tradeoff: larger pre-allocated KV cache per Conversation — roughly a few hundred MB extra per turn on E4B. Tolerable on a 16 GB Pixel 10 Pro given the earlier memory fixes; would need per-model tuning on tighter devices.

---

## 4. Multilingual Translation Drift

### Problem

Translation output occasionally contained tokens from *other* languages. A memorable example:

> **"Un कोशिश más. Esto mejor funciona, иначе será molesto."**

Spanish-intended output with Hindi (Devanagari कोशिश = "attempt") and Russian (Cyrillic иначе = "otherwise") substitutions. The semantics were correct — the model picked words that *meant* the right things. But the script was wrong.

### Root cause

Gemma 4 is heavily multilingual. Under decoding ambiguity between two near-equivalent tokens, the model occasionally samples one from another language whose embedding is "close enough." This is well-documented behavior of multilingual instruction-tuned models, particularly at smaller sizes (E2B more than E4B — quantization noise widens the decision boundary).

### Solution

Hardened the translate prompt with:
1. Explicit language lock: *"Respond in {target} ONLY. Every word must be a valid {target} word. Do not mix in words, characters, or scripts from any other language."*
2. A one-shot example demonstrating correct-language output for the target.

The negative instruction ("do not mix") combined with a concrete example reduces the drift rate substantially. Doesn't eliminate it — this is a model property, not a prompt bug — but makes it rare enough to be a non-issue in practice.

---

## 5. Prompt Echo

### Problem

A diagnostic prompt used placeholder syntax:

```
Respond in EXACTLY this format and NOTHING else:
LANG: <two-letter code>
TEXT: <transcription>
```

Gemma would sometimes echo that template verbatim *before* emitting the real answer. Our regex would then match the echoed `TEXT: <transcription>` and extract the literal word "transcription" (or worse, the whole paragraph after it) as the user's supposed utterance. Translation then faithfully rendered that mouthful into the target language.

### Root cause

`<...>` placeholder syntax uses characters that Gemma sometimes treats as literal content rather than template slots. Combined with sequence-to-sequence bias, the model includes the echo in its response.

### Solution

1. **Removed placeholder syntax.** Replaced with prose describing the expected format:

```
Line 1 starts with "LANG:" followed by a space and a two-letter code.
Line 2 starts with "TEXT:" followed by a space and the transcription.
```

2. **Stricter parser.** Regex now requires `LANG: xx` to be followed (with limited intervening content) by `TEXT:`, so an isolated echoed fragment doesn't match.

Later, with the Kotlin-side detection move, the entire `LANG:` parsing step was removed — the prompt just asks for transcription and nothing else, and the parser is the identity function.

---

## 6. Concurrent `initialize()` Race

### Problem

On app launch, a hard native crash fired intermittently before the UI even rendered. Stack-free; process just died.

### Root cause

We had two code paths invoking `tryLoadModels()`:
1. `ViewModel.init {}` — fires when the activity is created.
2. A `DisposableEffect` observing `Lifecycle.Event.ON_RESUME` — fires when the activity becomes visible.

Normally `ON_RESUME` fires after `init`, but the first call is async (launches a coroutine for `gemma.initialize()`), and the `isModelLoaded` flag doesn't flip until that coroutine completes. In the window between "init launched the coroutine" and "coroutine finished and set isModelLoaded=true," the `ON_RESUME` observer's call to `tryLoadModels()` would pass the guard and launch a *second* coroutine that also called `gemma.initialize()`. Two concurrent LiteRT-LM `Engine()` constructors → native crash.

### Solution

`@Volatile private var isLoading: Boolean = false` guard, set to `true` before the coroutine launches and cleared in `finally`. Both `tryLoadModels()` and `switchModel()` check it. A concurrent call now returns immediately.

---

## 7. Stale `SharedPreferences` Across Reinstalls

### Problem

A user's "Language B" dropdown mysteriously defaulted to Arabic on what they believed was a fresh install.

### Root cause

Android's **Auto Backup** feature (enabled by default when `android:allowBackup="true"` is in the manifest) syncs `SharedPreferences`, files in the app's private directory, and some other state to the user's Google account. A reinstall of the same `applicationId` can restore that backed-up state automatically. An old test value from a previous build survived across what looked like a clean install.

### Solution

Two-part fix:
1. **Bump the prefs file name** (`multi_translator_prefs` → `multi_translator_prefs_v2`). Anything stored in the old name becomes unreachable. The code reads the new name, finds nothing, and applies defaults cleanly.
2. **Set `android:allowBackup="false"`** in the manifest. Prevents future state from syncing across installs.

Bumping the version number whenever prefs schema changes is now the pattern.

---

## 8. Kotlin String Interpolation With Non-Latin Scripts

### Problem

Build failure on a Korean string:

```
Unresolved reference: 'b로'.
```

Code:

```kotlin
emptyPlaceholder = { a, b -> "$a 또는 $b로 말씀해 주세요." }
```

### Root cause

Korean Hangul characters are valid Kotlin identifier characters. `$b로` isn't parsed as "variable `b` followed by the literal characters `로`" — it's parsed as a single identifier `b로`, which doesn't exist.

### Solution

Explicit brace delimiters for the variable:

```kotlin
emptyPlaceholder = { a, b -> "$a 또는 ${b}로 말씀해 주세요." }
```

Good general rule for any multilingual string interpolation: use `${var}` whenever the character immediately following the variable could form part of an identifier — letters, digits, and underscore in any script Unicode considers "letter."

---

## 9. Cross-App Model File Sharing

### Problem

The bilingual and multilingual apps are separate `applicationId`s (so they install side-by-side) and each needs the multi-GB `.litertlm` model files. Storing two copies wasted 7+ GB on a storage-limited device.

### Root cause

`Context.getExternalFilesDir(null)` resolves to `/sdcard/Android/data/<applicationId>/files/`, which is sandboxed. Android 11+ specifically blocks `/sdcard/Android/data/<otherapp>/` access even for apps holding `MANAGE_EXTERNAL_STORAGE` — there's no loophole. Symlinks hit the same access check on resolve.

### Solution

Move the models to a non-sandboxed path both apps can reach:
- **Path:** `/sdcard/Download/litertlm-models/`
- **Permission:** `MANAGE_EXTERNAL_STORAGE` in both apps' manifests
- **First-run flow:** On first launch, check `Environment.isExternalStorageManager()`. If false, show a "Grant all-files access" screen that deep-links to `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`. A `DisposableEffect` on `Lifecycle.Event.ON_RESUME` re-invokes `tryLoadModels()` automatically when the user comes back from Settings.

Models are defined once as `GemmaTranslatorManager.SHARED_MODEL_DIR` in each module — kept in sync manually.

### Tradeoff

`MANAGE_EXTERNAL_STORAGE` is a "scary" permission on the Play Store (would trigger review). Fine for a dev/sideload build.

---

## 10. Per-Locale TTS Availability

### Problem

Android's `TextToSpeech` has inconsistent voice coverage across devices. Pixel ships strong coverage; other OEMs ship English-only; specific languages (Arabic, Hindi) may require manual voice-data installation.

### Solution

- Check availability per-locale at runtime: `tts.isLanguageAvailable(locale)` returns `LANG_AVAILABLE` / `LANG_COUNTRY_AVAILABLE` / `LANG_NOT_SUPPORTED` / `LANG_MISSING_DATA`.
- UI exposes `ttsAvailableForA` and `ttsAvailableForB` flags in `TranslatorUiState`.
- Dropdown shows "no voice" in red below any language without a voice.
- Conversation bubbles for a target with no voice say "(text only — no TTS voice installed)" instead of silently producing no audio.
- The disclaimer screen's "Play aloud" buttons disable when the corresponding language lacks a voice, with an explanatory label.

Silent failure was the real enemy. Failing loudly with a labeled reason beats failing quietly.

---

## 11. UI Localization Following Language A

### Problem

Hard-coded English UI chrome (buttons, status, hints) regardless of the user's primary language. A Korean speaker operating the app with A=Korean, B=English would still see "About / Disclaimer / Clear" in English.

### Solution

A `Strings.kt` file with a `UiStrings` data class (~22 fields) and a `forLanguage(lang: Language): UiStrings` function, populated for all 13 languages. The main screen reads `Strings.forLanguage(uiState.languageA)` on each recomposition and renders from there.

ViewModel-set status messages (`"Ready"`, `"Listening..."`, etc.) are still English internally; the UI maps them through a `localizeStatus(raw, strings)` helper. Dynamic diagnostic strings (error messages, "Model not found at ...") pass through unchanged because translating compiler-like text produces worse UX than leaving it English.

The Disclaimer screen body has its own in-screen A/B language picker (different use case — it's specifically spoken aloud to the other party). The About screen body has one translated version per language in `AboutText.kt`.

### Known limitation

Translations for languages I don't speak (Arabic, Korean, Hindi) were written on a confidence spectrum and should be reviewed by a native speaker before any production release. Scaffolding handles corrections as one-line edits.

---

## 12. Short-Text Detection Failure

### Problem

A real session produced:

```
Transcribe: Muy bien, Anito.
Detected en (A=en score=0, B=es score=0)
```

Spanish text, zero score for Spanish, zero score for English, and the tie-break default of A (English) won. Translation routed English → Spanish, output was garbage.

### Root cause

The Spanish stopword list was "academic" — `el/la/que/con/para`. Casual register words like `muy`, `bien`, `hola`, `gracias` were missing. "Muy bien, Anito" has no diacritics and no matching stopwords, so it scored zero.

### Solution

Aggressive stopword list expansion — casual greetings, common adverbs, frequent verbs, pronouns, greetings, courtesy words — for all Latin-script languages. Spanish grew from ~22 to ~60 entries. Now "Muy bien" scores 4 for Spanish (matching both "muy" and "bien") vs 0 for English.

Longer term, a better solution would be character-n-gram statistics or even a tiny on-device language-ID model (fastText-style, < 1 MB). But hand-curated stopwords get the pair-scoped binary decision to ~95% accuracy with zero runtime cost.

---

## 13. Script Mixing in Dropdown Labels

### Problem

With UI language = English but Language B = Chinese, the dropdown button showed only "中文" — no English context. Users unfamiliar with the script couldn't verify which language was selected.

### Solution

Dropdown buttons now stack the English display name on top and the native name below:

```
English           Chinese
                  中文
```

When `displayName == nativeName` (e.g. English) the button collapses to one line. Font sizes staggered (12sp primary, 10sp secondary) to keep button height tolerable.

In the dropdown *menu*, items always show both: `Chinese (Simplified) — 中文`.

---

## 14. Row Width Balancing

### Problem

Each row of buttons (model chips, action buttons, language pickers) had buttons sized to their content, so rows looked uneven. Worst case: the push-to-talk mic was visually off-center because Language A and Language B buttons had different label widths.

### Solution

`Modifier.weight(1f)` on each child in a row, combined with `Arrangement.spacedBy(8.dp)`. Each button takes an equal share of the row's horizontal space. For the mic row specifically: weighted flanks on either side of the fixed-size mic mean the mic is mathematically centered regardless of label length.

Text inside buttons gets `modifier = Modifier.fillMaxWidth()` + `textAlign = TextAlign.Center` so labels sit in the middle of their slot.

---

## 15. Conversation Continuity Across Process Restart

### Problem

Once the model-switch process restart was in place, the conversation history disappeared on each switch. User would build up a back-and-forth, switch models, and lose the context.

### Solution

Before the `exit(0)`, serialize `_uiState.value.turns` as JSON to `cacheDir/pending_turns.json`. On the next launch, `ViewModel.init` reads the file, deserializes, populates `TranslatorUiState.turns`, and **deletes the file immediately** — successful read or not.

The file existing only between the switch tap and the next launch's read (typically < 1 second) preserves the privacy story: normal app close, swipe-from-recents, or OS kill leave nothing behind.

JSON schema is deliberately flat — two strings for the bilingual app, same plus source/target language codes + `spokenAloud` flag for the multi app. `org.json` built-ins avoided pulling in a new dependency for what's ultimately a transient cache.

---

## 16. Silent-Audio Prompt-Echo Hallucination

### Problem

A user tapped and released the push-to-talk button with no meaningful speech (likely accidental, or a held-down-but-silent capture). Instead of failing cleanly, Gemma produced a plausible-sounding but entirely fabricated transcription:

```
Transcribe: English: The speaker is using English.
Español: El hablante está usando español.
```

This text wasn't spoken. It wasn't even close to what the audio contained. The model generated it by drawing on the *prompt* context — our transcribe prompt literally says "The speaker is using either English or Spanish" — when given no real audio signal to anchor to. The detection heuristic then scored it as Spanish (Spanish characters, more matches), the pair flipped, a translation turn was logged, and the conversation history now contained a fictional exchange.

### Root cause

Multimodal sequence-to-sequence models with autoregressive decoding don't have a clean "no input" response path. When the audio encoder produces nearly-null tokens (silence, mic rubbing, room tone), the text decoder still needs to produce *something*, and its easiest continuation is to echo or paraphrase plausible text from nearby in the prompt. The model isn't broken — it's doing exactly what autoregressive decoders do. But the output is indistinguishable from a real transcription without domain knowledge.

This is the same failure mode as prompt-echo (§5), but triggered by absent input rather than ambiguous input.

### Solution

Reject the audio on the Kotlin side, *before* it reaches Gemma. A simple two-gate check in `isAudioMeaningful(pcm: ByteArray)`:

1. **Minimum duration.** PCM buffer must be at least 9600 bytes (0.3 seconds at 16 kHz / 16-bit / mono). Catches accidental taps and button jitter.
2. **Minimum RMS amplitude.** Root-mean-square of the signed-16-bit samples must exceed 200 out of a 32 767 peak — roughly −44 dB FS. Catches sustained silence, dead mic, and very quiet room tone.

```kotlin
private fun isAudioMeaningful(pcm: ByteArray): Boolean {
    if (pcm.size < 9600) return false
    val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
    var sumSquares = 0.0
    var count = 0
    while (buf.remaining() >= 2) {
        val s = buf.short.toInt()
        sumSquares += (s * s).toDouble()
        count++
    }
    val rms = Math.sqrt(sumSquares / count)
    return rms > 200.0
}
```

If the check fails, the ViewModel silently returns to `"Ready"`. No error dialog, no "no speech detected" toast, no new conversation bubble — the user gets exactly nothing, which is the correct response to "nothing was said." Matches platform conventions (system voice assistants behave the same way).

### Why silent rather than noisy failure

Originally planned to show a localized "No speech detected" status — required a new `UiStrings` field × 13 translations. User's feedback: too much ceremony for a low-stakes edge case. The cost of silent rejection is that a user who genuinely held the mic but was unheard doesn't know *why* nothing happened — but they can just try again, which costs nothing. The cost of the verbose approach was translation debt and visual noise for what is fundamentally a no-op.

### Tradeoffs

- **Threshold tuning.** RMS 200 is empirical; may need adjustment on devices with aggressive mic gain/noise-suppression. If it's too strict, genuinely quiet speakers get rejected; if too lax, the original failure mode leaks back in. Current value has worked across the Pixel 10 Pro test device.
- **Minimum duration is a ceiling on responsiveness.** Users can't do ultra-short utterances ("Hi!" ≈ 0.25 s). 0.3 s is a tradeoff biased toward correctness.
- **Doesn't catch all silent-in-spirit input.** A user holding the mic while saying "um…" for 2 seconds passes the gate (duration fine, RMS fine) but still gives Gemma little to work with. The encoder's output for low-content speech is better than for true silence, so the hallucination mode is much less severe — usually just a short "Um" or similar.

---

## 17. E4B Native Memory Pressure on Pixel 10 Pro

### Problem

The multilingual app could hard-crash with E4B loaded even on a Pixel 10 Pro-class device. Logcat showed native allocator failures rather than a managed Kotlin heap exception:

```
Scudo ERROR: internal map failure (error desc=Out of memory)
Fatal signal 6 (SIGABRT) in tid "Recompile (dwt)"
```

This means the process was failing native `mmap()`/allocator work while LiteRT-LM, ART, GPU/OpenCL, and ML Kit were all sharing the same address-space and resident-memory budget. `largeHeap` does not solve this class of failure because the Gemma weights, GPU buffers, KV cache, image tensors, and ML Kit models are not ordinary Java heap objects.

### Root cause

E4B's model weights are only part of the footprint. The risky peaks came from several allocations stacking at once:

1. **Oversized KV/cache budget.** `maxNumTokens = 1536` preallocates more native cache than most short translation turns need. E4B pays a much higher per-token memory cost than E2B.
2. **Optional vision resources loaded beside the speech path.** The multilingual app configured the vision tower on GPU at engine startup even though the common path is speech. That reserved scarce GPU/native memory before a photo was ever selected.
3. **ML Kit translate preloading at the same time as Gemma startup.** First-launch preloading is useful for offline readiness, but it creates transient translator instances and model downloads right next to E4B's largest allocation window.
4. **Full-resolution photo frames.** Pixel camera stills are commonly around 4080x3072 in default binned mode, and can be roughly 8160x6144 in 50 MP mode. Passing those directly into native vision preprocessing can allocate large temporary tensors while E4B is already resident.
5. **Any accidental overlap between Gemma conversations.** Even if the UI tries to prevent it, native inference should be protected by a single-flight lock because overlapping `Conversation` allocations are expensive and failure is not catchable.

### Solution

The app now treats E4B as a memory-sensitive mode:

- **Per-model token budget.** E2B keeps `1536` max tokens. E4B uses `1024`, which is enough for short speech translation and OCR output while reducing the native KV/cache allocation.
- **Single-flight Gemma inference.** All Gemma calls go through a `Mutex`, so speech, direct translation, OCR, and text translation cannot overlap native `Conversation` allocations.
- **Vision backend tradeoff.** E4B uses `Backend.CPU()` for the vision tower while keeping the main LLM on GPU. E2B keeps GPU vision. Photo OCR remains available; E4B just avoids reserving GPU memory for the less-common path.
- **Defer ML Kit preload under E4B.** E2B still preloads translation models after Gemma and TTS are ready. E4B skips the startup preload and lazily loads on first photo translation instead.
- **Adaptive photo downsampling, not cropping.** Camera/gallery images are resized proportionally before Gemma OCR only when they exceed a long-edge limit. Nothing is cut off. E2B uses a 2560 px long-edge limit for dense documents; E4B uses 1920 px to reduce native vision preprocessing peaks.

### Tradeoffs

- **E4B max output length is lower.** Very long documents or unusually verbose translations may truncate sooner under E4B. For the app's intended short speech turns and sign/menu/photo snippets, 1024 tokens is the better stability tradeoff.
- **E4B photo OCR may be slower.** Moving the vision tower to CPU protects memory but can cost latency. The app keeps photo support because OCR quality is more important than speed for that mode.
- **First photo translation on E4B may need model setup.** Skipping ML Kit preload avoids startup crashes, but the first source/target photo translation pair may incur lazy model initialization or download if it was not already cached.
- **Downsampling is a safety valve, not a crop.** A default Pixel photo around 4080x3072 becomes roughly 1920x1446 on E4B. The whole image remains visible, but tiny far-away text may benefit from the user moving closer or cropping in the camera/gallery before submitting.

---

## Cross-Cutting Principles

Patterns that held up across all the above fights:

1. **Move correctness-critical decisions out of the model.** Gemma is excellent at audio → text and text → text, but treat its metadata output (language tags, self-reports) as untrustworthy. Deterministic Kotlin is better for classification.
2. **Binary problems beat N-way problems.** Detecting one-of-12 is hard; detecting A-or-B is trivial. Constrain problems to the pair the user actually configured.
3. **Process boundaries are the only deterministic memory reclaim on Android.** If a native library doesn't give you a synchronous unload, don't try to simulate one — restart the process.
4. **Fail loudly with reasons, not silently with defaults.** A visible "(text only — no TTS voice)" beats silent no-audio every time.
5. **Log the raw model I/O.** Almost every non-trivial bug in this session was diagnosed from a single `Log.i` line showing what Gemma actually returned — not what we thought it should have returned.
6. **Privacy by architecture, not by promise.** Every feature that persists state did so with an explicit audit — `pending_turns.json` exists for ~1 second; the manifest has no `INTERNET` permission; auto-backup is off. The privacy story isn't a policy, it's a structural property.

---

*Written to save the next person three days.*
