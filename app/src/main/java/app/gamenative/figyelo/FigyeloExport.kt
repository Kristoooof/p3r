package app.gamenative.figyelo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.gamenative.figyelo.elemzes.Fajlok
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Copies finished sessions to Downloads/GameNativeFigyelo and builds share intents. */
internal object FigyeloExport {

    const val LETOLTES_MAPPA = "GameNativeFigyelo"

    private val masolando = listOf(
        Fajlok.JELENTES, Fajlok.OSSZEGZES, Fajlok.IDOVONAL,
        Fajlok.META, Fajlok.MINTA, Fajlok.KEPKOCKAK, Fajlok.NAPLO, Fajlok.JELEK,
    )

    /** Returns a human readable location, e.g. "Letöltések/GameNativeFigyelo/<session>". */
    fun letoltesekbe(context: Context, mappa: File): String {
        val relativ = "${Environment.DIRECTORY_DOWNLOADS}/$LETOLTES_MAPPA/${mappa.name}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            for (nev in masolando) {
                val forras = File(mappa, nev)
                if (!forras.exists()) continue
                val ertekek = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, nev)
                    put(MediaStore.Downloads.MIME_TYPE, mime(nev))
                    put(MediaStore.Downloads.RELATIVE_PATH, relativ)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ertekek) ?: continue
                resolver.openOutputStream(uri)?.use { ki -> forras.inputStream().use { it.copyTo(ki) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            }
        } else {
            @Suppress("DEPRECATION")
            val cel = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$LETOLTES_MAPPA/${mappa.name}")
            cel.mkdirs()
            for (nev in masolando) {
                val forras = File(mappa, nev)
                if (forras.exists()) forras.copyTo(File(cel, nev), overwrite = true)
            }
        }
        return "Letöltések/$LETOLTES_MAPPA/${mappa.name}"
    }

    fun zip(mappa: File): File {
        val zip = File(mappa, "${mappa.name}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { ki ->
            for (nev in masolando) {
                val f = File(mappa, nev)
                if (!f.exists()) continue
                ki.putNextEntry(ZipEntry(nev))
                f.inputStream().use { it.copyTo(ki) }
                ki.closeEntry()
            }
        }
        return zip
    }

    /** Share intent for the report + summary (and optionally the raw data as a zip). */
    fun megosztas(context: Context, mappa: File, nyersAdatokkal: Boolean): Intent {
        val fajlok = if (nyersAdatokkal) {
            listOf(zip(mappa))
        } else {
            listOf(Fajlok.JELENTES, Fajlok.OSSZEGZES).map { File(mappa, it) }.filter { it.exists() }
        }
        val urik = ArrayList<Uri>(fajlok.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) })
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (nyersAdatokkal) "application/zip" else "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, urik)
            putExtra(Intent.EXTRA_SUBJECT, "GameNative Figyelő – ${mappa.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Megosztás") }
    }

    private fun mime(nev: String): String = when {
        nev.endsWith(".html") -> "text/html"
        nev.endsWith(".json") -> "application/json"
        nev.endsWith(".csv") -> "text/csv"
        nev.endsWith(".jsonl") -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}
