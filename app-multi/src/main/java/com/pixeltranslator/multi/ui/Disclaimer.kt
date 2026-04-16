package com.pixeltranslator.multi.ui

/**
 * Pre-translated disclaimer text, one entry per supported [Language]. Shown
 * on the first-run screen and available for aloud playback via TTS in
 * whichever of the two selected languages is the one the "other person"
 * speaks. The substance is:
 *   - This is AI translation; errors are possible.
 *   - Speak briefly, wait for translation, watch for the phone being pointed at you.
 *   - Nothing leaves the device.
 *
 * Mirrors the Spanish-only disclaimer from the bilingual predecessor,
 * generalized across all 13 supported languages. If a translation reads
 * awkwardly, fix the entry — the English and Spanish versions are the
 * ground truth.
 */
object Disclaimer {
    const val ENGLISH: String =
        "This is an AI translation tool. Translations may contain errors.\n\n" +
        "How the conversation works: the other person will speak, then the tool will read the translation aloud. " +
        "When it is your turn, they will point the phone toward you. Speak in short, clear phrases and wait for " +
        "the translation to finish before continuing.\n\n" +
        "Nothing from this conversation leaves the device. Your voice and this conversation are never recorded " +
        "or stored — they are deleted immediately."

    private val byLanguage: Map<Language, String> = mapOf(
        Language.ENGLISH to ENGLISH,

        Language.SPANISH to
            "Esta es una herramienta de traducción con inteligencia artificial. Las traducciones pueden contener errores.\n\n" +
            "Cómo funciona la conversación: la otra persona hablará y luego la herramienta traducirá en voz alta. " +
            "Cuando sea su turno, le apuntarán con el teléfono. Hable en frases cortas y claras, y espere la " +
            "traducción antes de continuar.\n\n" +
            "Nada de esta conversación sale del dispositivo. Su voz y esta conversación nunca se graban ni se " +
            "almacenan — se eliminan de inmediato.",

        Language.FRENCH to
            "Ceci est un outil de traduction par intelligence artificielle. Les traductions peuvent contenir des erreurs.\n\n" +
            "Comment la conversation fonctionne : l'autre personne parlera, puis l'outil lira la traduction à voix haute. " +
            "Quand ce sera votre tour, on pointera le téléphone vers vous. Parlez par phrases courtes et claires, et " +
            "attendez la fin de la traduction avant de continuer.\n\n" +
            "Rien de cette conversation ne quitte l'appareil. Votre voix et cette conversation ne sont jamais " +
            "enregistrées ni stockées — elles sont supprimées immédiatement.",

        Language.GERMAN to
            "Dies ist ein KI-Übersetzungswerkzeug. Übersetzungen können Fehler enthalten.\n\n" +
            "So funktioniert das Gespräch: Die andere Person spricht, dann liest das Werkzeug die Übersetzung vor. " +
            "Wenn Sie an der Reihe sind, richtet man das Telefon auf Sie. Sprechen Sie in kurzen, klaren Sätzen und " +
            "warten Sie auf die Übersetzung, bevor Sie fortfahren.\n\n" +
            "Nichts von diesem Gespräch verlässt das Gerät. Ihre Stimme und dieses Gespräch werden niemals " +
            "aufgezeichnet oder gespeichert — sie werden sofort gelöscht.",

        Language.PORTUGUESE to
            "Esta é uma ferramenta de tradução com inteligência artificial. As traduções podem conter erros.\n\n" +
            "Como funciona a conversa: a outra pessoa falará e depois a ferramenta lerá a tradução em voz alta. " +
            "Quando for a sua vez, apontarão o telefone para você. Fale em frases curtas e claras, e espere a " +
            "tradução terminar antes de continuar.\n\n" +
            "Nada desta conversa sai do dispositivo. Sua voz e esta conversa nunca são gravadas nem armazenadas — " +
            "são excluídas imediatamente.",

        Language.ITALIAN to
            "Questo è uno strumento di traduzione con intelligenza artificiale. Le traduzioni possono contenere errori.\n\n" +
            "Come funziona la conversazione: l'altra persona parlerà, poi lo strumento leggerà la traduzione ad alta voce. " +
            "Quando sarà il suo turno, le punteranno il telefono. Parli con frasi brevi e chiare, e aspetti la " +
            "traduzione prima di continuare.\n\n" +
            "Niente di questa conversazione lascia il dispositivo. La sua voce e questa conversazione non vengono mai " +
            "registrate né memorizzate — vengono eliminate immediatamente.",

        Language.CHINESE to
            "这是一个人工智能翻译工具。翻译可能包含错误。\n\n" +
            "对话如何进行：对方会说话，然后工具会朗读翻译。轮到您时，他们会把手机对着您。" +
            "请用简短清晰的句子说话，并在继续之前等待翻译完成。\n\n" +
            "本次对话的任何内容都不会离开本设备。您的声音和本次对话从不被录制或存储——它们会立即被删除。",

        Language.JAPANESE to
            "これはAI翻訳ツールです。翻訳に誤りが含まれる場合があります。\n\n" +
            "会話の進め方：相手の方が話し、その後ツールが翻訳を声に出して読み上げます。" +
            "あなたの番になると、相手が電話をあなたに向けます。短くはっきりした文で話し、次に進む前に翻訳が終わるのを待ってください。\n\n" +
            "この会話の内容は端末から出ることはありません。あなたの声とこの会話は録音も保存もされず、即座に削除されます。",

        Language.KOREAN to
            "이것은 AI 번역 도구입니다. 번역에 오류가 있을 수 있습니다.\n\n" +
            "대화 방식: 상대방이 말한 후 도구가 번역을 소리 내어 읽어 줍니다. " +
            "당신 차례가 되면 상대방이 전화기를 당신 쪽으로 돌립니다. 짧고 명확한 문장으로 말하고, " +
            "계속하기 전에 번역이 끝날 때까지 기다려 주십시오.\n\n" +
            "이 대화의 어떤 내용도 기기를 벗어나지 않습니다. 당신의 목소리와 이 대화는 녹음되거나 저장되지 않으며, 즉시 삭제됩니다.",

        Language.HINDI to
            "यह एक एआई अनुवाद उपकरण है। अनुवादों में त्रुटियाँ हो सकती हैं।\n\n" +
            "बातचीत कैसे होती है: दूसरा व्यक्ति बोलेगा, फिर यह उपकरण अनुवाद को ज़ोर से पढ़ेगा। " +
            "जब आपकी बारी होगी, तो वे फ़ोन आपकी ओर करेंगे। छोटे और स्पष्ट वाक्यों में बोलें, " +
            "और जारी रखने से पहले अनुवाद पूरा होने की प्रतीक्षा करें।\n\n" +
            "इस बातचीत की कोई भी चीज़ इस उपकरण से बाहर नहीं जाती। आपकी आवाज़ और यह बातचीत कभी रिकॉर्ड या संग्रहीत नहीं होती — वे तुरंत हटा दी जाती हैं।",

        Language.ARABIC to
            "هذه أداة ترجمة بالذكاء الاصطناعي. قد تحتوي الترجمات على أخطاء.\n\n" +
            "كيف تجري المحادثة: سيتحدث الشخص الآخر، ثم تقوم الأداة بقراءة الترجمة بصوت عالٍ. " +
            "عندما يحين دورك، سيوجهون الهاتف نحوك. تحدث بجمل قصيرة وواضحة، وانتظر انتهاء الترجمة قبل المتابعة.\n\n" +
            "لا يغادر أي شيء من هذه المحادثة هذا الجهاز. لا يتم تسجيل أو تخزين صوتك أو هذه المحادثة أبداً — يتم حذفها فوراً.",

        Language.RUSSIAN to
            "Это инструмент перевода на основе искусственного интеллекта. Переводы могут содержать ошибки.\n\n" +
            "Как происходит разговор: другой человек говорит, затем инструмент читает перевод вслух. " +
            "Когда придёт ваша очередь, к вам направят телефон. Говорите короткими и чёткими фразами и " +
            "дождитесь окончания перевода, прежде чем продолжать.\n\n" +
            "Ничто из этого разговора не покидает устройство. Ваш голос и этот разговор никогда не записываются и не сохраняются — они немедленно удаляются.",

        Language.VIETNAMESE to
            "Đây là công cụ dịch sử dụng trí tuệ nhân tạo. Bản dịch có thể chứa lỗi.\n\n" +
            "Cách cuộc trò chuyện diễn ra: người kia sẽ nói, sau đó công cụ sẽ đọc bản dịch thành tiếng. " +
            "Khi đến lượt bạn, họ sẽ hướng điện thoại về phía bạn. Hãy nói bằng những câu ngắn gọn, rõ ràng, " +
            "và chờ bản dịch hoàn tất trước khi tiếp tục.\n\n" +
            "Không điều gì trong cuộc trò chuyện này rời khỏi thiết bị. Giọng nói và cuộc trò chuyện này không bao giờ " +
            "được ghi âm hay lưu trữ — chúng được xóa ngay lập tức."
    )

    fun textFor(language: Language): String = byLanguage[language] ?: ENGLISH
}
