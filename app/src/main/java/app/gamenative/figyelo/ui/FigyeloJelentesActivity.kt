package app.gamenative.figyelo.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gamenative.figyelo.FigyeloExport
import app.gamenative.figyelo.FigyeloRogzito
import app.gamenative.figyelo.elemzes.Fajlok
import app.gamenative.figyelo.elemzes.Kimenet
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/** Lists recorded Figyelő sessions and shows their HTML report. */
class FigyeloJelentesActivity : ComponentActivity() {

    /** Short status line (the app forbids Toast; SnackbarManager only shows in MainActivity). */
    private val uzenet = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Tartalom()
                }
            }
        }
    }

    private fun munkamenetek(): List<File> =
        FigyeloRogzito.munkamenetekMappa(this).listFiles { f -> f.isDirectory }?.sortedByDescending { it.name } ?: emptyList()

    @Composable
    private fun Tartalom() {
        var frissites by remember { mutableIntStateOf(0) }
        var nyitott by remember { mutableStateOf<File?>(null) }
        var dolgozik by remember { mutableStateOf(false) }

        val mappa = nyitott
        if (mappa != null) {
            BackHandler { nyitott = null }
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { nyitott = null }) { Text("Vissza") }
                    Button(onClick = { megoszt(mappa, false) }) { Text("Megosztás") }
                    OutlinedButton(onClick = { megoszt(mappa, true) }) { Text("Nyers adatok") }
                }
                UzenetSor()
                val html = remember(mappa, frissites) {
                    File(mappa, Fajlok.JELENTES).takeIf { it.exists() }?.readText()
                        ?: "<p style='font-family:sans-serif;padding:16px'>Ehhez a méréshez még nincs jelentés.</p>"
                }
                WebNezet(html)
            }
            return
        }

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            Text("Figyelő jelentések", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Mérést a játék közben indíthatsz: gyorsmenü → Task Manager fül → Figyelő.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            UzenetSor()
            val lista = remember(frissites) { munkamenetek() }
            if (lista.isEmpty()) {
                Text("Még nincs mérés.", modifier = Modifier.padding(top = 16.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lista, key = { it.path }) { m ->
                    val (cim, alcim) = remember(m, frissites) { leiras(m) }
                    Card(modifier = Modifier.fillMaxWidth().clickable { nyitott = m }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(cim, style = MaterialTheme.typography.titleMedium)
                            Text(alcim, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                                OutlinedButton(
                                    enabled = !dolgozik,
                                    onClick = {
                                        dolgozik = true
                                        Thread {
                                            val siker = runCatching { Kimenet.feldolgoz(m) }
                                                .onFailure { Timber.e(it, "Figyelő: újraelemzés sikertelen") }
                                                .isSuccess
                                            runOnUiThread {
                                                dolgozik = false
                                                frissites++
                                                uzenet.value = if (siker) "Jelentés frissítve: ${m.name}" else "Az elemzés nem sikerült: ${m.name}"
                                            }
                                        }.start()
                                    },
                                ) { Text("Újraelemzés") }
                                Button(onClick = { nyitott = m }) { Text("Megnyitás") }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun UzenetSor() {
        val szoveg = uzenet.value ?: return
        Text(
            szoveg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }

    private fun leiras(m: File): Pair<String, String> {
        val meta = runCatching { JSONObject(File(m, Fajlok.META).readText()) }.getOrNull()
        val cim = meta?.optString("jatek")?.takeIf { it.isNotBlank() } ?: m.name
        val osszegzes = runCatching { JSONObject(File(m, Fajlok.OSSZEGZES).readText()).optJSONObject("osszegzes") }.getOrNull()
        val reszek = ArrayList<String>()
        meta?.optString("kezdes")?.takeIf { it.isNotBlank() }?.let { reszek += it }
        if (osszegzes != null) {
            val hossz = osszegzes.optInt("hosszMp")
            reszek += "%d:%02d".format(hossz / 60, hossz % 60)
            if (osszegzes.has("medianFps")) reszek += "medián ${osszegzes.optDouble("medianFps").toInt()} FPS"
            reszek += "${osszegzes.optInt("esesekSzama")} esés"
        } else {
            reszek += "nincs még jelentés"
        }
        return cim to reszek.joinToString(" · ")
    }

    private fun megoszt(mappa: File, nyers: Boolean) {
        try {
            startActivity(FigyeloExport.megosztas(this, mappa, nyers))
        } catch (e: Exception) {
            Timber.e(e, "Figyelő: megosztás sikertelen")
            uzenet.value = "A megosztás nem sikerült: ${e.message}"
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebNezet(html: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
            }
        },
        update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
    )
}
