package com.crowncouncil.orbit.data

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.crowncouncil.orbit.domain.Translator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("orbit_llm", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "models").apply { mkdirs() }
    val file get() = File(directory, "orbit-llm.litertlm")
    fun exists() = file.isFile && file.length() > 50_000_000L
    fun sizeMb() = if (exists()) file.length() / (1024 * 1024) else 0L
    var enabled: Boolean
        get() = exists() && prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value && exists()).apply() }

    suspend fun import(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val temporary = File(directory, "model.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to read the selected model file.")
            require(temporary.length() > 50_000_000L) { "Select a valid .litertlm model file, usually hundreds of MB or larger." }
            check(temporary.renameTo(file)) { "Unable to save the model in app storage." }
            prefs.edit().putBoolean("enabled", false).apply()
            file.length()
        }.onFailure { File(directory, "model.tmp").delete() }
    }

    fun remove() { file.delete(); prefs.edit().putBoolean("enabled", false).apply() }
}

/** Real local generator. Model weights stay in the app-private directory. */
class LiteRtTranslator(private val context: Context, private val store: ModelStore) : Translator, AutoCloseable {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private suspend fun load() = withContext(Dispatchers.IO) {
        if (conversation != null) return@withContext
        check(store.enabled && store.exists()) { "Enable an imported .litertlm model first." }
        val candidate = Engine(EngineConfig(modelPath = store.file.absolutePath, backend = Backend.CPU()))
        try {
            candidate.initialize()
            engine = candidate
            conversation = candidate.createConversation()
        } catch (error: Throwable) {
            runCatching { candidate.close() }
            throw error
        }
    }

    override suspend fun translate(text: String, context: String?): String? = withContext(Dispatchers.IO) {
        load()
        val prompt = "You are ORBIT, a private on-device travel assistant. Interpret the request using this context: " +
            (context ?: "No saved trip context.") + "\nUser request: " + text +
            "\nIf this is a Bangladesh trip-planning command, return one short normalized English command for ORBIT's verified planner. " +
            "Otherwise answer directly in under 120 words. Do not invent travel facts or prices."
        conversation?.sendMessage(prompt)?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun close() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
