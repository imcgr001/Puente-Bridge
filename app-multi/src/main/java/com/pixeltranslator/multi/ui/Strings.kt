package com.pixeltranslator.multi.ui

/**
 * On-screen UI chrome, translated per-language. Keyed by the user's Language A
 * selection so the app speaks to its operator in their language. The
 * About and Disclaimer screen bodies have their own in-screen language
 * pickers and are not governed by this table.
 */
data class UiStrings(
    val title: String,
    val aboutButton: String,
    val disclaimerButton: String,
    val settingsButton: String,
    val clearButton: String,
    val modelQuick: String,
    val modelHighQuality: String,
    val statusReady: String,
    val statusLoading: String,
    val statusListening: String,
    val statusTranslating: String,
    val statusSpeaking: String,
    val statusNoVoice: String,
    val micPermissionRequired: String,
    val loadingModels: String,
    val holdToSpeak: String,
    val takePhotoButton: String,
    val uploadPhotoButton: String,
    /** Takes both languages' native names and returns the empty-state sentence. */
    val emptyPlaceholder: (nativeA: String, nativeB: String) -> String,
    // Disclaimer + About screen chrome.
    val disclaimerScreenTitle: String,
    val playAloudLabel: String,
    val beginButton: String,
    val aboutScreenTitle: String,
    val aboutScreenSubtitle: String,
    val closeButton: String,
    // Settings screen body.
    val autoDetectTitle: String,
    val autoDetectSubtitle: String,
    val directTranslationTitle: String,
    val directTranslationSubtitle: String,
    val instructionsTitle: String,
    val instructionsBody: String,
    /** Photo: detected language not in your pair. Receives the detected language name. */
    val outOfPairWarning: (langName: String) -> String,
    /** Settings: title for the app-language picker. */
    val appLanguageTitle: String,
    val appLanguageSubtitle: String,
    val explicitDirectionTitle: String,
    val explicitDirectionSubtitle: String,
    val explicitDirectionDisabledSubtitle: String
)

