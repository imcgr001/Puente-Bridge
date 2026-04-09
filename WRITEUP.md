# Puente: Offline Real-Time Speech-to-Speech Translation with Gemma 4

## Gemma 4 Good Hackathon Submission | Health & Sciences + Digital Equity

---

## Summary

Puente ("bridge" in Spanish) is a fully on-device, real-time English-Spanish speech-to-speech translator. It runs natively on a Google Pixel 10 Pro using **Gemma 4 E2B/E4B** for multimodal speech recognition and translation via **LiteRT-LM** with GPU acceleration on the Tensor G5 NPU. Text-to-speech output uses Android's native TTS engine with language-appropriate voice selection.

**No data leaves the device.** All speech processing, transcription, translation, and audio synthesis occur entirely on the phone. No audio is recorded, stored, or transmitted over the internet. The app requires no internet connection to function.

Puente is built for anyone who needs to communicate across the English-Spanish language barrier -- in places where internet is unavailable, privacy matters, or professional interpretation services are not immediately accessible.

---

## The Problem

Approximately 68 million people in the United States speak a language other than English at home, and 8.2% of those -- roughly 5.6 million people -- speak English less than "very well" (U.S. Census Bureau, 2021 ACS). Spanish is by far the most common non-English language, spoken by over 41 million people in the U.S. The need for real-time English-Spanish translation is enormous and cuts across every sector of daily life.

Existing translation tools overwhelmingly require cloud connectivity. Google Translate, Apple Translate, and similar services send audio to remote servers for processing. This creates three fundamental problems:

1. **No internet, no translation.** Rural areas, disaster zones, remote worksites, and many institutional settings lack reliable connectivity. When the network goes down, translation stops.

2. **Privacy by default is impossible.** Cloud-based services transmit spoken words -- potentially sensitive conversations -- to third-party servers. Users must trust that their data is handled appropriately, with no way to verify.

3. **Cost and access barriers.** Professional interpretation services charge per-minute fees. Many of the people and communities who most need translation are least able to afford it.

Puente eliminates all three problems by running entirely on a single phone, with no internet required, no data transmitted, and no ongoing cost.

---

## Use Cases

### Everyday Communication
- Conversations between English and Spanish speakers in any setting
- Travel in Spanish-speaking regions (or Spanish speakers traveling in the U.S.)
- Parent-teacher conferences at schools
- Interactions at government offices, social services, or community organizations
- Workplace communication on job sites, in retail, or in service industries

### Areas Without Internet
- Rural and remote communities
- Disaster response and emergency situations
- Developing regions with limited infrastructure
- Institutional settings with restricted network access (correctional facilities, secure government buildings)
- Outdoor and field work (agriculture, construction, conservation)

### Healthcare (With Important Caveats)

Puente has a potential application in healthcare settings as a supplementary communication tool. The app includes a built-in Spanish-language disclaimer designed for clinical use that informs patients the tool is AI-powered, may contain errors, and that a human interpreter is available on request.

However, **healthcare use of machine translation is subject to specific federal regulations**, and the following section discusses the regulatory landscape for informational purposes only.

---

## Healthcare Regulatory Landscape: Section 1557 of the ACA

> **Disclaimer:** The following is provided for informational purposes only and does not constitute legal advice. We are not lawyers and cannot determine regulatory compliance for any specific organization, use case, or clinical scenario. Covered entities should consult qualified legal counsel to evaluate whether and how this tool may be used within their language access programs.

The 2024 Final Rule implementing Section 1557 of the Affordable Care Act (45 CFR Part 92) and the HHS Office for Civil Rights Dear Colleague Letter (December 5, 2024) establish requirements for providing "meaningful access" to individuals with limited English proficiency (LEP) in covered health programs.

### What the Regulations Say About Machine Translation

**45 CFR 92.201(a)** requires covered entities to "take reasonable steps to provide meaningful access to each individual with limited English proficiency."

**45 CFR 92.201(b)** requires that language assistance services be "provided free of charge, be accurate and timely, and protect the privacy and the independent decision-making ability of the individual with limited English proficiency."

**45 CFR 92.201(c)(3)** addresses machine translation directly:

> If a covered entity uses "machine translation" (defined as "automated translation, without the assistance of or review by a qualified human translator, that is text based and provides instant translations between various languages, sometimes with an option for audio input or output" -- 45 CFR 92.4) for **critical** communications, "those translations must be reviewed by a qualified human translator to ensure accuracy" when:
> - The underlying text is "critical to the rights, benefits, or meaningful access" of the LEP individual
> - "Accuracy is essential"
> - The source documents or materials contain "complex, non-literal or technical language"

