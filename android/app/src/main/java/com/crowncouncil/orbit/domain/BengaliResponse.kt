package com.crowncouncil.orbit.domain

/**
 * Deterministic Bengali response layer for the offline Brain.
 *
 * It localises stable response phrases while leaving destination names, numeric estimates,
 * currency values and itinerary data intact. It is deliberately not described as a generative
 * language model; unsupported wording remains readable rather than being fabricated.
 */
object BengaliResponse {
    private val phrases = listOf(
        "I did not catch a trip request in that." to "তোমার কথাটি থেকে ভ্রমণ-সংক্রান্ত অনুরোধটি বুঝতে পারিনি।",
        "I couldn't identify the district, division or upazila in that question." to "প্রশ্নে কোন জেলা, বিভাগ বা উপজেলা বোঝানো হয়েছে তা শনাক্ত করতে পারিনি।",
        "Try a specific administrative area name." to "নির্দিষ্ট জেলা, বিভাগ বা উপজেলার নাম লিখে আবার চেষ্টা করো।",
        "I don't have verified tourist-place records for" to "এই এলাকার জন্য আমার অফলাইন তালিকায় যাচাইকৃত পর্যটনস্থলের তথ্য নেই:",
        "Results come from ORBIT's bundled datasets; coverage is not exhaustive." to "তথ্যগুলো ORBIT-এর সংরক্ষিত ডেটাসেট থেকে নেওয়া; তালিকাটি সম্পূর্ণ নয়।",
        "These are straight-line distances, not driving distances or travel times." to "এগুলো সরলরেখায় মাপা দূরত্ব—সড়কপথের দূরত্ব বা যাত্রার সময় নয়।",
        "To find nearby places, name a starting place or enable location access and share your current position with ORBIT." to "কাছাকাছি জায়গা খুঁজতে শুরুর স্থানটির নাম বলো, অথবা লোকেশন অনুমতি দিয়ে ORBIT-এর সঙ্গে বর্তমান অবস্থান শেয়ার করো।",
        "I couldn't find a listed place within" to "আমার সংরক্ষিত তালিকায় এই সীমার মধ্যে কোনো স্থান খুঁজে পাইনি:",
        "You are welcome." to "স্বাগতম!",
        "You are welcome. Tell me when you want the next plan." to "স্বাগতম! পরের ভ্রমণ পরিকল্পনা করতে চাইলে আমাকে জানিও।",
        "Hello. Tell me what kind of trip you want, or say" to "হ্যালো! কী ধরনের ভ্রমণ করতে চাও বলো, অথবা বলতে পারো",
        "I can plan trips by budget, days, interests and season" to "আমি বাজেট, দিনের সংখ্যা, পছন্দের বিষয় ও ঋতু অনুযায়ী ভ্রমণ পরিকল্পনা করতে পারি",
        "Done. I cleared your saved preferences." to "হয়ে গেছে—তোমার সংরক্ষিত পছন্দগুলো মুছে দিয়েছি।",
        "Saved:" to "মনে রাখলাম:",
        "I will use it in future plans." to "ভবিষ্যৎ পরিকল্পনায় এটি বিবেচনায় রাখব।",
        "I can explain the trade-offs, but I need a destination to evaluate first." to "কেন এই সুপারিশ করেছি তা ব্যাখ্যা করতে পারি। তবে আগে একটি গন্তব্য জানতে হবে।",
        "You have not marked any spots yet." to "এখনো কোনো স্থানকে ভ্রমণ করা হয়েছে বলে চিহ্নিত করোনি।",
        "Open the Map tab and tap a district to mark places you have visited." to "ভ্রমণ করা স্থান চিহ্নিত করতে ম্যাপ ট্যাবে গিয়ে একটি জেলায় ট্যাপ করো।",
        "Open the Live map and start tracking, then ask again." to "লাইভ ম্যাপ খুলে লোকেশন ট্র্যাকিং চালু করে আবার জিজ্ঞেস করো।",
        "I need your location to measure the distance." to "দূরত্ব হিসাব করতে তোমার অবস্থান জানা প্রয়োজন।",
        "Plan a trip first, or name the place, and I will measure the distance from where you are." to "আগে একটি ভ্রমণ পরিকল্পনা করো অথবা স্থানের নাম বলো—তাহলে তোমার অবস্থান থেকে দূরত্ব হিসাব করতে পারব।",
        "Best months for" to "ভ্রমণের জন্য ভালো সময়:",
        "is in season." to "এই সময়টি ভ্রমণের উপযোগী মৌসুমের মধ্যে পড়ে।",
        "is off-season, so expect rain." to "এটি ভ্রমণের প্রধান মৌসুম নয়; বৃষ্টির সম্ভাবনা বিবেচনায় রেখো।",
        "Costs are estimates." to "খরচগুলো আনুমানিক হিসাব।",
        "Estimates only." to "এগুলো কেবল আনুমানিক হিসাব।",
        "Also good:" to "আরও ভালো বিকল্প:",
        "Assumed:" to "ধরে নেওয়া হয়েছে:",
        "Tags:" to "বৈশিষ্ট্য:",
        "Best months:" to "ভ্রমণের ভালো মাস:",
        "luxury tour" to "বিলাসবহুল ভ্রমণ",
        "Fancy tour" to "প্রিমিয়াম ভ্রমণ",
        "Value tour" to "সাশ্রয়ী ও মানসম্মত ভ্রমণ",
        "Budget tour" to "কম বাজেটের ভ্রমণ"
    )

    fun render(english: String, userMessage: String = ""): String {
        val normalizedInput = userMessage.lowercase()
        val tone = when {
            listOf("ভয়", "ভয়", "চিন্তা", "টেনশন", "নিরাপদ", "সমস্যা", "দুশ্চিন্তা", "worried", "afraid", "tension").any { normalizedInput.contains(it) } ->
                "তোমার উদ্বেগটা বুঝতে পারছি। নিরাপত্তা ও বাস্তব পরিস্থিতি মাথায় রেখে ধীরে-সুস্থে পরিকল্পনা করি।"
            listOf("উচ্ছ্বসিত", "এক্সাইটেড", "অপেক্ষা করতে পারছি না", "দারুণ লাগছে", "excited", "can't wait").any { normalizedInput.contains(it) } ->
                "বাহ, দারুণ! চলো, এই ভ্রমণটাকে স্মরণীয় করে তোলার মতো পরিকল্পনা করি।"
            listOf("ক্লান্ত", "বিরক্ত", "মন খারাপ", "হতাশ", "tired", "stressed", "upset").any { normalizedInput.contains(it) } ->
                "বুঝতে পারছি—এমন সময়ে সহজ ও চাপমুক্ত পরিকল্পনাই ভালো।"
            else -> ""
        }
        var result = english
        for ((from, to) in phrases) {
            result = result.replace(from, to, ignoreCase = true)
        }
        result = result.replace("BDT ", "৳")
            .replace(" — ", " — ")
        return if (tone.isBlank()) result else tone + "\n\n" + result
    }
}
