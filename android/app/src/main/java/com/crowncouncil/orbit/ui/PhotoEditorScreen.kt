@file:OptIn(ExperimentalLayoutApi::class)

package com.crowncouncil.orbit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun PhotoEditorScreen() {
    val ctx = LocalContext.current
    var full by remember { mutableStateOf<Bitmap?>(null) }       // used when saving
    var preview by remember { mutableStateOf<Bitmap?>(null) }    // used while editing
    var edits by remember { mutableStateOf(PhotoEdits()) }
    var message by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun load(uri: Uri?) {
        if (uri == null) return
        val big = PhotoEditor.decode(ctx, uri, 3000)
        if (big == null) { message = "Could not open that picture."; return }
        full = big; preview = PhotoEditor.decode(ctx, uri, 1080); edits = PhotoEdits(); message = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { load(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) load(cameraUri) }

    fun doSave() {
        val src = full ?: return
        val uri = PhotoEditor.save(ctx, PhotoEditor.render(src, edits))
        message = if (uri != null) "Saved to Pictures/ORBIT." else "Could not save the picture."
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) doSave() else message = "Saving needs storage permission on this Android version." }

    val shown = remember(preview, edits) { preview?.let { PhotoEditor.render(it, edits) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Photo editor")
        Text("Edits happen on your phone. Pictures are never uploaded.", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose photo") }
            OutlinedButton(onClick = {
                val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(dir, "shot_${System.currentTimeMillis()}.jpg"))
                cameraUri = uri; camera.launch(uri)
            }) { Text("Take photo") }
        }
        shown?.let { bmp ->
            Image(
                bmp.asImageBitmap(), contentDescription = "Photo preview", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(16.dp))
            )
            SectionLabel("FILTER")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoFilter.entries.forEach { f -> FilterChip(selected = edits.filter == f, onClick = { edits = edits.copy(filter = f) }, label = { Text(f.label) }) }
            }
            Adjust("Brightness", edits.brightness, -0.5f..0.5f) { edits = edits.copy(brightness = it) }
            Adjust("Contrast", edits.contrast, 0.5f..1.5f) { edits = edits.copy(contrast = it) }
            Adjust("Colour", edits.saturation, 0f..2f) { edits = edits.copy(saturation = it) }
            Adjust("Warmth", edits.warmth, -1f..1f) { edits = edits.copy(warmth = it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { edits = edits.copy(rotation = (edits.rotation + 90) % 360) }) { Text("Rotate") }
                Text("Square crop"); Switch(checked = edits.squareCrop, onCheckedChange = { edits = edits.copy(squareCrop = it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { edits = PhotoEdits() }) { Text("Reset") }
                Button(onClick = {
                    if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else doSave()
                }) { Text("Save to gallery") }
            }
        }
        message?.let { Text(it, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
    }
}

@Composable
private fun Adjust(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Text(label, fontSize = 13.sp); Slider(value = value, onValueChange = onChange, valueRange = range) }
}
