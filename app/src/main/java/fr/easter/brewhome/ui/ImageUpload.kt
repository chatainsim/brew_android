package fr.easter.brewhome.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Encodage des images pour l'upload : on ramène à 1600 px max de côté et on
 * sort un data URL JPEG base64, format attendu par le serveur.
 */
object ImageUpload {
    private const val MAX_SIDE = 1600
    private const val QUALITY = 85

    fun bitmapToDataUrl(bitmap: Bitmap): String {
        val scaled = downscale(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    /** Décode une image de la galerie en respectant l'orientation EXIF. */
    fun uriToBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        rotate(bmp, orientation)
    }.getOrNull()

    private fun downscale(bmp: Bitmap): Bitmap {
        val max = maxOf(bmp.width, bmp.height)
        if (max <= MAX_SIDE) return bmp
        val ratio = MAX_SIDE.toFloat() / max
        return Bitmap.createScaledBitmap(
            bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true,
        )
    }

    private fun rotate(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
