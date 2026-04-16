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
    RUSSIAN("ru", "Russian", "Русский", Locale("ru")),
    VIETNAMESE("vi", "Vietnamese", "Tiếng Việt", Locale("vi", "VN"));

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

            // Latin-script diagnostic characters. Vietnamese uses ô/â too
            // but its distinctive chars (ă ơ ư đ plus the dense tone-mark
            // stack) dominate the score for real Vietnamese text.
            val specialChars: String = when (candidate) {
                SPANISH -> "ñÑ¿¡áéíóúüÁÉÍÓÚÜ"
                FRENCH -> "çÇœŒàâêîïôûùÀÂÊÎÏÔÛÙ"
                GERMAN -> "äöüÄÖÜß"
                PORTUGUESE -> "ãõÃÕçÇáâàéêíóôúÁÂÀÉÊÍÓÔÚ"
                ITALIAN -> "àèéìòùÀÈÉÌÒÙ"
                VIETNAMESE ->
                    "ăâêôơưđĂÂÊÔƠƯĐ" +
                    // Tone-marked vowels (acute, grave, hook, tilde, dot)
                    "ạảãáàặẳẵắằậẩẫấầ" +
                    "ẹẻẽéèệểễếề" +
                    "ịỉĩíì" +
                    "ọỏõóòộổỗốồợởỡớờ" +
                    "ụủũúùựửữứừ" +
                    "ỵỷỹýỳ"
                else -> ""
            }
            score += text.count { it in specialChars } * 4

            // Common function words. Matched as whole words, case-insensitive.
            // Lists are deliberately broad to catch casual / short utterances
            // — "Muy bien" or "Sí, claro" have few diagnostic diacritics and
            // need stopword hits to score above the other candidate.
            // Stopword lists sourced from wrd.app/top-100-most-frequent-words
            // merged with our original greetings/courtesy words. ~100 entries
            // per Latin-script language. Non-Latin languages keep their shorter
            // lists since script-range scoring handles them (10 pts/char).
            val stopWords: List<String> = when (candidate) {
                ENGLISH -> listOf(
                    // Top-100 frequency
                    "be", "to", "and", "of", "a", "i", "you", "in", "it", "have",
                    "that", "not", "he", "do", "for", "on", "with", "his", "are", "as",
                    "she", "this", "we", "her", "at", "but", "what", "can", "me", "my",
                    "they", "from", "all", "by", "no", "will", "get", "him", "there", "so",
                    "know", "one", "your", "out", "if", "like", "said", "go", "or", "just",
                    "about", "who", "when", "which", "them", "now", "here", "make", "their",
                    "see", "then", "look", "right", "time", "well", "us", "into", "take",
                    "how", "come", "back", "more", "some", "want", "tell", "man", "think",
                    "over", "good", "two", "oh", "down", "only", "first", "after", "let",
                    "say", "other", "our", "way", "thing", "than", "yeah", "why", "before",
                    "little",
                    // Supplementary
                    "the", "is", "was", "been", "hi", "hello", "thanks", "yes", "please",
                    "sorry", "okay", "could", "would", "should", "i'm", "don't"
                )
                SPANISH -> listOf(
                    // Top-100 frequency
                    "de", "el", "la", "que", "a", "en", "y", "no", "un", "es",
                    "por", "lo", "los", "se", "una", "con", "estar", "ir", "tener", "las",
                    "para", "su", "me", "haber", "esta", "poder", "ser", "hacer", "te",
                    "como", "pero", "decir", "si", "mi", "querer", "bien", "le", "eso",
                    "yo", "tu", "ver", "bueno", "todo", "deber", "ya", "dar",
                    "esto", "sus", "era", "cuando", "muy", "este", "saber",
                    "ahora", "crear", "hay", "algo", "vamos", "dejar",
                    "nos", "dos", "tengo", "sobre", "encontrar", "hablar",
                    "hasta", "venir", "hombre", "solo", "pensar", "ese", "tiempo",
                    "llevar", "porque", "gracias", "mismo", "casa", "sentar", "soy",
                    "mucho", "necesitar", "entonces", "volver", "pasar",
                    // Supplementary
                    "hola", "sí", "buenos", "buenas", "mal", "del",
                    "está", "estoy", "eres", "son", "tiene", "quiero", "puedo",
                    "nada", "poco", "todos", "amigo", "señor", "usted",
                    "aquí", "allí", "dónde", "cuándo", "también"
                )
                FRENCH -> listOf(
                    // Top-100 frequency
                    "de", "la", "le", "et", "est", "que", "en", "je", "un", "les",
                    "il", "ce", "avoir", "des", "ne", "une", "pas", "du",
                    "vous", "dans", "pour", "tu", "qui", "au", "se", "on", "par",
                    "elle", "sur", "avec", "plus", "faire", "mais", "aller", "son",
                    "pouvoir", "me", "nous", "comme", "te", "tout", "bien", "sa",
                    "fait", "moi", "savoir", "lui", "cette", "ils", "ou", "dire",
                    "suivre", "non", "ses", "mon", "deux", "dit", "oui", "quoi",
                    "voir", "alors", "aussi", "toi", "leur", "ma", "jour",
                    "ici", "ces", "quand", "sans", "entre", "prendre", "venir",
                    "chose", "tous", "bon", "premier", "es", "encore", "autre",
                    "quelque", "pourquoi", "penser", "vouloir",
                    // Supplementary
                    "bonjour", "bonsoir", "merci", "salut", "comment",
                    "très", "mal", "maintenant", "aujourd'hui", "hier", "demain",
                    "suis", "sont", "ai", "avez", "avons", "ont", "va", "vais",
                    "c'est", "n'est", "s'il", "d'un", "qu'il"
                )
                GERMAN -> listOf(
                    // Top-100 frequency
                    "der", "sein", "die", "und", "das", "in", "ich", "ein", "dem", "zu",
                    "sie", "werden", "von", "er", "den", "mit", "nicht", "haben", "an",
                    "im", "es", "auf", "du", "eine", "als", "sich", "was", "wir",
                    "nach", "auch", "aus", "wie", "aber", "so", "ihr", "bei",
                    "dass", "ja", "bis", "mir", "noch", "um", "einer", "nur", "wollen",
                    "mein", "mich", "oder", "da", "wenn", "hier", "dieser", "sehen",
                    "geben", "sagen", "dann", "vor", "ihn", "gut", "dich", "man",
                    "seine", "alles", "schon", "unter", "jetzt", "doch", "lassen",
                    "ihm", "dir", "wieder", "diese", "gehen", "mehr", "erste",
                    "neu", "ihre", "finden", "keine", "ab", "wo", "tun", "zwei",
                    // Supplementary
                    "hallo", "danke", "bitte", "nein", "guten", "gute",
                    "morgen", "tag", "abend", "wann", "warum", "wer", "sehr",
                    "schlecht", "dort", "heute", "gestern", "dein",
                    "bin", "bist", "sind", "war", "habe", "hast", "hat"
                )
                PORTUGUESE -> listOf(
                    // Top-100 frequency
                    "o", "de", "a", "que", "um", "em", "e", "eu", "para", "ir",
                    "se", "esta", "com", "estar", "os", "por", "me", "ele", "isso",
                    "as", "ser", "ter", "como", "mas", "fazer", "dizer", "bem", "saber",
                    "mais", "sim", "dar", "ela", "ver", "podar", "aqui", "meu", "poder",
                    "te", "muito", "tem", "seu", "vamos", "vai", "estou", "sua",
                    "tudo", "ficar", "minha", "agora", "quando", "achar", "querer",
                    "era", "tenho", "dever", "mesmo", "eles", "falar", "certo",
                    "nada", "senhor", "bom", "isto", "quero", "sou", "quer",
                    "ano", "este", "dia", "deixar", "precisar", "todos", "mim",
                    "sobre", "pensar", "coisa", "tempo", "esse", "sabe", "conseguir",
                    "assim", "vir",
                    // Supplementary
                    "olá", "oi", "obrigado", "obrigada", "não", "você",
                    "do", "da", "dos", "das", "no", "na", "nos", "nas",
                    "bom", "boa", "noite", "tarde", "onde", "porque", "também",
                    "pouco", "ali", "ela", "nós", "vocês", "minha",
                    "nosso", "nossa", "está", "estão", "somos", "temos", "vou"
                )
                ITALIAN -> listOf(
                    // Top-100 frequency
                    "di", "il", "la", "e", "a", "in", "che", "non", "un", "i",
                    "per", "da", "le", "avere", "uno", "essere", "con", "si", "sono",
                    "lo", "potere", "ha", "ma", "ci", "mi", "come", "gli", "ho",
                    "fare", "su", "se", "cosa", "ti", "stato", "questo", "dovere",
                    "sapere", "andare", "volere", "era", "dire", "io", "venire",
                    "bene", "tutto", "anche", "altro", "solo", "mio", "sei", "molto",
                    "qui", "quello", "tu", "suo", "sua", "fatto", "me", "quando",
                    "questa", "anno", "ora", "vedere", "stare", "prima", "due",
                    "ne", "ad", "dove", "fa", "parte", "cui", "dopo", "lei",
                    "prendere", "tutti", "voi", "fu", "ed", "parlare", "loro",
                    "detto", "sia", "trovare", "mai", "tra", "lui", "volta",
                    "ancora", "oh", "grazie",
                    // Supplementary
                    "ciao", "prego", "sì", "no", "buongiorno", "buonasera",
                    "perché", "poco", "là", "mia", "tuo", "tua",
                    "nostro", "vostro", "sto", "stai", "sta", "hai",
                    "abbiamo", "avete", "hanno", "vado", "vai", "va"
                )
                VIETNAMESE -> listOf(
                    "là", "và", "có", "của", "không", "một", "các", "người", "trong",
                    "với", "cho", "được", "đã", "sẽ", "tôi", "bạn", "này", "đó", "nhưng",
                    "xin", "cảm", "ơn", "chào", "vâng", "dạ", "rồi", "đang", "những",
                    "nhà", "em", "anh", "chị", "ông", "bà", "bao", "nhiêu", "gì", "ai"
                )
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

        /**
         * Open-set detection: scores [text] against all 13 supported
         * languages and returns the highest. Used by the experimental
         * auto-detect mode where the user hasn't constrained input to a
         * specific A/B pair. Defaults to ENGLISH on a tie / empty text.
         */
        fun detectFromAllLanguages(text: String): Language {
            if (text.isEmpty()) return ENGLISH
            return entries.maxByOrNull { scoreCandidate(text, it) } ?: ENGLISH
        }
    }
}
