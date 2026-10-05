package app.gamenative.figyelo

import android.content.Context
import android.os.SystemClock
import app.gamenative.figyelo.elemzes.Fajlok
import app.gamenative.figyelo.elemzes.Kimenet
import app.gamenative.powercontrol.metrics.FrameTimeRing
import app.gamenative.powercontrol.metrics.PerformanceMetricsCollector
import com.winlator.container.Container
import com.winlator.core.Callback
import com.winlator.core.ProcessHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * GameNative Figyelő recorder: started / stopped from the in-game quick menu. Samples at 1 Hz on
 * its own low-priority thread, writes the JSONL session files, and when stopped (or when the
 * game's processes are gone) runs the analysis and copies the results to Downloads.
 */
object FigyeloRogzito {

    const val MINTAVETEL_MS = 1000L
    private const val NAPLO_MAX_PER_MP = 200
    private const val NAPLO_MAX_HOSSZ = 400
    private const val AUTO_STOP_MP = 15

    data class Allapot(
        val fut: Boolean = false,
        val kezdesMs: Long = 0L,
        val jelekDb: Int = 0,
        val feldolgozas: Boolean = false,
        /** Folder of the most recent session (finished or running). */
        val munkamenet: File? = null,
        /** Short Hungarian status line for the UI. */
        val uzenet: String? = null,
    )

    private val _allapot = MutableStateFlow(Allapot())
    val allapot: StateFlow<Allapot> = _allapot.asStateFlow()

    private val zar = Any()
    private var felvetel: Felvetel? = null

    fun munkamenetekMappa(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "figyelo").apply { mkdirs() }

    fun inditas(context: Context, container: Container?) {
        synchronized(zar) {
            if (felvetel != null) return
            try {
                val f = Felvetel(context.applicationContext, container)
                felvetel = f
                f.inditas()
                _allapot.value = Allapot(fut = true, kezdesMs = f.kezdesFal, munkamenet = f.mappa, uzenet = "Mérés folyamatban")
            } catch (e: Exception) {
                Timber.e(e, "Figyelő: indítás sikertelen")
                felvetel = null
                _allapot.value = Allapot(uzenet = "Nem sikerült elindítani a mérést: ${e.message}")
            }
        }
    }

    fun leallitas() {
        val f = synchronized(zar) {
            val aktualis = felvetel ?: return
            felvetel = null
            aktualis
        }
        Thread({ f.leallitas() }, "FigyeloLeallitas").start()
    }

    fun jeloles(cimke: String) {
        val f = synchronized(zar) { felvetel } ?: return
        f.jel(cimke)
        _allapot.value = _allapot.value.copy(jelekDb = f.jelekDb.get())
    }

    fun fut(): Boolean = synchronized(zar) { felvetel != null }

    private class Felvetel(private val context: Context, private val container: Container?) {
        val kezdesFal = System.currentTimeMillis()
        private val kezdesMono = SystemClock.elapsedRealtime()
        val mappa: File = File(
            munkamenetekMappa(context),
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(kezdesFal)) + "_" +
                FigyeloMeta.jatekNev(container).replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40),
        )
        private val mintavevo = Mintavevo(context)
        private lateinit var mintaIro: BufferedWriter
        private lateinit var kepkockaIro: BufferedWriter
        private lateinit var naploIro: BufferedWriter
        private lateinit var jelIro: BufferedWriter
        private val naploSor = ConcurrentLinkedQueue<Pair<Double, String>>()
        private val naploEbbenAMpben = AtomicInteger(0)
        val jelekDb = AtomicInteger(0)

        @Volatile
        private var fut = false
        private var szal: Thread? = null

        private val naploFigyelo = Callback<String> { sor ->
            if (!fut || sor.isBlank()) return@Callback
            if (naploEbbenAMpben.incrementAndGet() > NAPLO_MAX_PER_MP) return@Callback
            naploSor.add(ido() to sor.take(NAPLO_MAX_HOSSZ))
        }

        fun ido(): Double = (SystemClock.elapsedRealtime() - kezdesMono) / 1000.0

        fun inditas() {
            mappa.mkdirs()
            mintavevo.inditas()
            File(mappa, Fajlok.META).writeText(FigyeloMeta.keszit(context, container, mintavevo, kezdesFal).toString(2))
            mintaIro = File(mappa, Fajlok.MINTA).bufferedWriter()
            kepkockaIro = File(mappa, Fajlok.KEPKOCKAK).bufferedWriter()
            naploIro = File(mappa, Fajlok.NAPLO).bufferedWriter()
            jelIro = File(mappa, Fajlok.JELEK).bufferedWriter()
            fut = true
            ProcessHelper.addDebugCallback(naploFigyelo)
            szal = Thread(::ciklus, "FigyeloMintavevo").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
                start()
            }
            Timber.i("Figyelő: mérés indult, mappa=%s", mappa)
        }

        private fun ciklus() {
            var kovetkezo = SystemClock.elapsedRealtime() + MINTAVETEL_MS
            while (fut) {
                val varakozas = kovetkezo - SystemClock.elapsedRealtime()
                if (varakozas > 0) {
                    try {
                        Thread.sleep(varakozas)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                kovetkezo += MINTAVETEL_MS
                if (!fut) break
                try {
                    egyMinta()
                } catch (e: Throwable) {
                    Timber.w(e, "Figyelő: mintavétel hiba")
                }
                if (mintavevo.uresMasodpercek >= AUTO_STOP_MP) {
                    Timber.i("Figyelő: a játék folyamatai eltűntek, automatikus leállítás")
                    synchronized(zar) { if (felvetel === this) felvetel = null }
                    Thread({ leallitas() }, "FigyeloLeallitas").start()
                    break
                }
            }
        }

        private fun egyMinta() {
            val t = ido()
            val eredmeny = mintavevo.minta(t)
            synchronized(this) {
                mintaIro.write(eredmeny.minta.toString())
                mintaIro.newLine()
                kepkockaIro.write(JSONObject().put("t", eredmeny.minta.optDouble("t")).put("ms", eredmeny.kepkockak).toString())
                kepkockaIro.newLine()
                naploEbbenAMpben.set(0)
                while (true) {
                    val (nt, sor) = naploSor.poll() ?: break
                    naploIro.write(JSONObject().put("t", (nt * 10).toLong() / 10.0).put("sor", sor).toString())
                    naploIro.newLine()
                }
                mintaIro.flush()
                kepkockaIro.flush()
                naploIro.flush()
            }
        }

        fun jel(cimke: String) {
            val t = ido()
            synchronized(this) {
                if (!fut) return
                jelIro.write(JSONObject().put("t", (t * 10).toLong() / 10.0).put("cimke", cimke).toString())
                jelIro.newLine()
                jelIro.flush()
            }
            jelekDb.incrementAndGet()
        }

        fun leallitas() {
            if (!fut) return
            fut = false
            ProcessHelper.removeDebugCallback(naploFigyelo)
            szal?.let { if (it !== Thread.currentThread()) runCatching { it.interrupt(); it.join(3_000) } }
            synchronized(this) {
                runCatching { mintaIro.close() }
                runCatching { kepkockaIro.close() }
                runCatching { naploIro.close() }
                runCatching { jelIro.close() }
            }
            if (mintavevo.sajatKepkockaGyuru && !PerformanceMetricsCollector.isRunning) FrameTimeRing.stop()
            _allapot.value = _allapot.value.copy(fut = false, feldolgozas = true, munkamenet = mappa, uzenet = "Jelentés készül…")
            Thread({ feldolgozas() }, "FigyeloFeldolgozas").start()
        }

        private fun feldolgozas() {
            val uzenet = try {
                Kimenet.feldolgoz(mappa)
                val hova = runCatching { FigyeloExport.letoltesekbe(context, mappa) }
                    .onFailure { Timber.w(it, "Figyelő: másolás a Letöltésekbe sikertelen") }
                    .getOrNull()
                if (hova != null) "Kész. Jelentés: $hova" else "Kész. A jelentés az appban: Jelentések megnyitása"
            } catch (e: Throwable) {
                Timber.e(e, "Figyelő: feldolgozás sikertelen")
                "A jelentés készítése nem sikerült: ${e.message}"
            }
            _allapot.value = _allapot.value.copy(feldolgozas = false, munkamenet = mappa, uzenet = uzenet)
        }
    }
}
