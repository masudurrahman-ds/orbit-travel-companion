package com.crowncouncil.orbit.net

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import org.json.JSONObject

data class Consent(val ai: Boolean = false, val location: Boolean = false, val leaderboard: Boolean = false)

data class UserInfo(val identifier: String, val displayName: String, val consent: Consent) {
    companion object {
        fun from(j: JSONObject): UserInfo {
            val c = j.optJSONObject("consent") ?: JSONObject()
            return UserInfo(
                j.optString("identifier"), j.optString("displayName"),
                Consent(c.optBoolean("ai"), c.optBoolean("location"), c.optBoolean("leaderboard"))
            )
        }
    }
}

/** Keeps the login token and profile in encrypted storage on the device. */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "orbit_session",
        "orbit_master_key",
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    var guest: Boolean
        get() = prefs.getBoolean("guest", false)
        set(v) = prefs.edit().putBoolean("guest", v).apply()

    var user: UserInfo?
        get() = prefs.getString("user", null)?.let { runCatching { UserInfo.from(JSONObject(it)) }.getOrNull() }
        set(v) {
            val e = prefs.edit()
            if (v == null) e.remove("user") else e.putString("user", JSONObject().apply {
                put("identifier", v.identifier); put("displayName", v.displayName)
                put("consent", JSONObject().put("ai", v.consent.ai).put("location", v.consent.location).put("leaderboard", v.consent.leaderboard))
            }.toString())
            e.apply()
        }

    fun clear() = prefs.edit().clear().apply()
}
