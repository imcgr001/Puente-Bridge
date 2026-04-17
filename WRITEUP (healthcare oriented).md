# MedLingua: On-Device Speech-to-Speech Medical Translation with Gemma 4

## Gemma 4 Good Hackathon Submission | Health & Sciences Track

---

## Summary

MedLingua is a fully on-device, real-time speech-to-speech translator built for clinical encounters between English-speaking providers and Spanish-speaking patients. It runs natively on a Google Pixel 10 Pro using **Gemma 4 E2B/E4B** for multimodal speech recognition and translation via **LiteRT-LM** with GPU acceleration on the Tensor G5 NPU. Text-to-speech output is handled by Android's native TTS engine with language-appropriate voice selection.

The app is designed to supplement -- not replace -- qualified human interpreters in non-critical healthcare interactions, in alignment with the language access provisions of **Section 1557 of the Affordable Care Act** (45 CFR Part 92) and the **HHS Office for Civil Rights Dear Colleague Letter** on language access (December 5, 2024).

**No data leaves the device.** All speech processing, transcription, translation, and audio synthesis occur entirely on the phone. No audio is recorded, stored, or transmitted over the internet.

---

## The Problem

Approximately 68 million people in the United States speak a language other than English at home, and 8.2% of those speak English less than "very well" (U.S. Census Bureau, 2021 ACS). In healthcare settings, language barriers directly contribute to worse health outcomes, reduced quality of care, and disparities in access to services. Studies consistently show that patients who cannot communicate effectively with their providers experience higher rates of adverse events, lower treatment adherence, and reduced satisfaction with care.

The 2024 Final Rule implementing Section 1557 of the Affordable Care Act requires covered entities to take "reasonable steps to provide meaningful access to each individual with limited English proficiency" (45 CFR 92.201(a)). Language assistance services must be "provided free of charge, be accurate and timely, and protect the privacy and the independent decision-making ability of the individual with limited English proficiency" (45 CFR 92.201(b)).

However, qualified human interpreters are not always immediately available. Rural and underserved facilities face chronic shortages. Wait times for phone or video interpretation services can delay care. In these gaps, patients and providers often resort to ad hoc communication -- gestures, bilingual staff who may not meet the regulatory definition of "qualified interpreter," or family members including minor children (whose use is restricted under 45 CFR 92.201(e)).

MedLingua addresses this gap by providing an immediate, private, on-device translation tool that can serve as a supplementary language assistance resource while a qualified interpreter is being arranged, or as a communication aid in non-critical interactions where machine translation is permissible under the regulations.

---

## Regulatory Alignment: Section 1557 and the HHS OCR Dear Colleague Letter

### Where MedLingua Fits in the Regulatory Framework

The 2024 Final Rule and the December 2024 OCR Dear Colleague Letter establish a clear framework for when machine translation may and may not be used:

**Machine translation WITHOUT qualified human review is permissible when:**
- The communication is "not critical to the rights, benefits, or meaningful access" of the LEP individual
- Accuracy is not essential
- The source materials do not contain "complex, non-literal or technical language"
- Patients are warned that the translation may contain errors

*(45 CFR 92.201(c)(3); OCR DCL pp. 4)*

**Machine translation REQUIRES qualified human translator review when:**
- The underlying text is "critical to the rights, benefits, or meaningful access" of the LEP individual
- Accuracy is essential
- Source documents contain complex, non-literal, or technical language

*(45 CFR 92.201(c)(3))*

**Machine translation may be used as a temporary measure in emergencies** involving "an imminent threat to the safety or welfare of an individual or the public where there is no qualified interpreter for the individual with limited English proficiency immediately available," provided a qualified interpreter subsequently "confirms or supplements the initial communications."

*(45 CFR 92.201(e)(2)(i); OCR DCL pp. 5)*

### MedLingua's Intended Use Cases (Non-Critical Scenarios)

MedLingua is designed for use in **non-critical clinical communication** where machine translation is permitted under the regulations:

| Appropriate Use | NOT Appropriate Without Human Interpreter |
|---|---|
| General check-in / greeting | Informed consent for procedures |
| Appointment logistics | Discharge instructions |
| Basic symptom description (initial triage) | Medication dosage and instructions |
| Wayfinding within a facility | Diagnosis communication |
| General patient comfort questions | Surgical or treatment consent |
| Supplementing communication while awaiting a qualified interpreter | Legal notices, HIPAA, appeals |

### Built-In Safeguards Aligned with Regulatory Requirements

MedLingua implements several safeguards that directly address regulatory requirements:

