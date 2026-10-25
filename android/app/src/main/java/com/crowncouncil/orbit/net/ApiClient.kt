package com.crowncouncil.orbit.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Result of a call. `status` is 0 when the network failed. */
data class ApiResult(val status: Int, val body: JSONObject?) {
    val ok: Boolean get() = status in 200..299
    val error: String? get() = body?.optString("error")?.takeIf { it.isNotEmpty() }
    val message: String? get() = body?.optString("message")?.takeIf { it.isNotEmpty() }
}

/** Minimal JSON-over-HTTP client for the ORBIT backend. */
class ApiClient(val baseUrl: String, private val session: SessionStore) {

    suspend fun request(method: String, path: String, json: JSONObject? = null, auth: Boolean = true): ApiResult =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10_000
                    readTimeout = 30_000
                    setRequestProperty("Accept", "application/json")
                    if (auth) session.token?.let { setRequestProperty("Authorization", "Bearer $it") }
                    if (json != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { it.write(json.toString().toByteArray()) }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..399) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                ApiResult(code, runCatching { JSONObject(text) }.getOrNull())
            } catch (e: Exception) {
                ApiResult(0, null)
            } finally {
                conn?.disconnect()
            }
        }

    suspend fun get(path: String) = request("GET", path)
    suspend fun post(path: String, body: JSONObject, auth: Boolean = true) = request("POST", path, body, auth)
    suspend fun put(path: String, body: JSONObject) = request("PUT", path, body)
    suspend fun delete(path: String) = request("DELETE", path)
}