The OCR Dear Colleague Letter (pp. 4) clarifies that when machine translation is used in circumstances where it **does not** require qualified human review -- "i.e., where it is not critical to the rights, benefits, or meaningful access of an individual with LEP; accuracy is not essential; or the source documents or materials do not contain complex, non-literal or technical language" -- "the patients should be warned that the translated document may contain errors."

**45 CFR 92.201(e)(2)(i)** permits machine translation as a "temporary measure" in emergencies involving "an imminent threat to the safety or welfare of an individual or the public where there is no qualified interpreter for the individual with limited English proficiency immediately available," provided a qualified interpreter subsequently "confirms or supplements the initial communications."

### What This Means in Practice

| Potentially Appropriate for Machine Translation | Requires Qualified Human Interpreter |
|---|---|
| General greetings and check-in | Informed consent for procedures or research |
| Appointment logistics and scheduling | Discharge instructions |
| Basic comfort and wayfinding questions | Medication dosage and administration |
| General patient education (non-critical) | Diagnosis communication |
| Supplementing communication while awaiting an interpreter | Surgical or treatment consent |
| Non-critical administrative communication | Legal notices, appeals, grievance rights |

### How Puente's Design Addresses Regulatory Concerns

While we cannot determine compliance for any specific use case, Puente's architecture addresses several concerns raised in the regulatory framework:

- **Privacy (92.201(b)):** All processing is on-device. No patient data, audio, or text is transmitted, recorded, or stored. This eliminates the HIPAA exposure risk that the OCR has flagged with "consumer-grade machine translation tools."
- **Error disclosure (OCR DCL pp. 4):** The built-in disclaimer warns patients in Spanish that translations may contain errors.
- **Human interpreter escalation (92.201(c)(1)):** The disclaimer instructs patients that a human interpreter is available on request at any time.
- **Free of charge (92.201(b)):** No per-use fees, subscriptions, or internet costs.
- **Independent decision-making (92.201(b)):** Patients are informed about the tool's limitations and given agency to request alternatives.
- **Reducing reliance on restricted interpreters (92.201(e)):** Provides an alternative to using unqualified adults or minor children, whose use as interpreters is restricted.

**Again: organizations considering healthcare use should consult legal counsel.** The regulatory landscape is nuanced, and the distinction between "critical" and "non-critical" communications depends on specific clinical context.

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

**Two-Step Translation in a Single Conversation Session.** Rather than asking Gemma to transcribe and translate in one prompt (which produced unreliable results), Puente sends two sequential messages within the same `Conversation` session:
1. "Transcribe this speech exactly as spoken."
2. "Translate the following to [Spanish/English]."

This separation ensures the model never skips the translation step and allows the app to detect the source language from the transcription before requesting the translation direction.

**Automatic Language Detection.** The app detects the source language from the transcription using a heuristic that checks for Spanish-specific characters and common Spanish function words. This determines the translation direction per-turn, allowing free-form alternation between languages without manual switching. Speak English five times in a row -- each turn translates to Spanish. Switch to Spanish whenever you want.

**WAV Header Wrapping.** Gemma 4's audio encoder (via LiteRT-LM's `miniaudio` backend) requires audio in a container format, not raw PCM. The app wraps captured PCM bytes in a 44-byte RIFF/WAV header before passing them as `Content.AudioBytes`.

**Model Selection at Runtime.** The app offers a choice between Gemma 4 E2B (2.6 GB, faster) and E4B (3.7 GB, higher quality) via UI toggle chips, allowing users to balance speed against translation quality based on their device and needs.

---

## Gemma 4 Model Usage

Puente leverages Gemma 4's **multimodal audio understanding** -- a key capability of the Gemma 4 architecture. The model processes raw audio input natively through `Content.AudioBytes`, eliminating the need for a separate ASR pipeline. This means:

- **Single model** handles both speech recognition and translation
- **Native audio encoder** in Gemma 4 processes 16kHz PCM audio directly
- **GPU-accelerated inference** via LiteRT-LM's `Backend.GPU()` on Tensor G5
- **Audio backend on CPU** (`Backend.CPU()`) as required by the E2B model's audio encoder constraints
- **Conversation API** enables multi-turn interaction within a single session

The app supports both **E2B** (optimized for mobile, ~2.6 GB) and **E4B** (higher quality, ~3.7 GB) model variants, selectable at runtime without restarting the app.

