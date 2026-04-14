package com.pixeltranslator.multi.ui

import java.util.Locale

/**
 * The translator's supported languages. Each has strong Gemma 4 multilingual
 * coverage AND a stock Android TTS voice on recent Pixel devices. Other
 * languages will translate fine but may fall back to text-only output if the
 * device lacks an installed TTS voice.
 *
 * [code] is the two-letter ISO-639-1 tag emitted by the transcribe-step prompt
 * for auto-detection.
 */
enum class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val locale: Locale
) {
    ENGLISH("en", "English", "English", Locale.US),
    SPANISH("es", "Spanish", "Español", Locale("es", "ES")),
    FRENCH("fr", "French", "Français", Locale.FRENCH),
    GERMAN("de", "German", "Deutsch", Locale.GERMAN),
    PORTUGUESE("pt", "Portuguese", "Português", Locale("pt", "PT")),
    ITALIAN("it", "Italian", "Italiano", Locale.ITALIAN),
    CHINESE("zh", "Chinese (Simplified)", "中文", Locale.SIMPLIFIED_CHINESE),
    JAPANESE("ja", "Japanese", "日本語", Locale.JAPANESE),
    KOREAN("ko", "Korean", "한국어", Locale.KOREAN),
    HINDI("hi", "Hindi", "हिन्दी", Locale("hi", "IN")),
    ARABIC("ar", "Arabic", "العربية", Locale("ar")),
    RUSSIAN("ru", "Russian", "Русский", Locale("ru"));

    companion object {
        fun fromCode(code: String?): Language? {
            if (code == null) return null
            val lc = code.trim().lowercase()
            return entries.firstOrNull { it.code == lc }
        }

        /** Comma-separated list of codes, for use in prompts. */
        val allCodes: String = entries.joinToString(", ") { it.code }

        /**
         * Given a transcription and two candidates, returns which one the
         * text is more likely written in. Used instead of trusting Gemma's
         * LANG tag — the model's audio encoder transcribes accurately but
         * its self-reported language identification defaults to "en" under
         * ambiguity. Text-side features are deterministic and reliable.
         */
        fun scoreCandidate(text: String, candidate: Language): Int {
            if (text.isEmpty()) return 0
            var score = 0

            // Script range: strong signal if the language uses a distinct
            // script (Han, Hangul, Devanagari, Arabic, Cyrillic, Kana).
            val scriptRange: IntRange? = when (candidate) {
                CHINESE -> 0x4E00..0x9FFF
                JAPANESE -> 0x3040..0x30FF  // Hiragana + Katakana
                KOREAN -> 0xAC00..0xD7AF
                HINDI -> 0x0900..0x097F
                ARABIC -> 0x0600..0x06FF
                RUSSIAN -> 0x0400..0x04FF
                else -> null
            }
            if (scriptRange != null) {
                score += text.count { it.code in scriptRange } * 10
            }

            // Latin-script diagnostic characters.
            val specialChars: String = when (candidate) {
                SPANISH -> "ñÑ¿¡áéíóúüÁÉÍÓÚÜ"
                FRENCH -> "çÇœŒàâêîïôûùÀÂÊÎÏÔÛÙ"
                GERMAN -> "äöüÄÖÜß"
                PORTUGUESE -> "ãõÃÕçÇáâàéêíóôúÁÂÀÉÊÍÓÔÚ"
                ITALIAN -> "àèéìòùÀÈÉÌÒÙ"
                else -> ""
            }
            score += text.count { it in specialChars } * 4

            // Common function words. Matched as whole words, case-insensitive.
            val stopWords: List<String> = when (candidate) {
                ENGLISH -> listOf("the", "and", "is", "to", "of", "in", "it", "you", "that", "for", "are", "was", "be", "this", "have", "with")
                SPANISH -> listOf("el", "la", "los", "las", "del", "que", "es", "en", "un", "una", "por", "con", "para", "como", "no", "se", "hola", "gracias", "donde", "esto", "está", "estoy")
                FRENCH -> listOf("le", "la", "les", "de", "des", "du", "et", "est", "un", "une", "que", "qui", "dans", "avec", "pour", "nous", "vous", "ils", "elles", "bonjour", "merci")
                GERMAN -> listOf("der", "die", "das", "und", "ist", "ich", "nicht", "ein", "eine", "zu", "in", "mit", "für", "von", "auf", "aber", "auch", "hallo", "danke")
                PORTUGUESE -> listOf("o", "a", "os", "as", "de", "do", "da", "dos", "das", "e", "é", "em", "um", "uma", "para", "com", "por", "não", "que", "olá", "obrigado")
                ITALIAN -> listOf("il", "la", "lo", "gli", "le", "di", "e", "è", "che", "un", "una", "per", "con", "ma", "sono", "non", "ciao", "grazie")
                else -> emptyList()
            }
            val lower = text.lowercase()
            for (word in stopWords) {
                if (Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(lower)) {
                    score += 2
                }
            }
            return score
        }

        fun detectFromText(text: String, a: Language, b: Language): Language {
            val scoreA = scoreCandidate(text, a)
            val scoreB = scoreCandidate(text, b)
            return if (scoreB > scoreA) b else a
        }
    }
}
