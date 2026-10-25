package com.crowncouncil.orbit.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Everything here hands content to the system share sheet or dialer. ORBIT never sends it anywhere itself. */
fun shareText(ctx: Context, text: String, title: String = "Share") {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(send, title))
}

fun shareImage(ctx: Context, bitmap: Bitmap, name: String, caption: String? = null) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); caption?.let { putExtra(Intent.EXTRA_TEXT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Share"))
}

/** Hands a map link with your position to your messaging app of choice. */
fun shareLocation(ctx: Context, lat: Double, lon: Double) {
    shareText(ctx, String.format(Locale.US, "I am here: https://maps.google.com/?q=%.5f,%.5f (shared from ORBIT)", lat, lon), "Share my location")
}

/** 999 is Bangladesh's national emergency number. ACTION_DIAL only opens the dialer; the traveller presses call. */
fun dialEmergency(ctx: Context) {
    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:999")))
}
