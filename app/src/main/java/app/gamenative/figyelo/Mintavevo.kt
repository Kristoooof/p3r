package app.gamenative.figyelo

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import app.gamenative.figyelo.elemzes.SzalKategoria
import app.gamenative.powercontrol.metrics.FrameTimeRing
import app.gamenative.powercontrol.metrics.GpuUsageSampler
import app.gamenative.powercontrol.metrics.SystemMetricsSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import android.os.PowerManager as AndroidPowerManager

/**
 * Reads one sample per call (driven at ~1 Hz by [FigyeloRogzito]). Everything is best effort:
 * a source the app cannot read (SELinux / vendor restrictions) is simply left out.
 */
internal class Mintavevo(private val context: Context) {

    private val selfPid = android.os.Process.myPid()
    private val uid = android.os.Process.myUid()
    private val clkTck = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1L)

    val klaszterek: List<Klaszter> = runCatching { klaszterekKeresese() }.getOrDefault(emptyList())
    private val magDb: Int = runCatching { magokSzama() }.getOrDefault(Runtime.getRuntime().availableProcessors())
    val gpuFrekUtvonal: String? = runCatching { gpuFrekKeresese() }.getOrNull()
    private val borUtvonalak: List<String> = runCatching { borHoKeresese() }.getOrDefault(emptyList())
    private val cpuHoUtvonalak: List<String> = runCatching { SystemMetricsSources.cpuTempPaths() }.getOrDefault(emptyList())

    private val gpuMintavevo = GpuUsageSampler()
    private val gyorsitotar = FajlGyorsitotar()
    private val lapMeret = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(4096L).coerceAtLeast(1L)
    private var utolsoGyorsitotar = 0L
    private var megallitottSzal = 0
    private var osszesSzal = 0
    private var jatekPidek: List<Int> = emptyList()
    var gpuForras: String? = null
        private set

    private var elozoCpuStat: Map<String, LongArray>? = null
    private var elozoFolyamatTick = HashMap<Int, LongArray>()
    private var elozoSzalTick = HashMap<Long, LongArray>()
    private var elozoIo = HashMap<Int, LongArray>()
    private val folyamatNevek = HashMap<Int, String>()
    private var folyamatok: List<Int> = emptyList()
    private var utolsoFolyamatKereses = 0L

    private val kepkockaPuffer = LongArray(FrameTimeRing.capacity())
    private var utolsoKepkockaNs = 0L
    private var elozoKockaNs = 0L
    private var elozoMs = 0L

    /** True when this sampler (re)started [FrameTimeRing] and should stop it again. */
    var sajatKepkockaGyuru = false
        private set

    /** Seconds in a row without any game-side process; used for auto-stop. */
    var uresMasodpercek = 0
        private set

    class Klaszter(val magok: IntArray, val maxKhz: Long)

    class Eredmeny(val minta: JSONObject, val kepkockak: JSONArray)

    fun inditas() {
        if (!FrameTimeRing.isRecording()) {
            FrameTimeRing.start()
            sajatKepkockaGyuru = true
        }
        utolsoKepkockaNs = System.nanoTime()
        elozoMs = SystemClock.elapsedRealtime()
        runCatching { cpuStat() }
        runCatching { folyamatKereses(force = true) }
        runCatching { folyamatMinta(0.0) }
        runCatching { gpuMintavevo.sample() }
    }

    fun minta(t: Double): Eredmeny {
        val most = SystemClock.elapsedRealtime()
        val dt = ((most - elozoMs) / 1000.0).coerceAtLeast(0.001)
        elozoMs = most

        val o = JSONObject()
        o.put("t", (t * 10).roundToInt() / 10.0)

        // FrameTimeRing is fed by the X server present path (FrameRating.update); it has to be
        // recording for frame times. Other owners may stop it, so make sure it keeps running.
        if (!FrameTimeRing.isRecording()) {
            FrameTimeRing.start()
            sajatKepkockaGyuru = true
        }
        val kockak = kepkockak()
        o.put("fps", ((kockak.length() / dt) * 10).roundToInt() / 10.0)

        biztos { cpuStat() }?.let { (osszes, iow, magok) ->
            o.put("cpu", osszes)
            o.put("iow", iow)
            o.put("magok", JSONArray(magok.toList()))
        }
        if (klaszterek.isNotEmpty()) {
            biztos { klaszterFrek("scaling_cur_freq") }?.let { o.put("frek", JSONArray(it.toList())) }
            biztos { klaszterFrek("scaling_max_freq") }?.let { o.put("frekMax", JSONArray(it.toList())) }
        }
        biztos { gpuMintavevo.sample() }?.let {
            gpuForras = it.source
            o.put("gpu", it.percent)
        }
        biztos { gpuMhz() }?.let { o.put("gpuMhz", it) }

        val hom = JSONObject()
        biztos { SystemMetricsSources.readTemperatureC(cpuHoUtvonalak) }?.let { hom.put("cpu", it) }
        biztos { akkuHo() }?.let { hom.put("akku", it) }
        if (borUtvonalak.isNotEmpty()) biztos { SystemMetricsSources.readTemperatureC(borUtvonalak) }?.let { hom.put("bor", it) }
        if (hom.length() > 0) o.put("hom", hom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            biztos { (context.getSystemService(Context.POWER_SERVICE) as? AndroidPowerManager)?.currentThermalStatus }
                ?.let { o.put("hoAllapot", it) }
        }
        biztos { memoriaInfo()?.availMem?.div(1024L * 1024L)?.toInt() }?.let { o.put("memSzabadMb", it) }

        biztos { folyamatKereses(force = false) }
        biztos { folyamatMinta(dt) }?.let { (procs, szalak) ->
            o.put("folyamatok", procs)
            o.put("szalak", szalak)
        }
        uresMasodpercek = if (folyamatok.isEmpty()) uresMasodpercek + 1 else 0

        // Paused by GameNative (quick menu / overlay → SIGSTOP) — such seconds are not drops.
        val overlaySzunet = runCatching { app.gamenative.PluviaApp.isOverlayPaused }.getOrDefault(false)
        if (overlaySzunet || (osszesSzal > 0 && megallitottSzal * 2 > osszesSzal)) o.put("szunet", 1)

        if (most - utolsoGyorsitotar >= GYORSITOTAR_MS && jatekPidek.isNotEmpty()) {
            utolsoGyorsitotar = most
            biztos {
                gyorsitotar.frissit(jatekPidek, most)
                gyorsitotar.pillanatkep()
            }?.takeIf { it.length() > 0 }?.let { o.put("gyorsitotar", it) }
        }

        return Eredmeny(o, kockak)
    }

    private fun kepkockak(): JSONArray {
        val ms = JSONArray()
        val db = FrameTimeRing.copySince(utolsoKepkockaNs + 1, kepkockaPuffer)
        if (db <= 0) return ms
        for (i in 0 until db) {
            val ts = kepkockaPuffer[i]
            val delta = ts - elozoKockaNs
            if (elozoKockaNs > 0L && delta > 0L) ms.put((delta / 100_000L) / 10.0)
            elozoKockaNs = ts
        }
        utolsoKepkockaNs = kepkockaPuffer[db - 1]
        return ms
    }

    private fun cpuStat(): Triple<Int, Int, IntArray>? {
        val most = HashMap<String, LongArray>()
        File("/proc/stat").bufferedReader().useLines { lines ->
            for (line in lines) {
                if (!line.startsWith("cpu")) break
                val parts = line.split(' ').filter { it.isNotEmpty() }
                if (parts.size < 5) continue
                val v = parts.drop(1).take(8).mapNotNull { it.toLongOrNull() }
                most[parts[0]] = longArrayOf(v.sum(), v.getOrElse(3) { 0L } + v.getOrElse(4) { 0L }, v.getOrElse(4) { 0L })
            }
        }
        val elozo = elozoCpuStat
        elozoCpuStat = most
        if (elozo == null) return null
        fun d(kulcs: String): Pair<Int, Int>? {
            val a = elozo[kulcs] ?: return null
            val b = most[kulcs] ?: return null
            val ossz = b[0] - a[0]
            if (ossz <= 0L) return null
            val foglalt = ((ossz - (b[1] - a[1])).coerceAtLeast(0L) * 100L / ossz).toInt().coerceIn(0, 100)
            val iow = ((b[2] - a[2]).coerceAtLeast(0L) * 100L / ossz).toInt().coerceIn(0, 100)
            return foglalt to iow
        }
        val mind = d("cpu") ?: return null
        return Triple(mind.first, mind.second, IntArray(magDb) { d("cpu$it")?.first ?: -1 })
    }

    private fun folyamatKereses(force: Boolean) {
        val most = SystemClock.elapsedRealtime()
        if (!force && most - utolsoFolyamatKereses < 3_000L && folyamatok.isNotEmpty()) return
        utolsoFolyamatKereses = most
        val dirs = File("/proc").listFiles { f -> f.name.isNotEmpty() && f.name[0].isDigit() } ?: return
        val talalt = ArrayList<Int>()
        for (dir in dirs) {
            val pid = dir.name.toIntOrNull() ?: continue
            if (pid == selfPid) continue
            try {
                if (Os.stat(dir.path).st_uid != uid) continue
                talalt += pid
                folyamatNevek.getOrPut(pid) { folyamatNev(dir) }
            } catch (_: Exception) {
            }
        }
        folyamatok = talalt
        folyamatNevek.keys.retainAll(talalt.toSet())
    }

    fun leallitas() {
        runCatching { gyorsitotar.bezar() }
    }

    private fun folyamatMinta(dt: Double): Pair<JSONArray, JSONArray> {
        val procs = JSONArray()
        var megallitott = 0
        var osszes = 0
        val jatek = ArrayList<Int>()
        val szalLista = ArrayList<JSONObject>()
        val ujFolyamatTick = HashMap<Int, LongArray>()
        val ujSzalTick = HashMap<Long, LongArray>()
        val ujIo = HashMap<Int, LongArray>()
        for (pid in folyamatok) {
            val nev = folyamatNevek[pid] ?: continue
            val stat = try {
                statOlvas(File("/proc/$pid/stat").readText())
            } catch (_: Exception) {
                null
            } ?: continue
            val szamlalok = longArrayOf(stat.utime + stat.stime, stat.majflt, stat.blkio)
            val jatekFolyamat = nev.endsWith(".exe", ignoreCase = true) && nev.lowercase() !in WINE_FOLYAMATOK
            if (jatekFolyamat) jatek += pid
            ujFolyamatTick[pid] = szamlalok
            val elozo = elozoFolyamatTick[pid]
            val io = try {
                ioOlvas(pid)
            } catch (_: Exception) {
                null
            }
            if (io != null) ujIo[pid] = io
            if (elozo != null && dt > 0.0) {
                val p = JSONObject().put("pid", pid).put("nev", nev).put("cpu", szazalek(szamlalok[0] - elozo[0], dt))
                val elozoIo = elozoIo[pid]
                if (io != null && elozoIo != null) {
                    p.put("olv", ((io[0] - elozoIo[0]).coerceAtLeast(0L) / dt).toLong())
                    p.put("rchar", ((io[1] - elozoIo[1]).coerceAtLeast(0L) / dt).toLong())
                }
                val mf = ((szamlalok[1] - elozo[1]).coerceAtLeast(0L) / dt).roundToInt()
                if (mf > 0) p.put("mf", mf)
                // delayacct_blkio_ticks: time spent waiting for block I/O (0 if the kernel has delay accounting off)
                val blk = szazalek(szamlalok[2] - elozo[2], dt)
                if (blk > 0) p.put("blk", blk)
                runCatching {
                    File("/proc/$pid/statm").readText().trim().split(' ').getOrNull(1)?.toLongOrNull()
                }.getOrNull()?.let { p.put("rssMb", it * lapMeret / (1024L * 1024L)) }
                procs.put(p)
            }
            val taskok = File("/proc/$pid/task").listFiles() ?: continue
            for (task in taskok) {
                val tid = task.name.toIntOrNull() ?: continue
                val ts = try {
                    statOlvas(File(task, "stat").readText())
                } catch (_: Exception) {
                    null
                } ?: continue
                if (jatekFolyamat) {
                    osszes++
                    if (ts.allapot == 'T' || ts.allapot == 't') megallitott++
                }
                val kulcs = (pid.toLong() shl 32) or tid.toLong()
                val most = longArrayOf(ts.utime + ts.stime, ts.majflt)
                ujSzalTick[kulcs] = most
                val e = elozoSzalTick[kulcs] ?: continue
                if (dt <= 0.0) continue
                val cpu = szazalek(most[0] - e[0], dt)
                val mf = ((most[1] - e[1]).coerceAtLeast(0L) / dt).roundToInt()
                // Threads blocked on I/O ('D') use no CPU, so keep them even below 1%.
                if (cpu < 1 && mf == 0 && ts.allapot != 'D') continue
                szalLista += JSONObject()
                    .put("pid", pid)
                    .put("tid", tid)
                    .put("nev", ts.comm)
                    .put("kat", SzalKategoria.besorol(ts.comm, tid == pid, nev))
                    .put("cpu", cpu)
                    .put("all", ts.allapot.toString())
                    .apply { if (mf > 0) put("mf", mf) }
            }
        }
        elozoFolyamatTick = ujFolyamatTick
        elozoSzalTick = ujSzalTick
        elozoIo = ujIo
        megallitottSzal = megallitott
        osszesSzal = osszes
        jatekPidek = jatek
        val szalak = JSONArray()
        szalLista.sortedWith(
            compareByDescending<JSONObject> { it.optInt("cpu") + if (it.optString("all") == "D") 1000 else 0 }
                .thenByDescending { it.optInt("mf") },
        ).take(MAX_SZAL).forEach { szalak.put(it) }
        return procs to szalak
    }

    private class Stat(
        val comm: String,
        val allapot: Char,
        val utime: Long,
        val stime: Long,
        val majflt: Long,
        val blkio: Long,
    )

    private fun statOlvas(stat: String): Stat? {
        val nyit = stat.indexOf('(')
        val zar = stat.lastIndexOf(')')
        if (nyit < 0 || zar < nyit || zar + 2 > stat.length) return null
        // Fields after "(comm) ": state ppid pgrp session tty tpgid flags minflt cminflt majflt cmajflt utime stime …
        val tobbi = stat.substring(zar + 2).split(' ')
        if (tobbi.size < 13) return null
        return Stat(
            comm = stat.substring(nyit + 1, zar),
            allapot = tobbi[0].firstOrNull() ?: '?',
            utime = tobbi[11].toLongOrNull() ?: return null,
            stime = tobbi[12].toLongOrNull() ?: return null,
            majflt = tobbi[9].toLongOrNull() ?: 0L,
            blkio = tobbi.getOrNull(39)?.toLongOrNull() ?: 0L,
        )
    }

    /** [read_bytes, rchar] from /proc/pid/io */
    private fun ioOlvas(pid: Int): LongArray? {
        var readBytes = -1L
        var rchar = -1L
        File("/proc/$pid/io").bufferedReader().useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith("read_bytes:") -> readBytes = line.substringAfter(':').trim().toLongOrNull() ?: -1L
                    line.startsWith("rchar:") -> rchar = line.substringAfter(':').trim().toLongOrNull() ?: -1L
                }
            }
        }
        if (readBytes < 0L && rchar < 0L) return null
        return longArrayOf(readBytes.coerceAtLeast(0L), rchar.coerceAtLeast(0L))
    }

    private fun folyamatNev(dir: File): String {
        val args = try {
            String(File(dir, "cmdline").readBytes()).split('\u0000').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
        val exe = args.firstOrNull { it.endsWith(".exe", ignoreCase = true) }
            ?: args.firstOrNull { it.contains(".exe", ignoreCase = true) }
        // On modern Android every Wine-side binary is started as "/system/bin/linker64 <binary>",
        // so skip loader / emulator wrappers to get e.g. "wineserver" instead of "linker64".
        val program = args.map { it.substringAfterLast('/') }.firstOrNull { a ->
            a.isNotEmpty() && !a.startsWith("-") && betoltoNevek.none { a.equals(it, ignoreCase = true) } && !a.startsWith("ld-")
        }
        val comm = runCatching { File(dir, "comm").readText().trim() }.getOrDefault("?")
        return (exe ?: program ?: comm).substringAfterLast('/').substringAfterLast('\\').take(60)
    }

    private fun szazalek(tick: Long, dt: Double): Int = (tick.coerceAtLeast(0L) * 100.0 / (clkTck * dt)).roundToInt()

    private fun klaszterFrek(fajl: String): IntArray = IntArray(klaszterek.size) { i ->
        var mhz = -1
        for (mag in klaszterek[i].magok) {
            val khz = SystemMetricsSources.readLongFromLine("/sys/devices/system/cpu/cpu$mag/cpufreq/$fajl") ?: continue
            mhz = (khz / 1000L).toInt()
            break
        }
        mhz
    }

    private fun gpuMhz(): Int? {
        val raw = SystemMetricsSources.readLongFromLine(gpuFrekUtvonal ?: return null) ?: return null
        return when {
            raw >= 100_000_000L -> (raw / 1_000_000L).toInt()
            raw >= 100_000L -> (raw / 1_000L).toInt()
            else -> raw.toInt()
        }
    }

    private fun akkuHo(): Int? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        return intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0).takeIf { it > 0 }?.let { (it / 10f).roundToInt() }
    }

    fun memoriaInfo(): ActivityManager.MemoryInfo? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    }

    private fun magokSzama(): Int {
        val dirs = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
        val max = dirs?.maxOfOrNull { it.name.removePrefix("cpu").toInt() } ?: -1
        return if (max >= 0) max + 1 else Runtime.getRuntime().availableProcessors()
    }

    private fun klaszterekKeresese(): List<Klaszter> {
        val dirs = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) } ?: return emptyList()
        val szerint = java.util.TreeMap<Long, MutableList<Int>>()
        for (dir in dirs) {
            val index = dir.name.removePrefix("cpu").toIntOrNull() ?: continue
            val max = SystemMetricsSources.readLongFromLine("${dir.path}/cpufreq/cpuinfo_max_freq") ?: continue
            szerint.getOrPut(max) { mutableListOf() } += index
        }
        return szerint.map { (max, magok) -> Klaszter(magok.sorted().toIntArray(), max) }
    }

    private fun gpuFrekKeresese(): String? {
        listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/clock_mhz",
        ).firstOrNull { File(it).canRead() }?.let { return it }
        for (gyoker in listOf(File("/sys/class/devfreq"), File("/sys/devices/virtual/devfreq"))) {
            val nodes = gyoker.listFiles { f -> f.isDirectory } ?: continue
            for (node in nodes) {
                val p = node.path.lowercase(Locale.US)
                if (listOf("gpu", "kgsl", "mali", "g3d").none { p.contains(it) }) continue
                val f = File(node, "cur_freq")
                if (f.canRead()) return f.path
            }
        }
        return null
    }

    private fun borHoKeresese(): List<String> =
        listOf(File("/sys/class/thermal"), File("/sys/devices/virtual/thermal")).flatMap { gyoker ->
            val zonak = gyoker.listFiles { f -> f.isDirectory && f.name.startsWith("thermal_zone") } ?: return@flatMap emptyList()
            zonak.mapNotNull { z ->
                val tipus = SystemMetricsSources.readFirstLine(File(z, "type").path)?.trim()?.lowercase(Locale.US)
                    ?: return@mapNotNull null
                if (tipus.contains("skin")) File(z, "temp").path else null
            }
        }.distinct()

    private inline fun <T> biztos(block: () -> T?): T? = try {
        block()
    } catch (_: Throwable) {
        null
    }

    companion object {
        private const val MAX_SZAL = 40
        private const val GYORSITOTAR_MS = 5_000L
        private val WINE_FOLYAMATOK = setOf(
            "services.exe", "explorer.exe", "winedevice.exe", "svchost.exe", "plugplay.exe", "rpcss.exe",
            "start.exe", "winhandler.exe", "tabtip.exe", "conhost.exe", "steam.exe", "steamwebhelper.exe",
        )
        private val betoltoNevek = listOf("linker64", "linker", "box64", "box86", "FEXInterpreter", "FEXLoader", "wine64-preloader", "wine-preloader")
    }
}
