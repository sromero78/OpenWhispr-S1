package com.edib.openwhispr

/**
 * Built-in writing profiles for post-processing. Profiles are intentionally
 * small refinements layered on top of PostProcessor.DEFAULT_PROMPT.
 */
object WritingProfiles {
    const val NORMAL = "normal"
    const val WHATSAPP = "whatsapp"
    const val FORMAL = "formal"
    const val CUSTOM = "custom"

    val keys = arrayOf(NORMAL, WHATSAPP, FORMAL, CUSTOM)
    val labels = arrayOf("Normal", "WhatsApp", "Formal", "Personalizado")

    fun label(key: String): String = when (key) {
        WHATSAPP -> "WhatsApp"
        FORMAL -> "Formal"
        CUSTOM -> "Personalizado"
        else -> "Normal"
    }

    fun instructions(key: String, custom: String): String = when (key) {
        WHATSAPP -> """
            Writing profile: WhatsApp.
            Keep the result natural, conversational and concise, as a real chat message.
            Avoid needless formality and bureaucratic phrasing.
            In Spanish, relaxed chat punctuation is allowed: do not force opening ¿ or ¡ when the sentence is otherwise clear and natural.
            Preserve the speaker's actual meaning and vocabulary.
        """.trimIndent()
        FORMAL -> """
            Writing profile: Formal.
            Use polished, professional, grammatically careful language.
            Prefer clear sentence structure and standard punctuation.
            Do not add facts, arguments, greetings or conclusions that were not spoken.
        """.trimIndent()
        CUSTOM -> custom.trim()
        else -> ""
    }
}
