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
            },
        )
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
    }
}
