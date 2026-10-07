package com.edib.openwhispr

/**
 * Built-in writing profiles for post-processing. Profiles are refinements
 * layered on top of PostProcessor.DEFAULT_PROMPT.
 */
object WritingProfiles {
    const val AUTO = "auto"
    const val NORMAL = "normal"
    const val WHATSAPP = "whatsapp"
    const val FORMAL = "formal"
    const val CUSTOM = "custom"

    val keys = arrayOf(AUTO, NORMAL, WHATSAPP, FORMAL, CUSTOM)
    val labels = arrayOf("Automático", "Normal", "WhatsApp / informal", "Formal", "Personalizado")

    fun label(key: String): String = when (key) {
        AUTO -> "Automático"
        WHATSAPP -> "WhatsApp / informal"
        FORMAL -> "Formal"
        CUSTOM -> "Personalizado"
        else -> "Normal"
    }

    /**
     * Automatic profile selection is intentionally package-based and small.
     * Unknown apps fall back to Normal instead of guessing from editor text.
     */
    fun autoProfileForPackage(packageName: String?): String = when (packageName.orEmpty()) {
        "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging", "com.facebook.orca" -> WHATSAPP

        "com.google.android.gm",
        "com.samsung.android.email.provider",
        "com.microsoft.office.outlook" -> FORMAL

        else -> NORMAL
    }

    fun appLabel(packageName: String?): String = when (packageName.orEmpty()) {
        "com.whatsapp" -> "WhatsApp"
        "com.whatsapp.w4b" -> "WhatsApp Business"
        "org.telegram.messenger" -> "Telegram"
        "org.thoughtcrime.securesms" -> "Signal"
        "com.google.android.apps.messaging" -> "Mensajes"
        "com.facebook.orca" -> "Messenger"
        "com.google.android.gm" -> "Gmail"
        "com.samsung.android.email.provider" -> "Correo de Samsung"
        "com.microsoft.office.outlook" -> "Outlook"
        else -> "esta app"
    }

    fun instructions(key: String, custom: String): String = when (key) {
        WHATSAPP -> """
            Writing profile: conversational / instant messaging.
            Rewrite the transcript so it reads like a natural message a real person would send in a chat.
            Prefer everyday vocabulary and short, direct sentence structures.
            Remove spoken false starts, repeated fragments and unnecessary filler without making the message sound polished like an email.
            Preserve useful interjections, emphasis and the speaker's personal wording when they help the conversational tone.
            Avoid bureaucratic, corporate, literary or overly courteous phrasing unless the speaker explicitly used it.
            Do not upgrade ordinary words into more formal synonyms just to sound elegant.
            In Spanish, relaxed chat punctuation is acceptable; do not force opening ¿ or ¡ when omitting it sounds natural in an informal message.
            Do not add facts, greetings, emojis, conclusions or intentions that were not spoken.
        """.trimIndent()

        FORMAL -> """
            Writing profile: formal / professional.
            Rewrite the transcript as polished professional prose suitable for an email or formal written communication.
            Use complete, well-structured sentences, standard punctuation and grammatically careful Spanish.
            Replace obvious spoken filler and overly colloquial constructions with clear written equivalents while preserving the speaker's meaning.
            Prefer precise neutral wording over chat-style abbreviations, fragments or casual interjections.
            Keep the result natural rather than pompous or bureaucratic.
            Do not invent facts, arguments, greetings, farewells or conclusions that were not spoken.
        """.trimIndent()

        CUSTOM -> custom.trim()
        else -> ""
    }

    fun examplesText(): String = """
        El mismo dictado puede quedar así:

        Dictado:
        “Mañana no puedo ir porque tengo una reunión y seguramente termine bastante tarde.”

        WhatsApp / informal:
        “Mañana no puedo ir, tengo una reunión y seguramente termine bastante tarde.”

        Formal:
        “Mañana no podré asistir, ya que tengo una reunión y previsiblemente terminaré bastante tarde.”

        Normal mantiene un estilo neutro. Automático elige según la app activa.
    """.trimIndent()
}
