package app.gamenative.figyelo

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import app.gamenative.BuildConfig
import app.gamenative.utils.ContainerUtils
import com.winlator.container.Container
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds meta.json: device, SoC, Android version and the game container's settings. */
internal object FigyeloMeta {

    /** Bumped with every Figyelő change that alters what is recorded, so reports show which build measured. */
    const val FIGYELO_VERZIO = 6

    fun jatekNev(container: Container?): String {
        if (container == null) return "Ismeretlen játék"
        val nev = runCatching { ContainerUtils.resolveGameName(container.id) }.getOrNull()
        return nev?.takeIf { it.isNotBlank() }
            ?: container.getName()?.takeIf { it.isNotBlank() }
            ?: container.getExecutablePath()?.substringAfterLast('\\')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Ismeretlen játék"
    }

    fun keszit(context: Context, container: Container?, mintavevo: Mintavevo, kezdesMs: Long): JSONObject = JSONObject().apply {
        put("formatum", 1)
        put("alkalmazas", "GameNative Figyelő")
        put("verzio", BuildConfig.VERSION_NAME)
        put("figyeloVerzio", FIGYELO_VERZIO)
        put("csomag", BuildConfig.APPLICATION_ID)
        put("kezdes", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(kezdesMs)))
        put("kezdesMs", kezdesMs)
        put("mintavetelMs", FigyeloRogzito.MINTAVETEL_MS)
        put("jatek", jatekNev(container))
        put("eszkoz", eszkoz(context, mintavevo))
        if (container != null) put("kontener", kontener(container))
        put(
            "olvashato",
            JSONObject().apply {
                put("procStat", File("/proc/stat").canRead())
                put("gpuFrek", mintavevo.gpuFrekUtvonal != null)
                putOpt("gpuFrekForras", mintavevo.gpuFrekUtvonal)
                put("cpuFrek", mintavevo.klaszterek.isNotEmpty())
                put("gpuFajlok", gpuDiagnosztika())
            },
        )
    }

    /** Which GPU sysfs nodes exist / are readable on this phone (vendors often block them). */
    private fun gpuDiagnosztika(): JSONObject = JSONObject().apply {
        listOf(
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/kernel/gpu/gpu_model",
        ).forEach { ut ->
            val f = File(ut)
            val allapot = when {
                f.canRead() -> runCatching { f.readText().trim().take(40) }.getOrNull()?.let { "olvashato: $it" } ?: "olvashato"
                runCatching { f.exists() }.getOrDefault(false) -> "tiltott"
                else -> "nincs"
            }
            put(ut, allapot)
        }
    }

    private fun eszkoz(context: Context, mintavevo: Mintavevo): JSONObject = JSONObject().apply {
        put("gyarto", Build.MANUFACTURER)
        put("modell", Build.MODEL)
        put("eszkozNev", Build.DEVICE)
        put("hardver", Build.HARDWARE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put("soc", listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" "))
        } else {
            put("soc", Build.HARDWARE)
        }
        put("android", Build.VERSION.RELEASE)
        put("sdk", Build.VERSION.SDK_INT)
        put("build", Build.DISPLAY)
        runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
        }.getOrNull()?.let { put("kijelzoHz", it.toDouble()) }
        put("magok", Runtime.getRuntime().availableProcessors())
        put(
            "klaszterek",
            JSONArray().apply {
                mintavevo.klaszterek.forEach { k ->
                    put(JSONObject().put("magok", JSONArray(k.magok.toList())).put("maxMhz", (k.maxKhz / 1000L).toInt()))
                }
            },
        )
        mintavevo.memoriaInfo()?.let { put("memoriaMb", it.totalMem / (1024L * 1024L)) }
    }

    private fun kontener(c: Container): JSONObject = JSONObject().apply {
        fun s(kulcs: String, ertek: () -> Any?) {
            runCatching { ertek() }.getOrNull()?.let { if (it !is String || it.isNotBlank()) put(kulcs, it) }
        }
        s("id") { c.id }
        s("nev") { c.getName() }
        s("exe") { c.getExecutablePath() }
        s("inditasiParameterek") { c.getExecArgs() }
        s("emulator") { c.getEmulator() }
        s("box64Verzio") { c.getBox64Version() }
        s("box64Preset") { c.getBox64Preset() }
        s("fexVerzio") { c.getFEXCoreVersion() }
        s("fexPreset") { c.getFEXCorePreset() }
        s("wineVerzio") { c.getWineVersion() }
        s("wow64") { c.isWoW64Mode() }
        s("valtozat") { c.getContainerVariant() }
        s("driver") { c.getGraphicsDriver() }
        s("driverVerzio") { c.getGraphicsDriverVersion() }
        s("driverBeallitas") { c.getGraphicsDriverConfig() }
        s("dxwrapper") { c.getDXWrapper() }
        s("dxwrapperBeallitas") { c.getDXWrapperConfig() }
        s("kepernyo") { c.getScreenSize() }
        s("cpuLista") { c.getCPUList() }
        s("kornyezetiValtozok") { c.getEnvVars() }
        s("hang") { c.getAudioDriver() }
        s("meghajtok") { c.getDrives() }
        runCatching { jatekMappa(c.getDrives()) }.getOrNull()?.let { mappa ->
            put("jatekMappa", mappa)
            put("jatekMappaTipus", helyTipus(mappa))
        }
    }

    /** Game folder = the A: drive (custom games) or the first non-default drive. */
    private fun jatekMappa(drives: String?): String? {
        if (drives.isNullOrBlank()) return null
        val lista = Container.drivesIterator(drives).map { it[0] to it[1] }
        val ut = (
            lista.firstOrNull { it.first == "A" }
                ?: lista.firstOrNull { (_, p) -> !p.endsWith("/Download") && !p.endsWith("${BuildConfig.APPLICATION_ID}/storage") }
            )?.second ?: return null
        return runCatching { File(ut).canonicalPath }.getOrDefault(ut)
    }

    /**
     * internal = the app's own private storage (fast); appSajatKulso = Android/data/<pkg> (bind-mounted, fast);
     * megosztott = shared storage such as Download/ (goes through Android's FUSE layer, slower);
     * sdKartya = removable storage.
     */
    fun helyTipus(ut: String): String = when {
        ut.startsWith("/data/data/") || ut.startsWith("/data/user/") -> "belso"
        ut.contains("/Android/data/${BuildConfig.APPLICATION_ID}") && ut.startsWith("/storage/emulated/") -> "appSajatKulso"
        ut.startsWith("/storage/emulated/") || ut.startsWith("/sdcard") -> "megosztott"
        ut.startsWith("/storage/") -> "sdKartya"
        else -> "ismeretlen"
    }
}
