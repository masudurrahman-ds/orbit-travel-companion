package com.crowncouncil.orbit.data

import android.content.Context
import android.net.Uri
import com.crowncouncil.orbit.domain.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Temporary build-safe model store.
 * LiteRT-LM is disabled in this release because its floating latest.release dependency pulled
 * Kotlin metadata incompatible with the project's Kotlin 2.0.21 / KSP 2.0.21 toolchain.
 * ORBIT Brain remains available offline; optional imported-model translation is disabled until
 * a specific compatible runtime version is verified.
 */
class ModelStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("orbit_llm", Context.MODE_PRIVATE)
    private val dir: File get() = File(context.filesDir, "models").apply { mkdirs() }
    val file: File get() = File(dir, "orbit-llm.litertlm")
    fun exists(): Boolean = false
    fun sizeMb(): Long = 0L
    var enabled: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) { prefs.edit().putBoolean("enabled", false).apply() }

    suspend fun import(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        Result.failure(IllegalStateException(
            "Optional on-device language-model support is temporarily disabled in this build. ORBIT Brain works offline without it."
        ))
    }

    fun remove() { file.delete(); prefs.edit().putBoolean("enabled", false).apply() }
}

/** No-op adapter: the core deterministic offline Brain does not require an LLM. */
class LiteRtTranslator(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") store: ModelStore
) : Translator, AutoCloseable {
    override suspend fun translate(text: String, context: String?): String? = null
    override fun close() = Unit
}
