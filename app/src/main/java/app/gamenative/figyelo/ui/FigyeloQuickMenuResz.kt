package app.gamenative.figyelo.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gamenative.figyelo.FigyeloRogzito
import app.gamenative.ui.component.QuickMenuDetailRow
import app.gamenative.ui.component.QuickMenuSectionHeader
import app.gamenative.ui.theme.PluviaTheme
import com.winlator.container.Container
import kotlinx.coroutines.delay

/** Markers offered in the quick menu; free text is awkward while a game is running. */
private val JELOLESEK = listOf("menü", "betöltés", "harc", "egyéb")

/** "Figyelő" block at the top of the quick menu's Task Manager tab. */
@Composable
fun FigyeloQuickMenuResz(
    container: Container?,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val allapot by FigyeloRogzito.allapot.collectAsState()
    val accent = PluviaTheme.colors.accentCyan
    var most by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(allapot.fut) {
        while (allapot.fut) {
            most = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Column {
        QuickMenuSectionHeader(
            title = "Figyelő – teljesítménymérés",
            subtitle = "Rögzíti az FPS-t, a CPU- és GPU-terhelést, a szálakat és a naplót.",
        )
        if (allapot.fut) {
            val mp = ((most - allapot.kezdesMs) / 1000L).coerceAtLeast(0L)
            QuickMenuDetailRow(
                title = "Mérés leállítása",
                subtitle = "Fut: %d:%02d · jelölések: %d".format(mp / 60, mp % 60, allapot.jelekDb),
                accentColor = PluviaTheme.colors.accentDanger,
                onActivate = { FigyeloRogzito.leallitas() },
                focusRequester = firstItemFocusRequester,
            )
            JELOLESEK.forEach { cimke ->
                QuickMenuDetailRow(
                    title = "Jelölés: $cimke",
                    subtitle = "Nyomd meg, amikor ez történik a játékban",
                    accentColor = accent,
                    onActivate = { FigyeloRogzito.jeloles(cimke) },
                )
            }
        } else {
            QuickMenuDetailRow(
                title = if (allapot.feldolgozas) "Jelentés készül…" else "Mérés indítása",
                subtitle = if (allapot.feldolgozas) "Pár másodperc" else "Indítsd el, majd játssz 10–15 percet",
                accentColor = PluviaTheme.colors.accentSuccess,
                onActivate = { if (!allapot.feldolgozas) FigyeloRogzito.inditas(context, container) },
                focusRequester = firstItemFocusRequester,
            )
            QuickMenuDetailRow(
                title = "Jelentések megnyitása",
                subtitle = "A korábbi mérések jelentései",
                accentColor = accent,
                onActivate = {
                    context.startActivity(
                        Intent(context, FigyeloJelentesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
        allapot.uzenet?.takeIf { !allapot.fut }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