1. **Patient Disclaimer (45 CFR 92.201(b) -- independent decision-making)**
   The app presents a Spanish-language disclaimer at the operator's discretion informing the patient that:
   - This is an AI-powered translation tool that may contain errors
   - A human interpreter is available on request at any time
   - The patient should voice any concerns
   This aligns with the OCR guidance that "patients should be warned that the translated document may contain errors" and protects the patient's independent decision-making ability.

2. **Complete Privacy (45 CFR 92.201(b) -- protect privacy)**
   All processing occurs entirely on-device. No audio, text, or patient data is transmitted over the internet, recorded, or stored. Data exists only in volatile memory during the active conversation and is immediately discarded. This exceeds the privacy requirements of 92.201(b) and eliminates HIPAA concerns related to third-party cloud translation services.

3. **Human Interpreter Escalation**
   The disclaimer explicitly instructs the patient that they may request a human interpreter at any point during the conversation. The app is positioned as a supplement to -- not a replacement for -- qualified interpretation services. If the patient requests a human interpreter, the provider is expected to arrange one per 45 CFR 92.201(c)(1).

4. **Free of Charge (45 CFR 92.201(b))**
   The tool runs on hardware already present in clinical settings and requires no subscription, per-use fees, or internet connectivity. Language assistance is provided at no cost to the patient.

5. **No Use of Minors or Unqualified Adults**
   By providing an immediate on-device alternative, MedLingua reduces the temptation to rely on "unqualified adults" or minor children to interpret, which is restricted under 45 CFR 92.201(e).

---

## Technical Architecture

### Pipeline

```
Microphone (16kHz/16-bit/mono PCM)
    |
    v
[WAV encoding] --> Gemma 4 E2B/E4B (LiteRT-LM, GPU)
                        |
                        |-- Message 1: "Transcribe this speech"
                        |-- Message 2: "Translate to [Spanish/English]"
                        |
                        v
                   Transcription + Translation
                        |
                        v
              Android TextToSpeech (language-appropriate voice)
                        |
                        v
                     Speaker
```

### Components

| Component | Technology | Purpose |
|---|---|---|
| **ASR + Translation** | Gemma 4 E2B or E4B via LiteRT-LM | On-device multimodal speech understanding and translation |
| **Hardware Acceleration** | Tensor G5 GPU (via `Backend.GPU()`) | Low-latency inference on Pixel 10 Pro NPU |
| **Audio Capture** | Android `AudioRecord` | 16kHz, 16-bit, mono PCM with `VOICE_RECOGNITION` source |
| **Text-to-Speech** | Android `TextToSpeech` | Native English (`Locale.US`) and Spanish (`Locale.es_ES`) voices |
| **UI** | Jetpack Compose + Material 3 | Push-to-talk interface with conversation history |
| **Target Device** | Pixel 10 Pro (Tensor G5) | Consumer-grade mobile hardware |

### Key Design Decisions

**Two-Step Translation in a Single Conversation Session.** Rather than asking Gemma to transcribe and translate in one prompt (which produced unreliable results), MedLingua sends two sequential messages within the same `Conversation` session:
1. "Transcribe this speech exactly as spoken."
2. "Translate the following to [Spanish/English]."

This separation ensures the model never skips the translation step and allows the app to detect the source language from the transcription before requesting the translation direction.

**Automatic Language Detection.** The app detects the source language from the transcription using a heuristic that checks for Spanish-specific characters (accented vowels, n-tilde, inverted punctuation) and common Spanish function words. This determines the translation direction per-turn, allowing free-form alternation between languages without manual switching.

**WAV Header Wrapping.** Gemma 4's audio encoder (via LiteRT-LM's `miniaudio` backend) requires audio in a container format, not raw PCM. The app wraps captured PCM bytes in a 44-byte RIFF/WAV header before passing them as `Content.AudioBytes`.

**Model Selection at Runtime.** The app offers a choice between Gemma 4 E2B (2.6 GB, faster) and E4B (3.7 GB, higher quality) via UI toggle chips, allowing providers to balance speed against translation quality based on device capabilities and clinical needs.

---

## Gemma 4 Model Usage

MedLingua leverages Gemma 4's **multimodal audio understanding** capability -- a key differentiator of the Gemma 4 architecture. The model processes raw audio input natively through `Content.AudioBytes`, eliminating the need for a separate ASR pipeline. This means:

- **Single model** handles both speech recognition and translation
- **Native audio encoder** in Gemma 4 processes 16kHz PCM audio directly
- **GPU-accelerated inference** via LiteRT-LM's `Backend.GPU()` on Tensor G5
- **Audio backend on CPU** (`Backend.CPU()`) as required by the E2B model's audio encoder constraints
- **Conversation API** enables multi-turn interaction within a single session

The app supports both **E2B** (optimized for mobile, ~2.6 GB) and **E4B** (higher quality, ~3.7 GB) model variants, selectable at runtime.

