package com.pkrkey.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * The PKR symbol is an image. It is served to other apps through a FileProvider
 * content:// URI. It is never treated as a text character.
 */
object SymbolImage {
    private const val DIR = "pkr_images"
    private const val NAME = "pkr_symbol.png"
    const val MIME = "image/png"
    private const val LABEL = "PKR symbol"

    /** Copies the bundled PNG into the cache folder (if needed) and returns it. */
    fun file(context: Context): File {
        val dir = File(context.cacheDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, NAME)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(NAME).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return out
    }

    fun uri(context: Context): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file(context))

    /** Puts the image on the clipboard. Returns false if it could not be prepared. */
    fun copyToClipboard(context: Context): Boolean = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, LABEL, uri(context)))
        true
    } catch (e: Exception) {
        false
    }

    /** Opens the Android share sheet with the image. Works from an activity or the keyboard service. */
    fun share(context: Context, chooserTitle: String): Boolean = try {
        val uri = uri(context)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(LABEL, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        false
    }
}