object Strings {
    fun forLanguage(lang: Language): UiStrings = when (lang) {
        Language.ENGLISH -> UiStrings(
            title = "AI Translator",
            aboutButton = "About",
            disclaimerButton = "Disclaimer",
            settingsButton = "Settings",
            clearButton = "Clear",
            modelQuick = "Faster",
            modelHighQuality = "Higher Accuracy",
            statusReady = "Ready",
            statusLoading = "Loading…",
            statusListening = "Listening…",
            statusTranslating = "Translating…",
            statusSpeaking = "Speaking…",
            statusNoVoice = "Ready (no voice installed)",
            micPermissionRequired = "Microphone permission required",
            loadingModels = "Loading models…",
            holdToSpeak = "Hold to speak",
            takePhotoButton = "Take photo",
            uploadPhotoButton = "Upload photo",
            emptyPlaceholder = { a, b -> "Speak in $a or $b.\nThe app will translate into the other." },
            disclaimerScreenTitle = "Before you begin",
            playAloudLabel = "Play aloud:",
            beginButton = "Begin",
            aboutScreenTitle = "About",
            aboutScreenSubtitle = "Fully offline, fully private",
            closeButton = "Close",
            autoDetectTitle = "Auto-detect language",
            autoDetectSubtitle = "Detects any language, translates into the language selected above",
            directTranslationTitle = "Direct translation",
            directTranslationSubtitle = "Faster — audio to translation in one step",
            instructionsTitle = "How to use",
            instructionsBody = """
                • Voice: Hold the microphone, speak, then release. The app translates and plays the result aloud.
                • Photo: Tap Take photo or Upload photo to translate any visible text — signs, menus, labels, handwriting.
                • Paired mode: Pick two languages above the conversation. The app routes each turn to the opposite language automatically.
                • Auto-detect: Recognizes ~110 languages and translates into the app language. Useful when the other person's language is unknown.
                • Direct translation: Faster path that skips the transcription step.
                • Manual direction: In paired mode, shows two mics so you choose the target language. Locks direction per turn and may improve accuracy when you know who is speaking.
                • Faster vs Higher Accuracy: Faster uses a smaller, lower-latency model; Higher Accuracy uses a larger model with better translation quality.
                • Fully offline after first launch — no data leaves the device.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(Detected: $lang — not in your pair)" },
            appLanguageTitle = "App language",
            appLanguageSubtitle = "Drives the interface and is the target for auto-detect.",
            explicitDirectionTitle = "Manual direction",
            explicitDirectionSubtitle = "Show two mics in paired mode so you choose the target language. Locks direction per turn and may improve accuracy when you know who is speaking.",
            explicitDirectionDisabledSubtitle = "Unavailable in Auto-detect because any-language mode always translates into the app language."
        )
        Language.SPANISH -> UiStrings(
            title = "Traductor con IA",
            aboutButton = "Acerca",
            disclaimerButton = "Aviso",
            settingsButton = "Ajustes",
            clearButton = "Borrar",
            modelQuick = "Más rápido",
            modelHighQuality = "Mayor precisión",
            statusReady = "Listo",
            statusLoading = "Cargando…",
            statusListening = "Escuchando…",
            statusTranslating = "Traduciendo…",
            statusSpeaking = "Hablando…",
            statusNoVoice = "Listo (sin voz instalada)",
            micPermissionRequired = "Se necesita permiso del micrófono",
            loadingModels = "Cargando modelos…",
            holdToSpeak = "Mantén presionado para hablar",
            takePhotoButton = "Tomar foto",
            uploadPhotoButton = "Subir foto",
            emptyPlaceholder = { a, b -> "Habla en $a o $b.\nLa app traducirá al otro idioma." },
            disclaimerScreenTitle = "Antes de empezar",
            playAloudLabel = "Reproducir en voz alta:",
            beginButton = "Comenzar",
            aboutScreenTitle = "Acerca",
            aboutScreenSubtitle = "Completamente sin conexión, completamente privado",
            closeButton = "Cerrar",
            autoDetectTitle = "Detección automática",
            autoDetectSubtitle = "Detecta cualquier idioma, traduce al idioma seleccionado arriba",
            directTranslationTitle = "Traducción directa",
            directTranslationSubtitle = "Más rápida — audio a traducción en un paso",
            instructionsTitle = "Cómo usar",
            instructionsBody = """
                • Voz: Mantén presionado el micrófono, habla y suelta. La app traduce y reproduce el resultado en voz alta.
                • Foto: Toca Tomar foto o Subir foto para traducir cualquier texto visible: letreros, menús, etiquetas, escritura a mano.
                • Modo emparejado: Elige dos idiomas arriba. La app traduce cada turno automáticamente al idioma opuesto.
                • Detección automática: Reconoce ~110 idiomas y traduce al idioma de la app. Útil cuando no sabes qué idioma habla la otra persona.
                • Traducción directa: Más rápida; omite la transcripción.
                • Dirección manual: En modo emparejado, muestra dos micrófonos para que elijas el idioma de destino. Bloquea la dirección en cada turno y puede mejorar la precisión cuando sabes quién habla.
                • Más rápido vs Mayor precisión: Más rápido usa un modelo más pequeño y veloz; Mayor precisión usa uno más grande con mejor calidad de traducción.
                • Totalmente sin conexión tras la primera carga: ningún dato sale del dispositivo.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(Detectado: $lang — no está en tu par)" },
            appLanguageTitle = "Idioma de la app",
            appLanguageSubtitle = "Controla la interfaz y es el destino para la detección automática.",
            explicitDirectionTitle = "Dirección manual",
            explicitDirectionSubtitle = "Muestra dos micrófonos en modo emparejado para que elijas el idioma de destino. Bloquea la dirección en cada turno y puede mejorar la precisión cuando sabes quién habla.",
            explicitDirectionDisabledSubtitle = "No está disponible con detección automática porque el modo de cualquier idioma siempre traduce al idioma de la app."
        )
        Language.FRENCH -> UiStrings(
            title = "Traducteur IA",
            aboutButton = "À propos",
            disclaimerButton = "Avis",
            settingsButton = "Paramètres",
            clearButton = "Effacer",
            modelQuick = "Plus rapide",
            modelHighQuality = "Plus précis",
            statusReady = "Prêt",
            statusLoading = "Chargement…",
            statusListening = "Écoute…",
            statusTranslating = "Traduction…",
            statusSpeaking = "Lecture…",
            statusNoVoice = "Prêt (aucune voix installée)",
            micPermissionRequired = "Autorisation du microphone requise",
            loadingModels = "Chargement des modèles…",
            holdToSpeak = "Maintenir pour parler",
            takePhotoButton = "Prendre photo",
            uploadPhotoButton = "Importer photo",
            emptyPlaceholder = { a, b -> "Parlez en $a ou en $b.\nL'app traduira vers l'autre langue." },
            disclaimerScreenTitle = "Avant de commencer",
            playAloudLabel = "Lire à voix haute :",
            beginButton = "Commencer",
            aboutScreenTitle = "À propos",
            aboutScreenSubtitle = "Entièrement hors ligne, entièrement privé",
            closeButton = "Fermer",
            autoDetectTitle = "Détection automatique",
            autoDetectSubtitle = "Détecte toute langue, traduit dans la langue sélectionnée ci-dessus",
            directTranslationTitle = "Traduction directe",
            directTranslationSubtitle = "Plus rapide — audio à traduction en une étape",
            instructionsTitle = "Mode d'emploi",
            instructionsBody = """
                • Voix : Maintenez le micro, parlez, puis relâchez. L'app traduit et lit le résultat à voix haute.
                • Photo : Touchez Prendre photo ou Importer photo pour traduire tout texte visible — panneaux, menus, étiquettes, écriture manuscrite.
                • Mode jumelé : Choisissez deux langues au-dessus. L'app traduit chaque tour automatiquement vers l'autre langue.
                • Détection auto : Reconnaît ~110 langues et traduit dans la langue de l'app. Utile quand la langue de l'interlocuteur est inconnue.
                • Traduction directe : Plus rapide ; saute l'étape de transcription.
                • Direction manuelle : En mode jumelé, affiche deux micros pour choisir la langue cible. Verrouille la direction à chaque tour et peut améliorer la précision quand vous savez qui parle.
                • Plus rapide vs Plus précis : Plus rapide utilise un modèle plus petit et rapide ; Plus précis utilise un modèle plus grand avec une meilleure qualité.
                • Entièrement hors ligne après le premier lancement — aucune donnée ne quitte l'appareil.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(Détecté : $lang — pas dans votre paire)" },
            appLanguageTitle = "Langue de l'app",
            appLanguageSubtitle = "Définit l'interface et la cible de la détection automatique.",
            explicitDirectionTitle = "Direction manuelle",
            explicitDirectionSubtitle = "Affiche deux micros en mode jumelé pour choisir la langue cible. Verrouille la direction à chaque tour et peut améliorer la précision quand vous savez qui parle.",
            explicitDirectionDisabledSubtitle = "Indisponible avec la détection automatique, car le mode toute langue traduit toujours vers la langue de l'app."
        )
        Language.GERMAN -> UiStrings(
            title = "KI-Übersetzer",
            aboutButton = "Info",
            disclaimerButton = "Hinweis",
            settingsButton = "Einstellungen",
            clearButton = "Löschen",
            modelQuick = "Schneller",
            modelHighQuality = "Genauer",
            statusReady = "Bereit",
            statusLoading = "Lade…",
            statusListening = "Höre zu…",
            statusTranslating = "Übersetze…",
            statusSpeaking = "Spreche…",
            statusNoVoice = "Bereit (keine Stimme installiert)",
            micPermissionRequired = "Mikrofon-Berechtigung erforderlich",
            loadingModels = "Modelle werden geladen…",
            holdToSpeak = "Zum Sprechen gedrückt halten",
            takePhotoButton = "Foto aufnehmen",
            uploadPhotoButton = "Foto hochladen",
            emptyPlaceholder = { a, b -> "Sprechen Sie $a oder $b.\nDie App übersetzt in die andere Sprache." },
            disclaimerScreenTitle = "Bevor Sie beginnen",
            playAloudLabel = "Vorlesen:",
            beginButton = "Beginnen",
            aboutScreenTitle = "Info",
            aboutScreenSubtitle = "Vollständig offline, vollständig privat",
            closeButton = "Schließen",
            autoDetectTitle = "Auto-Erkennung",
            autoDetectSubtitle = "Erkennt jede Sprache, übersetzt in die oben gewählte Sprache",
            directTranslationTitle = "Direkte Übersetzung",
            directTranslationSubtitle = "Schneller — Audio direkt zur Übersetzung",
            instructionsTitle = "Bedienung",
            instructionsBody = """
                • Sprache: Mikrofon gedrückt halten, sprechen, loslassen. Die App übersetzt und liest das Ergebnis vor.
                • Foto: Tippen Sie auf Foto aufnehmen oder Foto hochladen, um sichtbaren Text zu übersetzen — Schilder, Menüs, Etiketten, Handschrift.
                • Paar-Modus: Wählen Sie oben zwei Sprachen. Die App übersetzt jede Aussage automatisch in die jeweils andere.
                • Auto-Erkennung: Erkennt ~110 Sprachen und übersetzt in die App-Sprache. Nützlich, wenn die Sprache des Gegenübers unbekannt ist.
                • Direkte Übersetzung: Schneller; überspringt die Transkription.
                • Manuelle Richtung: Im Paar-Modus werden zwei Mikrofone angezeigt, damit Sie die Zielsprache wählen. Die Richtung wird pro Runde festgelegt und kann die Genauigkeit verbessern, wenn Sie wissen, wer spricht.
                • Schneller vs Genauer: Schneller nutzt ein kleineres Modell mit niedriger Latenz; Genauer nutzt ein größeres Modell mit besserer Übersetzungsqualität.
                • Vollständig offline nach dem ersten Start — es verlassen keine Daten das Gerät.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(Erkannt: $lang — nicht in Ihrem Paar)" },
            appLanguageTitle = "App-Sprache",
            appLanguageSubtitle = "Steuert die Oberfläche und ist Ziel der automatischen Erkennung.",
            explicitDirectionTitle = "Manuelle Richtung",
            explicitDirectionSubtitle = "Zeigt im Paar-Modus zwei Mikrofone, damit Sie die Zielsprache wählen. Legt die Richtung pro Runde fest und kann die Genauigkeit verbessern, wenn Sie wissen, wer spricht.",
            explicitDirectionDisabledSubtitle = "Nicht verfügbar bei Auto-Erkennung, da der Modus für beliebige Sprachen immer in die App-Sprache übersetzt."
        )
        Language.PORTUGUESE -> UiStrings(
            title = "Tradutor com IA",
            aboutButton = "Sobre",
            disclaimerButton = "Aviso",
            settingsButton = "Configurações",
            clearButton = "Limpar",
            modelQuick = "Mais rápido",
            modelHighQuality = "Maior precisão",
            statusReady = "Pronto",
            statusLoading = "Carregando…",
            statusListening = "Ouvindo…",
            statusTranslating = "Traduzindo…",
            statusSpeaking = "Falando…",
            statusNoVoice = "Pronto (sem voz instalada)",
            micPermissionRequired = "Permissão do microfone necessária",
            loadingModels = "Carregando modelos…",
            holdToSpeak = "Mantenha pressionado para falar",
            takePhotoButton = "Tirar foto",
            uploadPhotoButton = "Enviar foto",
            emptyPlaceholder = { a, b -> "Fale em $a ou $b.\nO app traduzirá para o outro idioma." },
            disclaimerScreenTitle = "Antes de começar",
            playAloudLabel = "Ler em voz alta:",
            beginButton = "Começar",
            aboutScreenTitle = "Sobre",
            aboutScreenSubtitle = "Totalmente offline, totalmente privado",
            closeButton = "Fechar",
            autoDetectTitle = "Detecção automática",
            autoDetectSubtitle = "Detecta qualquer idioma, traduz para o idioma selecionado acima",
            directTranslationTitle = "Tradução direta",
            directTranslationSubtitle = "Mais rápido — áudio para tradução em um passo",
            instructionsTitle = "Como usar",
            instructionsBody = """
                • Voz: Mantenha pressionado o microfone, fale e solte. O app traduz e reproduz o resultado em voz alta.
                • Foto: Toque em Tirar foto ou Enviar foto para traduzir qualquer texto visível — placas, cardápios, etiquetas, manuscritos.
                • Modo pareado: Escolha dois idiomas acima. O app traduz cada turno automaticamente para o idioma oposto.
                • Detecção automática: Reconhece ~110 idiomas e traduz para o idioma do app. Útil quando o idioma do outro é desconhecido.
                • Tradução direta: Mais rápida; pula a transcrição.
                • Direção manual: No modo pareado, mostra dois microfones para você escolher o idioma de destino. Fixa a direção por turno e pode melhorar a precisão quando você sabe quem está falando.
                • Mais rápido vs Maior precisão: Mais rápido usa um modelo menor e mais rápido; Maior precisão usa um modelo maior com melhor qualidade.
                • Totalmente offline após a primeira execução — nenhum dado sai do dispositivo.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(Detectado: $lang — não está no seu par)" },
            appLanguageTitle = "Idioma do app",
            appLanguageSubtitle = "Controla a interface e é o destino da detecção automática.",
            explicitDirectionTitle = "Direção manual",
            explicitDirectionSubtitle = "Mostra dois microfones no modo pareado para você escolher o idioma de destino. Fixa a direção por turno e pode melhorar a precisão quando você sabe quem está falando.",
            explicitDirectionDisabledSubtitle = "Indisponível na detecção automática porque o modo de qualquer idioma sempre traduz para o idioma do app."
        )
        Language.ITALIAN -> UiStrings(
            title = "Traduttore IA",
            aboutButton = "Info",
            disclaimerButton = "Avviso",
            settingsButton = "Impostazioni",
            clearButton = "Cancella",
            modelQuick = "Più veloce",
            modelHighQuality = "Più preciso",
            statusReady = "Pronto",
            statusLoading = "Caricamento…",
            statusListening = "In ascolto…",
            statusTranslating = "Traduzione…",
            statusSpeaking = "In riproduzione…",
            statusNoVoice = "Pronto (nessuna voce installata)",
            micPermissionRequired = "Permesso microfono richiesto",
            loadingModels = "Caricamento modelli…",
            holdToSpeak = "Tieni premuto per parlare",
            takePhotoButton = "Scatta foto",
            uploadPhotoButton = "Carica foto",
            emptyPlaceholder = { a, b -> "Parla in $a o $b.\nL'app tradurrà nell'altra lingua." },
            disclaimerScreenTitle = "Prima di iniziare",
            playAloudLabel = "Leggi ad alta voce:",
            beginButton = "Inizia",
            aboutScreenTitle = "Info",
            aboutScreenSubtitle = "Completamente offline, completamente privato",
            closeButton = "Chiudi",
            autoDetectTitle = "Rilevamento automatico",
            autoDetectSubtitle = "Rileva qualsiasi lingua, traduce nella lingua selezionata sopra",
            directTranslationTitle = "Traduzione diretta",
            directTranslationSubtitle = "Più veloce — audio a traduzione in un passaggio",
            instructionsTitle = "Come si usa",
            instructionsBody = """
                • Voce: Tieni premuto il microfono, parla, poi rilascia. L'app traduce e riproduce il risultato ad alta voce.
                • Foto: Tocca Scatta foto o Carica foto per tradurre qualsiasi testo visibile — cartelli, menu, etichette, scritto a mano.
                • Modalità accoppiata: Scegli due lingue in alto. L'app traduce ogni turno automaticamente nell'altra lingua.
                • Rilevamento automatico: Riconosce ~110 lingue e traduce nella lingua dell'app. Utile quando la lingua dell'altra persona è sconosciuta.
                • Traduzione diretta: Più veloce; salta la trascrizione.
                • Direzione manuale: In modalità accoppiata mostra due microfoni per scegliere la lingua di destinazione. Blocca la direzione per ogni turno e può migliorare la precisione quando sai chi sta parlando.
                • Più veloce vs Più preciso: Più veloce usa un modello più piccolo e rapido; Più preciso usa un modello più grande con migliore qualità.
                • Completamente offline dopo il primo avvio — nessun dato lascia il dispositivo.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(Rilevato: $lang — non è nella tua coppia)" },
            appLanguageTitle = "Lingua dell'app",
            appLanguageSubtitle = "Imposta l'interfaccia e la lingua di destinazione del rilevamento automatico.",
            explicitDirectionTitle = "Direzione manuale",
            explicitDirectionSubtitle = "Mostra due microfoni in modalità accoppiata per scegliere la lingua di destinazione. Blocca la direzione per ogni turno e può migliorare la precisione quando sai chi sta parlando.",
            explicitDirectionDisabledSubtitle = "Non disponibile con il rilevamento automatico perché la modalità qualsiasi lingua traduce sempre nella lingua dell'app."
        )
        Language.CHINESE -> UiStrings(
            title = "AI 翻译器",
            aboutButton = "关于",
            disclaimerButton = "声明",
            settingsButton = "设置",
            clearButton = "清除",
            modelQuick = "更快",
            modelHighQuality = "更准确",
            statusReady = "就绪",
            statusLoading = "加载中…",
            statusListening = "聆听中…",
            statusTranslating = "翻译中…",
            statusSpeaking = "朗读中…",
            statusNoVoice = "就绪（未安装语音）",
            micPermissionRequired = "需要麦克风权限",
            loadingModels = "正在加载模型…",
            holdToSpeak = "按住说话",
            takePhotoButton = "拍照",
            uploadPhotoButton = "上传照片",
            emptyPlaceholder = { a, b -> "请说 $a 或 $b。\n应用将翻译为另一种语言。" },
            disclaimerScreenTitle = "开始之前",
            playAloudLabel = "朗读：",
            beginButton = "开始",
            aboutScreenTitle = "关于",
            aboutScreenSubtitle = "完全离线，完全私密",
            closeButton = "关闭",
            autoDetectTitle = "自动检测",
            autoDetectSubtitle = "检测任何语言，翻译为上面选定的语言",
            directTranslationTitle = "直接翻译",
            directTranslationSubtitle = "更快——音频一步直达翻译",
            instructionsTitle = "使用方法",
            instructionsBody = """
                • 语音：按住麦克风说话，松开即可。应用会翻译并大声朗读结果。
                • 照片：点击"拍照"或"上传照片"，可翻译任何可见文本——标志、菜单、标签、手写文字。
                • 配对模式：在上方选择两种语言。应用会自动将每次发言翻译到另一种语言。
                • 自动检测：识别约 110 种语言，并翻译为应用语言。在不知道对方语言时很有用。
                • 直接翻译：更快，跳过转录步骤。
                • 手动方向：在配对模式下显示两个麦克风，让你选择目标语言。每轮固定翻译方向；当你知道是谁在说话时，可能提高准确性。
                • 更快 vs 更准确："更快"使用较小、低延迟的模型；"更准确"使用更大、翻译质量更好的模型。
                • 首次启动后完全离线——没有数据离开设备。
            """.trimIndent(),
            outOfPairWarning = { lang -> "(检测到: $lang — 不在您的语言对中)" },
            appLanguageTitle = "应用语言",
            appLanguageSubtitle = "决定界面语言以及自动检测的翻译目标语言。",
            explicitDirectionTitle = "手动方向",
            explicitDirectionSubtitle = "在配对模式下显示两个麦克风，让你选择目标语言。每轮固定翻译方向；当你知道是谁在说话时，可能提高准确性。",
            explicitDirectionDisabledSubtitle = "自动检测时不可用，因为任意语言模式始终翻译为应用语言。"
        )
        Language.JAPANESE -> UiStrings(
            title = "AI翻訳",
            aboutButton = "アプリ情報",
            disclaimerButton = "注意事項",
            settingsButton = "設定",
            clearButton = "クリア",
            modelQuick = "高速",
            modelHighQuality = "高精度",
            statusReady = "準備完了",
            statusLoading = "読み込み中…",
            statusListening = "聞き取り中…",
            statusTranslating = "翻訳中…",
            statusSpeaking = "読み上げ中…",
            statusNoVoice = "準備完了（音声未インストール）",
            micPermissionRequired = "マイクの権限が必要です",
            loadingModels = "モデルを読み込み中…",
            holdToSpeak = "押しながら話す",
            takePhotoButton = "写真を撮る",
            uploadPhotoButton = "写真を選ぶ",
            emptyPlaceholder = { a, b -> "$a または $b で話してください。\nアプリがもう一方の言語に翻訳します。" },
            disclaimerScreenTitle = "はじめる前に",
            playAloudLabel = "読み上げ:",
            beginButton = "はじめる",
            aboutScreenTitle = "アプリ情報",
            aboutScreenSubtitle = "完全オフライン、完全プライベート",
            closeButton = "閉じる",
            autoDetectTitle = "自動検出",
            autoDetectSubtitle = "あらゆる言語を検出し、上で選んだ言語に翻訳",
            directTranslationTitle = "直接翻訳",
            directTranslationSubtitle = "高速 — 音声から翻訳まで一段階",
            instructionsTitle = "使い方",
            instructionsBody = """
                • 音声：マイクを押しながら話し、離します。アプリが翻訳し、結果を読み上げます。
                • 写真：「写真を撮る」または「写真を選ぶ」をタップし、看板・メニュー・ラベル・手書きなどの文字を翻訳します。
                • ペアモード：上で2つの言語を選択。アプリが各発話を自動的にもう一方の言語へ翻訳します。
                • 自動検出：約110言語を認識し、アプリの言語に翻訳。相手の言語が不明なときに便利。
                • 直接翻訳：高速。文字起こしを省略します。
                • 手動方向：ペアモードで2つのマイクを表示し、翻訳先の言語を選べます。各ターンの方向を固定するため、誰が話しているか分かっている場合は精度が上がることがあります。
                • 高速 vs 高精度:「高速」は小型・低遅延モデル、「高精度」は大型・高品質モデル。
                • 初回起動後は完全オフライン — データは端末外に出ません。
            """.trimIndent(),
            outOfPairWarning = { lang -> "(検出: $lang — ペアに含まれていません)" },
            appLanguageTitle = "アプリの言語",
            appLanguageSubtitle = "画面の言語と、自動検出時の翻訳先言語を決めます。",
            explicitDirectionTitle = "手動方向",
            explicitDirectionSubtitle = "ペアモードで2つのマイクを表示し、翻訳先の言語を選べます。各ターンの方向を固定するため、誰が話しているか分かっている場合は精度が上がることがあります。",
            explicitDirectionDisabledSubtitle = "自動検出では使用できません。任意言語モードは常にアプリの言語へ翻訳します。"
        )
        Language.KOREAN -> UiStrings(
            title = "AI 번역기",
            aboutButton = "정보",
            disclaimerButton = "주의사항",
            settingsButton = "설정",
            clearButton = "지우기",
            modelQuick = "더 빠름",
            modelHighQuality = "더 정확",
            statusReady = "준비됨",
            statusLoading = "로딩 중…",
            statusListening = "듣는 중…",
            statusTranslating = "번역 중…",
            statusSpeaking = "말하는 중…",
            statusNoVoice = "준비됨 (음성 미설치)",
            micPermissionRequired = "마이크 권한이 필요합니다",
            loadingModels = "모델 로딩 중…",
            holdToSpeak = "누르고 말하기",
            takePhotoButton = "사진 찍기",
            uploadPhotoButton = "사진 업로드",
            emptyPlaceholder = { a, b -> "$a 또는 ${b}로 말씀해 주세요.\n앱이 다른 언어로 번역합니다." },
            disclaimerScreenTitle = "시작하기 전에",
            playAloudLabel = "소리 내어 읽기:",
            beginButton = "시작",
            aboutScreenTitle = "정보",
            aboutScreenSubtitle = "완전 오프라인, 완전 비공개",
            closeButton = "닫기",
            autoDetectTitle = "자동 감지",
            autoDetectSubtitle = "모든 언어를 감지하여 위에서 선택한 언어로 번역",
            directTranslationTitle = "직접 번역",
            directTranslationSubtitle = "더 빠름 — 오디오를 한 번에 번역",
            instructionsTitle = "사용 방법",
            instructionsBody = """
                • 음성: 마이크를 누른 채 말한 뒤 손을 떼세요. 앱이 번역하여 결과를 소리 내어 읽어줍니다.
                • 사진: 사진 찍기 또는 사진 업로드를 눌러 보이는 모든 텍스트(간판, 메뉴, 라벨, 손글씨)를 번역하세요.
                • 페어 모드: 위에서 두 언어를 선택하세요. 앱이 각 발화를 자동으로 반대 언어로 번역합니다.
                • 자동 감지: 약 110개 언어를 인식하며 앱 언어로 번역합니다. 상대방의 언어를 모를 때 유용합니다.
                • 직접 번역: 더 빠르며 전사 단계를 건너뜁니다.
                • 수동 방향: 페어 모드에서 마이크 두 개를 표시해 대상 언어를 직접 선택합니다. 각 턴의 방향을 고정하므로 누가 말하는지 알 때 정확도가 좋아질 수 있습니다.
                • 더 빠름 vs 더 정확: "더 빠름"은 작고 빠른 모델, "더 정확"은 더 크고 번역 품질이 좋은 모델입니다.
                • 첫 실행 이후 완전 오프라인 — 어떠한 데이터도 기기를 떠나지 않습니다.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(감지: $lang — 페어에 없음)" },
            appLanguageTitle = "앱 언어",
            appLanguageSubtitle = "인터페이스 언어이자 자동 감지 시 번역 대상 언어입니다.",
            explicitDirectionTitle = "수동 방향",
            explicitDirectionSubtitle = "페어 모드에서 마이크 두 개를 표시해 대상 언어를 직접 선택합니다. 각 턴의 방향을 고정하므로 누가 말하는지 알 때 정확도가 좋아질 수 있습니다.",
            explicitDirectionDisabledSubtitle = "자동 감지에서는 사용할 수 없습니다. 모든 언어 모드는 항상 앱 언어로 번역합니다."
        )
        Language.HINDI -> UiStrings(
            title = "एआई अनुवादक",
            aboutButton = "परिचय",
            disclaimerButton = "सूचना",
            settingsButton = "सेटिंग्स",
            clearButton = "साफ़",
            modelQuick = "तेज़",
            modelHighQuality = "अधिक सटीक",
            statusReady = "तैयार",
            statusLoading = "लोड हो रहा है…",
            statusListening = "सुन रहा है…",
            statusTranslating = "अनुवाद हो रहा है…",
            statusSpeaking = "बोल रहा है…",
            statusNoVoice = "तैयार (कोई आवाज़ इंस्टॉल नहीं)",
            micPermissionRequired = "माइक्रोफ़ोन की अनुमति आवश्यक है",
            loadingModels = "मॉडल लोड हो रहे हैं…",
            holdToSpeak = "बोलने के लिए दबाए रखें",
            takePhotoButton = "फोटो लें",
            uploadPhotoButton = "फोटो अपलोड",
            emptyPlaceholder = { a, b -> "$a या $b में बोलें।\nऐप दूसरी भाषा में अनुवाद करेगा।" },
            disclaimerScreenTitle = "शुरू करने से पहले",
            playAloudLabel = "ज़ोर से पढ़ें:",
            beginButton = "शुरू करें",
            aboutScreenTitle = "परिचय",
            aboutScreenSubtitle = "पूरी तरह ऑफ़लाइन, पूरी तरह निजी",
            closeButton = "बंद करें",
            autoDetectTitle = "स्वचालित पहचान",
            autoDetectSubtitle = "किसी भी भाषा को पहचानता है, ऊपर चुनी गई भाषा में अनुवाद करता है",
            directTranslationTitle = "सीधा अनुवाद",
            directTranslationSubtitle = "तेज़ — एक चरण में ऑडियो से अनुवाद",
            instructionsTitle = "उपयोग कैसे करें",
            instructionsBody = """
                • आवाज़: माइक्रोफ़ोन दबाए रखें, बोलें, फिर छोड़ दें। ऐप अनुवाद करेगा और परिणाम ज़ोर से पढ़कर सुनाएगा।
                • फ़ोटो: फोटो लें या फोटो अपलोड पर टैप करके किसी भी दिखाई देने वाले पाठ का अनुवाद करें — संकेत, मेनू, लेबल, हस्तलिखित।
                • युग्मित मोड: ऊपर दो भाषाएं चुनें। ऐप हर बारी को स्वतः विपरीत भाषा में अनुवाद करेगा।
                • स्वचालित पहचान: ~110 भाषाओं को पहचानता है और ऐप की भाषा में अनुवाद करता है। जब सामने वाले की भाषा अज्ञात हो, तब उपयोगी।
                • सीधा अनुवाद: तेज़ — लिप्यंतरण चरण को छोड़ देता है।
                • मैनुअल दिशा: युग्मित मोड में दो माइक दिखाता है ताकि आप लक्ष्य भाषा चुन सकें। हर बारी की दिशा तय रहती है और जब आपको पता हो कि कौन बोल रहा है, तो सटीकता बेहतर हो सकती है।
                • तेज़ vs अधिक सटीक: "तेज़" छोटा, कम विलंब वाला मॉडल उपयोग करता है; "अधिक सटीक" बड़ा मॉडल बेहतर अनुवाद गुणवत्ता के साथ।
                • पहली बार चलाने के बाद पूरी तरह ऑफ़लाइन — कोई डेटा डिवाइस से बाहर नहीं जाता।
            """.trimIndent(),
            outOfPairWarning = { lang -> "(पहचाना गया: $lang — आपकी जोड़ी में नहीं)" },
            appLanguageTitle = "ऐप की भाषा",
            appLanguageSubtitle = "इंटरफ़ेस की भाषा और स्वतः पहचान का अनुवाद लक्ष्य।",
            explicitDirectionTitle = "मैनुअल दिशा",
            explicitDirectionSubtitle = "युग्मित मोड में दो माइक दिखाता है ताकि आप लक्ष्य भाषा चुन सकें। हर बारी की दिशा तय रहती है और जब आपको पता हो कि कौन बोल रहा है, तो सटीकता बेहतर हो सकती है।",
            explicitDirectionDisabledSubtitle = "स्वचालित पहचान में उपलब्ध नहीं, क्योंकि कोई भी भाषा मोड हमेशा ऐप की भाषा में अनुवाद करता है।"
        )
        Language.ARABIC -> UiStrings(
            title = "مترجم بالذكاء الاصطناعي",
            aboutButton = "حول",
            disclaimerButton = "إشعار",
            settingsButton = "الإعدادات",
            clearButton = "مسح",
            modelQuick = "أسرع",
            modelHighQuality = "أعلى دقة",
            statusReady = "جاهز",
            statusLoading = "جارٍ التحميل…",
            statusListening = "يستمع…",
            statusTranslating = "جارٍ الترجمة…",
            statusSpeaking = "يتحدث…",
            statusNoVoice = "جاهز (لا يوجد صوت مثبت)",
            micPermissionRequired = "يتطلب إذن الميكروفون",
            loadingModels = "جارٍ تحميل النماذج…",
            holdToSpeak = "اضغط مطولاً للتحدث",
            takePhotoButton = "التقاط صورة",
            uploadPhotoButton = "تحميل صورة",
            emptyPlaceholder = { a, b -> "تحدث بـ$a أو $b.\nسيقوم التطبيق بالترجمة إلى اللغة الأخرى." },
            disclaimerScreenTitle = "قبل البدء",
            playAloudLabel = "قراءة بصوت عالٍ:",
            beginButton = "ابدأ",
            aboutScreenTitle = "حول",
            aboutScreenSubtitle = "دون اتصال بالكامل، خاص بالكامل",
            closeButton = "إغلاق",
            autoDetectTitle = "كشف تلقائي للغة",
            autoDetectSubtitle = "يكتشف أي لغة ويترجم إلى اللغة المحددة أعلاه",
            directTranslationTitle = "ترجمة مباشرة",
            directTranslationSubtitle = "أسرع — من الصوت إلى الترجمة في خطوة واحدة",
            instructionsTitle = "طريقة الاستخدام",
            instructionsBody = """
                • الصوت: اضغط مطولاً على الميكروفون، تحدث، ثم اترك الزر. يقوم التطبيق بالترجمة وقراءة النتيجة بصوت عالٍ.
                • الصورة: اضغط على التقاط صورة أو تحميل صورة لترجمة أي نص مرئي — لافتات، قوائم، ملصقات، خط اليد.
                • وضع الاقتران: اختر لغتين في الأعلى. يترجم التطبيق كل دور تلقائيًا إلى اللغة المقابلة.
                • الكشف التلقائي: يتعرف على ~110 لغة ويُترجم إلى لغة التطبيق. مفيد عندما تكون لغة الطرف الآخر غير معروفة.
                • الترجمة المباشرة: أسرع — تتخطى خطوة النسخ.
                • الاتجاه اليدوي: في وضع الاقتران، يعرض ميكروفونين لتختار لغة الإخراج. يثبت الاتجاه لكل دور وقد يحسّن الدقة عندما تعرف من يتحدث.
                • أسرع مقابل أعلى دقة: "أسرع" يستخدم نموذجًا أصغر بزمن استجابة أقل؛ "أعلى دقة" يستخدم نموذجًا أكبر بجودة ترجمة أفضل.
                • دون اتصال تمامًا بعد التشغيل الأول — لا تغادر أي بيانات الجهاز.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(تم الكشف: $lang — ليست في زوجك)" },
            appLanguageTitle = "لغة التطبيق",
            appLanguageSubtitle = "تتحكم في الواجهة وتُحدِّد لغة الترجمة عند الكشف التلقائي.",
            explicitDirectionTitle = "الاتجاه اليدوي",
            explicitDirectionSubtitle = "في وضع الاقتران، يعرض ميكروفونين لتختار لغة الإخراج. يثبت الاتجاه لكل دور وقد يحسّن الدقة عندما تعرف من يتحدث.",
            explicitDirectionDisabledSubtitle = "غير متاح مع الكشف التلقائي، لأن وضع أي لغة يترجم دائمًا إلى لغة التطبيق."
        )
        Language.RUSSIAN -> UiStrings(
            title = "ИИ-переводчик",
            aboutButton = "О приложении",
            disclaimerButton = "Примечание",
            settingsButton = "Настройки",
            clearButton = "Очистить",
            modelQuick = "Быстрее",
            modelHighQuality = "Точнее",
            statusReady = "Готово",
            statusLoading = "Загрузка…",
            statusListening = "Слушаю…",
            statusTranslating = "Перевод…",
            statusSpeaking = "Говорю…",
            statusNoVoice = "Готово (голос не установлен)",
            micPermissionRequired = "Требуется разрешение на микрофон",
            loadingModels = "Загрузка моделей…",
            holdToSpeak = "Удерживайте, чтобы говорить",
            takePhotoButton = "Сделать фото",
            uploadPhotoButton = "Загрузить фото",
            emptyPlaceholder = { a, b -> "Говорите на $a или $b.\nПриложение переведёт на другой язык." },
            disclaimerScreenTitle = "Перед началом",
            playAloudLabel = "Прочитать вслух:",
            beginButton = "Начать",
            aboutScreenTitle = "О приложении",
            aboutScreenSubtitle = "Полностью офлайн, полностью приватно",
            closeButton = "Закрыть",
            autoDetectTitle = "Автоопределение языка",
            autoDetectSubtitle = "Определяет любой язык, переводит на выбранный выше",
            directTranslationTitle = "Прямой перевод",
            directTranslationSubtitle = "Быстрее — звук в перевод за один шаг",
            instructionsTitle = "Как пользоваться",
            instructionsBody = """
                • Голос: удерживайте микрофон, говорите, затем отпустите. Приложение переведёт и зачитает результат вслух.
                • Фото: нажмите «Сделать фото» или «Загрузить фото», чтобы перевести любой видимый текст — вывески, меню, этикетки, рукопись.
                • Парный режим: выберите два языка выше. Приложение автоматически переводит каждую реплику в противоположный язык.
                • Автоопределение: распознаёт ~110 языков и переводит на язык приложения. Полезно, когда язык собеседника неизвестен.
                • Прямой перевод: быстрее, пропускает этап транскрибирования.
                • Ручное направление: в парном режиме показывает два микрофона, чтобы вы выбрали язык перевода. Направление фиксируется для каждой реплики и может повысить точность, когда вы знаете, кто говорит.
                • Быстрее vs Точнее: «Быстрее» — меньшая, быстрая модель; «Точнее» — большая модель с лучшим качеством перевода.
                • Полностью офлайн после первого запуска — никакие данные не покидают устройство.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(Определено: $lang — не в вашей паре)" },
            appLanguageTitle = "Язык приложения",
            appLanguageSubtitle = "Управляет интерфейсом и языком перевода при автоопределении.",
            explicitDirectionTitle = "Ручное направление",
            explicitDirectionSubtitle = "В парном режиме показывает два микрофона, чтобы вы выбрали язык перевода. Направление фиксируется для каждой реплики и может повысить точность, когда вы знаете, кто говорит.",
            explicitDirectionDisabledSubtitle = "Недоступно при автоопределении, потому что режим любого языка всегда переводит на язык приложения."
        )
        Language.VIETNAMESE -> UiStrings(
            title = "Dịch AI",
            aboutButton = "Giới thiệu",
            disclaimerButton = "Lưu ý",
            settingsButton = "Cài đặt",
            clearButton = "Xóa",
            modelQuick = "Nhanh hơn",
            modelHighQuality = "Chính xác hơn",
            statusReady = "Sẵn sàng",
            statusLoading = "Đang tải…",
            statusListening = "Đang nghe…",
            statusTranslating = "Đang dịch…",
            statusSpeaking = "Đang đọc…",
            statusNoVoice = "Sẵn sàng (chưa cài giọng nói)",
            micPermissionRequired = "Yêu cầu quyền truy cập micrô",
            loadingModels = "Đang tải mô hình…",
            holdToSpeak = "Giữ để nói",
            takePhotoButton = "Chụp ảnh",
            uploadPhotoButton = "Tải ảnh lên",
            emptyPlaceholder = { a, b -> "Hãy nói bằng $a hoặc $b.\nỨng dụng sẽ dịch sang ngôn ngữ còn lại." },
            disclaimerScreenTitle = "Trước khi bắt đầu",
            playAloudLabel = "Đọc thành tiếng:",
            beginButton = "Bắt đầu",
            aboutScreenTitle = "Giới thiệu",
            aboutScreenSubtitle = "Hoàn toàn ngoại tuyến, hoàn toàn riêng tư",
            closeButton = "Đóng",
            autoDetectTitle = "Tự động phát hiện ngôn ngữ",
            autoDetectSubtitle = "Phát hiện mọi ngôn ngữ, dịch sang ngôn ngữ đã chọn ở trên",
            directTranslationTitle = "Dịch trực tiếp",
            directTranslationSubtitle = "Nhanh hơn — từ âm thanh đến bản dịch trong một bước",
            instructionsTitle = "Cách sử dụng",
            instructionsBody = """
                • Giọng nói: Giữ micrô, nói, rồi thả ra. Ứng dụng sẽ dịch và đọc to kết quả.
                • Ảnh: Chạm Chụp ảnh hoặc Tải ảnh lên để dịch mọi văn bản hiển thị — biển báo, thực đơn, nhãn, chữ viết tay.
                • Chế độ ghép cặp: Chọn hai ngôn ngữ ở trên. Ứng dụng tự động dịch mỗi lượt sang ngôn ngữ còn lại.
                • Tự động phát hiện: Nhận diện ~110 ngôn ngữ và dịch sang ngôn ngữ ứng dụng. Hữu ích khi không biết ngôn ngữ của người đối diện.
                • Dịch trực tiếp: Nhanh hơn, bỏ qua bước phiên âm.
                • Hướng thủ công: Ở chế độ ghép cặp, hiển thị hai micrô để bạn chọn ngôn ngữ đích. Khóa hướng cho từng lượt và có thể cải thiện độ chính xác khi bạn biết ai đang nói.
                • Nhanh hơn vs Chính xác hơn: "Nhanh hơn" dùng mô hình nhỏ, độ trễ thấp; "Chính xác hơn" dùng mô hình lớn với chất lượng dịch tốt hơn.
                • Hoàn toàn ngoại tuyến sau lần khởi chạy đầu — không có dữ liệu nào rời khỏi thiết bị.
            """.trimIndent(),
            outOfPairWarning = { lang -> "(Phát hiện: $lang — không có trong cặp của bạn)" },
            appLanguageTitle = "Ngôn ngữ ứng dụng",
            appLanguageSubtitle = "Điều khiển giao diện và là ngôn ngữ đích khi tự động phát hiện.",
            explicitDirectionTitle = "Hướng thủ công",
            explicitDirectionSubtitle = "Ở chế độ ghép cặp, hiển thị hai micrô để bạn chọn ngôn ngữ đích. Khóa hướng cho từng lượt và có thể cải thiện độ chính xác khi bạn biết ai đang nói.",
            explicitDirectionDisabledSubtitle = "Không khả dụng khi tự động phát hiện, vì chế độ mọi ngôn ngữ luôn dịch sang ngôn ngữ ứng dụng."
        )
    }
}
