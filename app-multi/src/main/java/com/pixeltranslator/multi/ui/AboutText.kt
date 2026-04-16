package com.pixeltranslator.multi.ui

/**
 * Localized body text for the About screen. Keyed on the user's Language A
 * selection so operators see the offline/privacy explainer in their own
 * language. Falls back to English if a translation is missing.
 *
 * The content is intentionally short: four bullets covering the "nothing
 * leaves the device" promise, a one-paragraph how-it-works, and a closing
 * "privacy by architecture" tagline. Keeping it brief made translating it
 * into 13 languages feasible.
 */
object AboutText {
    const val ENGLISH: String =
        "This translator runs entirely on your phone. Nothing you say goes to the internet.\n\n" +
        "• No internet is used or required. Airplane mode works exactly the same.\n" +
        "• Your voice is never recorded, stored, or transmitted.\n" +
        "• Transcription and translation text stay only on this device.\n" +
        "• No analytics, no accounts, no cloud. The app has no permission to reach the network.\n\n" +
        "Speech recognition, translation, and voice output all run on the phone using Google's on-device Gemma 4 AI model. The model is loaded once from local storage; no network connection is ever made."

    private val byLanguage: Map<Language, String> = mapOf(
        Language.ENGLISH to ENGLISH,

        Language.SPANISH to
            "Este traductor funciona completamente en tu teléfono. Nada de lo que dices va a internet.\n\n" +
            "• No se usa ni se requiere internet. El modo avión funciona igual.\n" +
            "• Tu voz nunca se graba, almacena ni transmite.\n" +
            "• El texto de transcripción y traducción permanece solo en este dispositivo.\n" +
            "• Sin análisis, sin cuentas, sin nube. La app no tiene permiso para acceder a la red.\n\n" +
            "El reconocimiento de voz, la traducción y la salida de voz se ejecutan en el teléfono usando el modelo de IA Gemma 4 de Google en el dispositivo. El modelo se carga una vez desde el almacenamiento local; nunca se establece una conexión de red.",

        Language.FRENCH to
            "Ce traducteur fonctionne entièrement sur votre téléphone. Rien de ce que vous dites ne va sur internet.\n\n" +
            "• Aucun internet n'est utilisé ni requis. Le mode avion fonctionne exactement de la même manière.\n" +
            "• Votre voix n'est jamais enregistrée, stockée ni transmise.\n" +
            "• Le texte de transcription et de traduction reste uniquement sur cet appareil.\n" +
            "• Aucune analyse, aucun compte, aucun cloud. L'application n'a aucune autorisation réseau.\n\n" +
            "La reconnaissance vocale, la traduction et la sortie vocale s'exécutent toutes sur le téléphone à l'aide du modèle d'IA Gemma 4 de Google sur l'appareil. Le modèle est chargé une fois depuis le stockage local ; aucune connexion réseau n'est jamais établie.",

        Language.GERMAN to
            "Dieser Übersetzer läuft vollständig auf Ihrem Telefon. Nichts, was Sie sagen, geht ins Internet.\n\n" +
            "• Es wird kein Internet verwendet oder benötigt. Flugmodus funktioniert identisch.\n" +
            "• Ihre Stimme wird niemals aufgezeichnet, gespeichert oder übertragen.\n" +
            "• Transkriptions- und Übersetzungstext bleiben ausschließlich auf diesem Gerät.\n" +
            "• Keine Analyse, keine Konten, keine Cloud. Die App hat überhaupt keine Netzwerkberechtigung.\n\n" +
            "Spracherkennung, Übersetzung und Sprachausgabe laufen alle auf dem Telefon mit Googles On-Device-KI-Modell Gemma 4. Das Modell wird einmal aus dem lokalen Speicher geladen; es wird nie eine Netzwerkverbindung hergestellt.",

        Language.PORTUGUESE to
            "Este tradutor funciona inteiramente no seu telefone. Nada do que você diz vai para a internet.\n\n" +
            "• Não é usada nem necessária conexão com a internet. O modo avião funciona de forma idêntica.\n" +
            "• Sua voz nunca é gravada, armazenada ou transmitida.\n" +
            "• O texto de transcrição e tradução permanece apenas neste dispositivo.\n" +
            "• Sem análises, sem contas, sem nuvem. O app não tem permissão de rede.\n\n" +
            "O reconhecimento de voz, a tradução e a saída de voz são executados no telefone usando o modelo de IA Gemma 4 do Google no dispositivo. O modelo é carregado uma vez do armazenamento local; nenhuma conexão de rede é feita.",

        Language.ITALIAN to
            "Questo traduttore funziona interamente sul tuo telefono. Nulla di ciò che dici va su internet.\n\n" +
            "• Non viene usata né richiesta alcuna connessione internet. La modalità aereo funziona identicamente.\n" +
            "• La tua voce non viene mai registrata, memorizzata o trasmessa.\n" +
            "• Il testo di trascrizione e traduzione rimane solo su questo dispositivo.\n" +
            "• Nessuna analisi, nessun account, nessun cloud. L'app non ha alcun permesso di rete.\n\n" +
            "Il riconoscimento vocale, la traduzione e l'output vocale vengono tutti eseguiti sul telefono utilizzando il modello di IA Gemma 4 di Google sul dispositivo. Il modello viene caricato una volta dallo storage locale; non viene mai effettuata alcuna connessione di rete.",

        Language.CHINESE to
            "此翻译器完全在您的手机上运行。您所说的任何内容都不会上传到互联网。\n\n" +
            "• 不使用也不需要互联网。飞行模式下完全相同地工作。\n" +
            "• 您的声音从不被录制、存储或传输。\n" +
            "• 转录和翻译文本仅保留在此设备上。\n" +
            "• 无分析，无账户，无云端。应用完全没有网络权限。\n\n" +
            "语音识别、翻译和语音输出都使用 Google 的设备端 Gemma 4 AI 模型在手机上运行。模型从本地存储加载一次；从不建立网络连接。",

        Language.JAPANESE to
            "この翻訳アプリはすべてお使いの端末上で動作します。話した内容はインターネットに送信されません。\n\n" +
            "• インターネットは使用も必要もされません。機内モードでも同じように動作します。\n" +
            "• あなたの声は録音、保存、送信されることはありません。\n" +
            "• 文字起こしと翻訳テキストはこの端末にのみ保存されます。\n" +
            "• 分析なし、アカウントなし、クラウドなし。アプリにネットワーク権限はありません。\n\n" +
            "音声認識、翻訳、音声出力はすべて、Googleのオンデバイス Gemma 4 AIモデルを使用して端末上で実行されます。モデルはローカルストレージから一度だけ読み込まれ、ネットワーク接続は決して行われません。",

        Language.KOREAN to
            "이 번역기는 완전히 사용자의 전화에서 실행됩니다. 말한 내용은 인터넷으로 전송되지 않습니다.\n\n" +
            "• 인터넷이 사용되거나 필요하지 않습니다. 비행기 모드에서도 동일하게 작동합니다.\n" +
            "• 음성은 결코 녹음, 저장, 전송되지 않습니다.\n" +
            "• 전사 및 번역 텍스트는 이 기기에만 남습니다.\n" +
            "• 분석, 계정, 클라우드 모두 없음. 앱에는 네트워크 권한이 없습니다.\n\n" +
            "음성 인식, 번역, 음성 출력은 모두 Google의 온디바이스 Gemma 4 AI 모델을 사용하여 전화에서 실행됩니다. 모델은 로컬 저장소에서 한 번만 로드되며, 네트워크 연결은 결코 이루어지지 않습니다.",

        Language.HINDI to
            "यह अनुवादक पूरी तरह से आपके फ़ोन पर चलता है। आप जो कुछ भी कहते हैं वह इंटरनेट पर नहीं जाता।\n\n" +
            "• इंटरनेट का उपयोग नहीं किया जाता और न ही आवश्यक है। हवाई जहाज़ मोड में भी समान रूप से काम करता है।\n" +
            "• आपकी आवाज़ कभी रिकॉर्ड, संग्रहीत या प्रसारित नहीं होती।\n" +
            "• ट्रांसक्रिप्शन और अनुवाद टेक्स्ट केवल इस डिवाइस पर ही रहता है।\n" +
            "• कोई विश्लेषण नहीं, कोई खाता नहीं, कोई क्लाउड नहीं। ऐप के पास कोई नेटवर्क अनुमति नहीं है।\n\n" +
            "ध्वनि पहचान, अनुवाद और ध्वनि आउटपुट सभी Google के ऑन-डिवाइस Gemma 4 AI मॉडल का उपयोग करके फ़ोन पर चलते हैं। मॉडल एक बार स्थानीय संग्रहण से लोड होता है; कभी भी नेटवर्क कनेक्शन नहीं बनाया जाता।",

        Language.ARABIC to
            "يعمل هذا المترجم بالكامل على هاتفك. لا يذهب أي شيء تقوله إلى الإنترنت.\n\n" +
            "• لا يتم استخدام الإنترنت أو الحاجة إليه. يعمل وضع الطيران بنفس الطريقة تماماً.\n" +
            "• لا يتم تسجيل صوتك أو تخزينه أو إرساله أبداً.\n" +
            "• يبقى نص النسخ والترجمة على هذا الجهاز فقط.\n" +
            "• لا تحليلات، لا حسابات، لا سحابة. لا يمتلك التطبيق أي إذن للشبكة.\n\n" +
            "يتم تشغيل التعرف على الكلام والترجمة وإخراج الصوت كلها على الهاتف باستخدام نموذج Google Gemma 4 للذكاء الاصطناعي على الجهاز. يتم تحميل النموذج مرة واحدة من التخزين المحلي؛ لا يتم إجراء أي اتصال بالشبكة أبداً.",

        Language.RUSSIAN to
            "Этот переводчик работает полностью на вашем телефоне. Ничто из сказанного вами не уходит в интернет.\n\n" +
            "• Интернет не используется и не требуется. Режим полёта работает идентично.\n" +
            "• Ваш голос никогда не записывается, не сохраняется и не передаётся.\n" +
            "• Текст транскрипции и перевода остаётся только на этом устройстве.\n" +
            "• Никакой аналитики, никаких учётных записей, никакого облака. У приложения нет разрешения на доступ к сети.\n\n" +
            "Распознавание речи, перевод и синтез речи — всё выполняется на телефоне с использованием модели ИИ Gemma 4 от Google на устройстве. Модель загружается один раз из локального хранилища; сетевое соединение никогда не устанавливается.",

        Language.VIETNAMESE to
            "Trình dịch này chạy hoàn toàn trên điện thoại của bạn. Không có gì bạn nói được gửi lên internet.\n\n" +
            "• Không sử dụng và không cần internet. Chế độ máy bay hoạt động y hệt.\n" +
            "• Giọng nói của bạn không bao giờ được ghi âm, lưu trữ hay truyền đi.\n" +
            "• Văn bản phiên âm và bản dịch chỉ lưu trên thiết bị này.\n" +
            "• Không phân tích, không tài khoản, không đám mây. Ứng dụng không có quyền truy cập mạng.\n\n" +
            "Nhận dạng giọng nói, dịch thuật và đầu ra giọng nói đều chạy trên điện thoại sử dụng mô hình AI Gemma 4 của Google trên thiết bị. Mô hình được tải một lần từ bộ nhớ cục bộ; không bao giờ thực hiện kết nối mạng."
    )

    fun textFor(language: Language): String = byLanguage[language] ?: ENGLISH
}
