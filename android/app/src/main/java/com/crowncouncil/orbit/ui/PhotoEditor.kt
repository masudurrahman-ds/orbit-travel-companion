package com.crowncouncil.orbit.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.min

enum class PhotoFilter(val label: String) { NONE("Original"), MONO("Mono"), SEPIA("Sepia"), VIVID("Vivid"), FADE("Fade") }

data class PhotoEdits(
    val brightness: Float = 0f,   // -0.5 .. 0.5
    val contrast: Float = 1f,     // 0.5 .. 1.5
    val saturation: Float = 1f,   // 0 .. 2
    val warmth: Float = 0f,       // -1 .. 1 (cool to warm)
    val filter: PhotoFilter = PhotoFilter.NONE,
    val rotation: Int = 0,        // degrees, multiples of 90
    val squareCrop: Boolean = false
)

/** On-device photo editing. Nothing here uploads the picture anywhere. */
object PhotoEditor {

    fun colorMatrix(e: PhotoEdits): ColorMatrix {
        val cm = ColorMatrix()
        cm.postConcat(ColorMatrix().apply { setSaturation(e.saturation) })
        val c = e.contrast
        val t = (1f - c) * 128f + e.brightness * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        val w = e.warmth * 0.15f
        cm.postConcat(ColorMatrix(floatArrayOf(1f + w, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f - w, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        when (e.filter) {
            PhotoFilter.NONE -> Unit
            PhotoFilter.MONO -> cm.postConcat(ColorMatrix().apply { setSaturation(0f) })
            PhotoFilter.SEPIA -> cm.postConcat(ColorMatrix(floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            PhotoFilter.VIVID -> {
                cm.postConcat(ColorMatrix().apply { setSaturation(1.4f) })
                cm.postConcat(ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, -12f, 0f, 1.1f, 0f, 0f, -12f, 0f, 0f, 1.1f, 0f, -12f, 0f, 0f, 0f, 1f, 0f)))
            }
            PhotoFilter.FADE -> cm.postConcat(ColorMatrix(floatArrayOf(.85f, 0f, 0f, 0f, 24f, 0f, .85f, 0f, 0f, 24f, 0f, 0f, .85f, 0f, 24f, 0f, 0f, 0f, 1f, 0f)))
        }
        return cm
    }

    fun render(src: Bitmap, e: PhotoEdits): Bitmap {
        var b = src
        if (e.rotation % 360 != 0) {
            b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(e.rotation.toFloat()) }, true)
        }
        if (e.squareCrop) {
            val s = min(b.width, b.height)
            b = Bitmap.createBitmap(b, (b.width - s) / 2, (b.height - s) / 2, s, s)
        }
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix(e)) })
        return out
    }

    /** Decodes a picture, scaled so its longest side is at most [maxDim], upright according to its EXIF data. */
    fun decode(ctx: Context, uri: Uri, maxDim: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val degrees = ctx.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = min(1f, maxDim.toFloat() / max(bmp.width, bmp.height))
        val m = Matrix().apply { postRotate(degrees); postScale(scale, scale) }
        Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }.getOrNull()

    /** Saves to the gallery under Pictures/ORBIT. */
    @Suppress("DEPRECATION")
    fun save(ctx: Context, bmp: Bitmap): Uri? = runCatching {
        val name = "ORBIT_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ORBIT")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
            uri
        } else {
            MediaStore.Images.Media.insertImage(ctx.contentResolver, bmp, name, "ORBIT")?.let(Uri::parse)
        }
    }.getOrNull()
}