---

## Privacy Architecture

| Property | Implementation |
|---|---|
| Data transmission | None. Fully offline. |
| Audio storage | Never recorded. Exists only in volatile memory during capture. |
| Transcription/translation storage | In-memory only. Cleared on app close or "Clear" button. |
| Cloud dependencies | None. All inference runs on-device. |
| Third-party services | None. No analytics, telemetry, or external APIs. |
| HIPAA exposure surface | Eliminated. No PHI leaves the device. |

This architecture directly addresses the OCR's concern that "consumer-grade machine translation tools often lack HIPAA compliance safeguards, risking exposure of protected health information." By keeping all processing on-device, MedLingua eliminates this risk entirely.

---

## Impact Potential

### Scale of the Problem
- **25 million** LEP individuals in the U.S. are eligible for or enrolled in health programs
- **Spanish** is the most common non-English language, spoken by over 41 million people
- Healthcare facilities in 48 states report encountering Spanish-speaking LEP patients
- Interpreter wait times at many facilities range from 15-45 minutes, during which meaningful communication is impossible

### How MedLingua Helps
- **Immediate availability**: No wait time, no scheduling, no phone queues
- **Zero marginal cost**: Runs on existing consumer hardware with no per-minute charges
- **Works offline**: Functions in rural clinics, ambulances, disaster response settings, and areas with poor connectivity
- **Reduces reliance on ad hoc interpretation**: Provides a better alternative than untrained bilingual staff or family members
- **Preserves patient dignity**: Patients receive communication in their language with a clear disclaimer about limitations

### Deployment Scenarios
1. **Rural clinics** without on-site interpreters where phone interpretation has poor connectivity
2. **Emergency departments** as a temporary measure while qualified interpreters are located (per 45 CFR 92.201(e)(2)(i))
3. **Ambulatory care** for non-critical check-ins, scheduling, and general communication
4. **Community health events** and screening programs
5. **Home health visits** where bringing an interpreter is logistically difficult

---

## Limitations and Responsible Use

MedLingua is **not a replacement for qualified human interpreters.** The regulatory framework is clear that machine translation requires human review for critical communications (45 CFR 92.201(c)(3)). This tool is designed for:

- Non-critical conversations where machine translation is permissible
- Temporary bridging while a qualified interpreter is being arranged
- Supplementing (not replacing) existing language access programs

Known technical limitations:
- Translation quality depends on audio clarity, ambient noise, and speech patterns
- Regional dialects and medical terminology may not always translate accurately
- The model may occasionally fail to translate, requiring the user to re-record
- TTS pronunciation may not capture all regional variations

The app's disclaimer mechanism, human interpreter escalation path, and clear positioning as a supplementary tool are designed to mitigate these limitations in accordance with regulatory guidance.

---

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material 3
- **ML Runtime**: LiteRT-LM 0.10.0 (Google AI Edge)
- **Model**: Gemma 4 E2B/E4B (`.litertlm` format)
- **TTS**: Android `TextToSpeech` API
- **Audio**: Android `AudioRecord` (capture) + `AudioTrack` (historically, now TTS)
- **Build**: Gradle 8.11.1, AGP 8.7.3, Kotlin 2.3.0
- **Target**: Android API 26+, optimized for Pixel 10 Pro (Tensor G5)
- **Min Hardware**: Any arm64 Android device with ~4GB free storage

---

## References

1. 45 CFR Part 92, "Nondiscrimination in Health Programs and Activities," 89 Fed. Reg. 37,522 (May 6, 2024).
2. 45 CFR 92.201, "Meaningful access for individuals with limited English proficiency."
3. 45 CFR 92.4, Definitions ("machine translation," "qualified interpreter," "qualified translator").
4. HHS Office for Civil Rights, "Dear Colleague Letter: Language Access Provisions of the Final Rule Implementing Section 1557 of the Affordable Care Act," December 5, 2024.
5. U.S. Census Bureau, 2021 American Community Survey, "Why We Ask Questions About Languages Spoken At Home."
6. Diamond, L. et al., "A Systematic Review of the Impact of Patient-Physician Non-English Language Concordance on Quality of Care and Outcomes," J. Gen. Internal Med. 34(8):1591 (2019).
7. Gonzalez-Barrera, A. et al., "Language Barriers in Health Care," KFF Survey on Racism, Discrimination, and Health (May 16, 2024).

---

## Repository

**Source Code**: [github.com/your-repo/pixel-ai-translator](https://github.com/your-repo/pixel-ai-translator)

**License**: Apache 2.0

---

*Built with Gemma 4 on Pixel 10 Pro. Designed for healthcare equity.*