---

## Privacy Architecture

| Property | Implementation |
|---|---|
| Data transmission | None. Fully offline. |
| Audio storage | Never recorded. Exists only in volatile memory during capture. |
| Transcription/translation storage | In-memory only. Cleared on app close or "Clear" button. |
| Cloud dependencies | None. All inference runs on-device. |
| Third-party services | None. No analytics, telemetry, or external APIs. |
| Network requirement | None. Airplane mode works. |

This is not "privacy by policy" -- it is **privacy by architecture**. There is no server to breach, no API key to leak, no data retention policy to read. The conversation exists only in the room where it happens.

---

## Impact Potential

### The Numbers
- **41 million** Spanish speakers in the U.S. (U.S. Census Bureau, 2021)
- **5.6 million** speak English less than "very well"
- **25 million** LEP individuals eligible for or enrolled in health programs
- Spanish is the most common non-English language in **43 of 50 states**
- Professional interpreter services cost **$1.50-$3.00+ per minute**; many encounters last 15-30 minutes

### Who This Helps
- **Patients** who cannot communicate with their doctors and currently wait 15-45 minutes for phone interpreters -- or go without
- **Small clinics and rural providers** that cannot afford full-time interpreters or per-minute phone services
- **Teachers and school staff** communicating with Spanish-speaking parents
- **First responders** in the field with no connectivity
- **Social workers and case managers** conducting home visits
- **Construction site foremen** coordinating with mixed-language crews
- **Anyone traveling** where Spanish or English is not the local language

### What Makes On-Device Different
Cloud translation services already exist. What they cannot do:

- Work without internet
- Guarantee that a sensitive conversation is not transmitted to a third party
- Function in airplane mode, in a basement, in a rural area with no cell signal, or in a disaster zone where infrastructure is down
- Operate at zero marginal cost per conversation

Puente can do all of these things because the entire pipeline -- from microphone to speaker -- runs on a single phone.

---

## Limitations and Responsible Use

Puente is an AI translation tool. It will make mistakes.

- Translation quality depends on audio clarity, ambient noise, and speech patterns
- Regional dialects, slang, and technical vocabulary may not always translate accurately
- The model may occasionally fail to translate, requiring the user to re-record
- TTS pronunciation uses standard American English and Castilian Spanish voices and may not capture all regional variations
- This is not a substitute for professional interpretation in high-stakes situations

The app includes a Spanish-language disclaimer that can be shown and read aloud to the non-English speaker, informing them of the tool's limitations and their right to request a human interpreter (in healthcare or any other professional context).

---

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material 3
- **ML Runtime**: LiteRT-LM 0.10.0 (Google AI Edge)
- **Model**: Gemma 4 E2B/E4B (`.litertlm` format)
- **TTS**: Android `TextToSpeech` API
- **Audio**: Android `AudioRecord` (16kHz/16-bit/mono PCM capture)
- **Build**: Gradle 8.11.1, AGP 8.7.3, Kotlin 2.3.0
- **Target**: Android API 26+, optimized for Pixel 10 Pro (Tensor G5)
- **Min Hardware**: Any arm64 Android device with ~4GB free storage

---

## References

1. U.S. Census Bureau, 2021 American Community Survey, "Why We Ask Questions About Languages Spoken At Home."
2. 45 CFR Part 92, "Nondiscrimination in Health Programs and Activities," 89 Fed. Reg. 37,522 (May 6, 2024).
3. 45 CFR 92.201, "Meaningful access for individuals with limited English proficiency."
4. 45 CFR 92.4, Definitions ("machine translation," "qualified interpreter," "qualified translator").
5. HHS Office for Civil Rights, "Dear Colleague Letter: Language Access Provisions of the Final Rule Implementing Section 1557 of the Affordable Care Act," December 5, 2024.
6. Diamond, L. et al., "A Systematic Review of the Impact of Patient-Physician Non-English Language Concordance on Quality of Care and Outcomes," J. Gen. Internal Med. 34(8):1591 (2019).
7. Gonzalez-Barrera, A. et al., "Language Barriers in Health Care," KFF Survey on Racism, Discrimination, and Health (May 16, 2024).

---

## Repository

**Source Code**: [github.com/your-repo/pixel-ai-translator](https://github.com/your-repo/pixel-ai-translator)

**License**: Apache 2.0

---

*Built with Gemma 4 on Pixel 10 Pro. No cloud. No recording. No barriers.*
