package com.crowncouncil.orbit.ui

import android.graphics.BitmapFactory
import android.util.Base64
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentScreen(vm: OrbitViewModel, onPlanSaved: () -> Unit) {
    val messages by vm.chat.collectAsState()
    val busy by vm.chatBusy.collectAsState()
    val generatedImage by vm.generatedImageB64.collectAsState()
    val imageBusy by vm.imageBusy.collectAsState()
    val imageError by vm.imageError.collectAsState()
    val q by vm.query.collectAsState()
    val list = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var imagePrompt by remember { mutableStateOf("") }
    var speechMode by remember { mutableStateOf("english") }
    var listening by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    val speechLanguage = if (speechMode == "bengali") "bn-BD" else "en-US"
    val context = LocalContext.current
    val speechPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { listening = true; speechError = null; startOrbitSpeech(context, speechLanguage, { recognized -> input = listOf(input, recognized).filter { it.isNotBlank() }.joinToString(" ") }, { message -> speechError = message }, { listening = false }) }
    }

    LaunchedEffect(messages.size, busy) { list.animateScrollToItem(messages.size) }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Title("Guide")
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(messages.size) { i ->
                val m = messages[i]
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(
                        Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (m.fromUser) Navy else MaterialTheme.colorScheme.surface).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(m.text, color = if (m.fromUser) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                        m.plan?.let { plan -> Button(onClick = { vm.savePlan(plan, onPlanSaved) }) { Text(tr("Save this plan as my trip")) } }
                        m.brainPlan?.let { plan -> Button(onClick = { vm.saveBrainPlan(plan, null, onPlanSaved) }) { Text(tr("Save this plan as my trip")) } }
                    }
                }
            }
            if (busy) item { Text("Thinking...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Create a travel photo", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(imagePrompt, { imagePrompt = it.take(1200) }, modifier = Modifier.weight(1f), placeholder = { Text("e.g. cinematic sunrise at Sajek Valley") }, maxLines = 2)
                Button(onClick = { vm.generateTravelImage(imagePrompt) }, enabled = imagePrompt.isNotBlank() && !imageBusy) { Text(if (imageBusy) "Creating…" else "Generate") }
            }
            imageError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            generatedImage?.let { encoded ->
                val bitmap = remember(encoded) { runCatching { BitmapFactory.decodeByteArray(Base64.decode(encoded, Base64.DEFAULT), 0, Base64.decode(encoded, Base64.DEFAULT).size)?.asImageBitmap() }.getOrNull() }
                bitmap?.let { Image(bitmap = it, contentDescription = "AI-generated travel image", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { speechMode = "english"; speechError = null }, label = { Text(if (speechMode == "english") "✓ English" else "English") })
            AssistChip(onClick = { speechMode = "bengali"; speechError = null }, label = { Text(if (speechMode == "bengali") "✓ বাংলা" else "বাংলা") })
            AssistChip(onClick = { speechMode = "banglish"; speechError = null }, label = { Text(if (speechMode == "banglish") "✓ Banglish*" else "Banglish*") })
        }
        if (speechMode == "banglish") Text("*Banglish uses the device's English speech model; results depend on your speech provider.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        speechError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.ask("Plan a budget-friendly trip within BDT ${q.budget}.") }, label = { Text(tr("Budget trip")) })
            AssistChip(onClick = { vm.ask("Plan an affordable but high-quality trip within BDT ${q.budget}.") }, label = { Text(tr("Affordable, quality")) })
            AssistChip(onClick = { vm.ask("Plan a fancy, comfort-first trip.") }, label = { Text(tr("Fancy trip")) })
            AssistChip(onClick = { vm.ask("Suggest the best place I have not visited yet.") }, label = { Text(tr("Next new place")) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it.take(1000) }, modifier = Modifier.weight(1f), placeholder = { Text(tr("Ask your guide")) }, maxLines = 3)
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    listening = true
                    speechError = null
                    startOrbitSpeech(context, speechLanguage, { recognized -> input = listOf(input, recognized).filter { it.isNotBlank() }.joinToString(" ") }, { message -> speechError = message }, { listening = false })
                } else speechPermission.launch(Manifest.permission.RECORD_AUDIO)
            }, enabled = !busy) { Text(if (listening) "Listening…" else "🎙") }
            Button(onClick = { vm.ask(input); input = "" }, enabled = input.isNotBlank() && !busy) { Text(tr("Send")) }
        }
    }
}


/** Uses Android's installed speech service; offline support depends on the device's language pack. */
private fun startOrbitSpeech(
    context: android.content.Context,
    languageTag: String,
    onText: (String) -> Unit,
    onError: (String) -> Unit,
    onFinished: () -> Unit
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onError("Speech recognition is unavailable on this device. You can type your request instead.")
        onFinished()
        return
    }
    var finished = false
    fun finish() {
        if (!finished) { finished = true; onFinished() }
    }
    val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrElse {
        onError("Could not start speech recognition. Please try typing your request.")
        finish()
        return
    }
    recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onError(error: Int) {
            runCatching { recognizer.destroy() }
            onError(when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't understand that. Try speaking clearly or type your request."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection or an installed offline language pack."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Please try again in a moment."
                else -> "Voice input stopped. Please try again or type your request."
            })
            finish()
        }
        override fun onResults(results: android.os.Bundle?) {
            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) onText(spoken) else onError("No speech was detected. Please try again.")
            runCatching { recognizer.destroy() }
            finish()
        }
        override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_PROMPT, if (languageTag == "bn-BD") "বাংলায় বলুন" else "Speak your travel request")
    }
    runCatching { recognizer.startListening(intent) }.onFailure {
        runCatching { recognizer.destroy() }
        onError("Could not start voice input. Please try again.")
        finish()
    }
}
