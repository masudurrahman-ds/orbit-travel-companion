package com.crowncouncil.orbit.domain

/** Anything that can turn a message ORBIT Brain did not understand into one short English request. */
interface Translator {
    suspend fun translate(text: String, context: String? = null): String?
}

/**
 * Optional language layer. An on-device language model only TRANSLATES a message ORBIT Brain did not understand
 * (Bangla, Banglish, loose wording) into one short English request. ORBIT Brain then does all the planning, so prices,
 * places and numbers always come from the app's own data, never from the model. The model's own words are never shown.
 * Mirrors backend/src/translation.js.
 */
object Translation {
    const val NOTICE = "Read with the on-device language model."
    private const val MAX_WORDS = 25

    val INSTRUCTIONS = """
You convert a traveller's message into ONE short English request for a Bangladesh trip planner.
The message may be in English, Bangla or Banglish (Bangla written in English letters).
Output exactly one line and nothing else. Keep every number, amount, place name and month. Write digits as 0-9.
Use the optional prior trip context only to resolve short follow-ups such as “make it cheaper”, “same place but longer”, or “for two people”. Preserve explicit changes in the latest message. Never invent a destination, amount, date, or preference; if the request cannot be safely resolved from the message and context, output NONE.
Use these forms:
- plan: "<N> days <interest> trip under <amount> for <N> people in <month>"; add cheap, affordable but good quality, or luxury when the traveller asks for a style
- "itinerary for <place>"
- "best time to visit <place>"
- "how much for <N> people <N> days in <place>"
- "compare <place> and <place>"
- "something I have not visited"
- "what have i visited"
- "how far is <place>"
- "remember that <short note>"
- "forget everything"
Interests are: beach, hills, nature, food, culture, adventure.
If the message is not about travel, output NONE.
Examples:
Message: আমি ৩ দিনের জন্য সমুদ্র সৈকতে যেতে চাই, বাজেট ২০ হাজার
Output: 3 days beach trip under 20k
Message: cox bazar jete koto khoroch hobe 4 jon
Output: how much for 4 people in Cox's Bazar
Message: শীতে পাহাড়ে ঘুরতে চাই, সস্তায়
Output: cheap hills trip in winter
Message: what is 2+2
Output: NONE
""".trimIndent()

    /** Cleans raw model output into a single safe command, or returns null if it is unusable. */
    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        val line = raw.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val s = line
            .replace(Regex("^(?:output|command|answer)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^[\\s\"'`*_>\\-]+|[\\s\"'`*_]+$"), "")
            .replace(Regex("[\\u0000-\\u001f\\u007f]"), " ")
            .replace("?", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (s.isEmpty() || Regex("^none\\.?$", RegexOption.IGNORE_CASE).matches(s)) return null
        if (!Regex("[a-z]", RegexOption.IGNORE_CASE).containsMatchIn(s)) return null
        if (s.split(" ").size > MAX_WORDS || s.length > 160) return null
        return s
    }
}
