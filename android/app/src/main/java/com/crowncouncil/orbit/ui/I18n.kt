package com.crowncouncil.orbit.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Light-weight English / Bangla switch. Strings are looked up by their English text, so anything without a Bangla entry
 * simply stays in English. The guide's replies are English for now; it understands Bangla questions.
 * Please have a Bangla speaker review these translations.
 */
object I18n {
    var lang by mutableStateOf("en")
        private set

    fun init(context: Context) { lang = context.getSharedPreferences("orbit_prefs", Context.MODE_PRIVATE).getString("lang", "en") ?: "en" }
    fun set(context: Context, value: String) {
        lang = value
        context.getSharedPreferences("orbit_prefs", Context.MODE_PRIVATE).edit().putString("lang", value).apply()
    }

    fun t(en: String): String = if (lang == "bn") (BN[en] ?: en) else en

    private val BN = mapOf(
        "Home" to "হোম", "Plan" to "পরিকল্পনা", "Guide" to "গাইড", "Map" to "মানচিত্র", "More" to "আরও", "Explore" to "ঘুরে দেখুন",
        "Itinerary" to "ভ্রমণসূচি", "Budget" to "বাজেট", "Live map" to "লাইভ ম্যাপ", "Photo editor" to "ছবি সম্পাদনা", "Rankings" to "র‍্যাঙ্কিং",
        "Add a spot" to "স্পট যোগ করুন", "Account" to "অ্যাকাউন্ট", "Group trips" to "গ্রুপ ভ্রমণ", "My Bangladesh" to "আমার বাংলাদেশ",
        "Plan your trip" to "ভ্রমণের পরিকল্পনা করুন", "Traveller rankings" to "ভ্রমণকারীদের র‍্যাঙ্কিং", "Sign in" to "সাইন ইন",
        "Create account" to "অ্যাকাউন্ট খুলুন", "Continue as guest" to "অতিথি হিসেবে চালিয়ে যান", "TOUR STYLE" to "ট্যুরের ধরন",
        "TRAVEL MONTH" to "ভ্রমণের মাস", "INTERESTS" to "আগ্রহ", "Days" to "দিন", "Travellers" to "ভ্রমণকারী", "Find destinations" to "গন্তব্য খুঁজুন",
        "Beach" to "সমুদ্র সৈকত", "Hills" to "পাহাড়", "Nature" to "প্রকৃতি", "Food" to "খাবার", "Culture" to "সংস্কৃতি", "Adventure" to "অ্যাডভেঞ্চার",
        "Plan this trip" to "এই ট্রিপ পরিকল্পনা করুন", "WHY THIS SCORE" to "এই স্কোর কেন", "Search spot, district or division" to "স্পট, জেলা বা বিভাগ খুঁজুন",
        "No matches" to "কিছু পাওয়া যায়নি", "Add expense" to "খরচ যোগ করুন", "BY CATEGORY" to "ক্যাটাগরি অনুযায়ী", "EXPENSES" to "খরচের তালিকা",
        "NEXT UP" to "এরপর", "No trip yet" to "এখনো কোনো ট্রিপ নেই", "Send" to "পাঠান", "Ask your guide" to "গাইডকে জিজ্ঞাসা করুন",
        "Budget trip" to "কম খরচের ট্রিপ", "Affordable, quality" to "সাশ্রয়ী, মানসম্মত", "Fancy trip" to "বিলাসবহুল ট্রিপ", "Next new place" to "নতুন জায়গা",
        "Save this plan as my trip" to "এই পরিকল্পনা আমার ট্রিপ হিসেবে সংরক্ষণ করুন", "Thinking..." to "ভাবছি...", "Share my passport" to "আমার পাসপোর্ট শেয়ার করুন",
        "Share trip story" to "ট্রিপের গল্প শেয়ার করুন", "BADGES" to "ব্যাজ", "Mark visited" to "ঘুরে এসেছি", "Unmark" to "মুছুন",
        "Gold pin = visited" to "সোনালি পিন = ঘুরেছেন", "Faint dot = still to visit" to "ফিকে বিন্দু = বাকি আছে", "Start tracking" to "ট্র্যাকিং শুরু",
        "Stop tracking" to "ট্র্যাকিং বন্ধ", "Allow location" to "লোকেশন অনুমতি দিন", "Share my location" to "আমার লোকেশন শেয়ার করুন", "Call 999" to "৯৯৯-এ কল করুন",
        "Ask the guide about this trip" to "এই ট্রিপ নিয়ে গাইডকে জিজ্ঞাসা করুন", "DATA AND PERMISSIONS" to "ডেটা ও অনুমতি", "POLICIES" to "নীতিমালা",
        "Privacy Policy" to "গোপনীয়তা নীতি", "Terms of Use" to "ব্যবহারের শর্ত", "Sign out" to "সাইন আউট", "Delete my account" to "আমার অ্যাকাউন্ট মুছুন",
        "Download my data" to "আমার ডেটা ডাউনলোড", "LANGUAGE" to "ভাষা", "Cancel" to "বাতিল", "Save" to "সংরক্ষণ", "Delete" to "মুছুন",
        "Create group" to "গ্রুপ তৈরি করুন", "Join with code" to "কোড দিয়ে যোগ দিন", "Group name" to "গ্রুপের নাম", "Invite code" to "আমন্ত্রণ কোড",
        "Balances" to "হিসাব", "Who pays whom" to "কে কাকে দেবে", "Leave group" to "গ্রুপ ছাড়ুন", "Display name" to "প্রদর্শিত নাম",
        "Email or mobile number" to "ইমেইল বা মোবাইল নম্বর", "Password" to "পাসওয়ার্ড", "AGREEMENTS" to "সম্মতি", "Verify" to "যাচাই করুন", "Back" to "পেছনে",
        "Report a price" to "দাম জানান", "ADVISORIES" to "সতর্কবার্তা", "Submit for review" to "পর্যালোচনার জন্য পাঠান", "Join the leaderboard" to "লিডারবোর্ডে যোগ দিন",
        "Jan" to "জানু", "Feb" to "ফেব্রু", "Mar" to "মার্চ", "Apr" to "এপ্রিল", "May" to "মে", "Jun" to "জুন", "Jul" to "জুলাই", "Aug" to "আগস্ট",
        "Sep" to "সেপ্টেম্বর", "Oct" to "অক্টোবর", "Nov" to "নভেম্বর", "Dec" to "ডিসেম্বর"
    )
}

fun tr(en: String): String = I18n.t(en)
