package app.gamenative.figyelo.elemzes

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One second of the merged timeline. */
data class Masodperc(
    val t: Int,
    val fps: Double?,
    val ftMed: Double?,
    val ftMax: Double?,
    val kepkockaDb: Int,
    val cpu: Int?,
    val iowait: Int?,
    val gpu: Int?,
    val gpuMhz: Int?,
    val topSzal: Szal?,
    /** summed CPU% of all recorded threads per category */
    val katCpu: Map<String, Int>,
    val olvasasMBs: Double,
    val rcharMBs: Double,
    /** major page faults / s summed over the game-side processes */
    val laphiba: Int,
    /** max % of the second a process waited for block I/O (0 without kernel delay accounting) */
    val blkio: Int,
    /** names of threads blocked in uninterruptible ('D', usually I/O) state */
    val dSzalak: List<String>,
    /** scaling_max_freq / cpuinfo_max_freq of the fastest cluster, 0..1 */
    val frekArany: Double?,
    val memSzabadMb: Int?,
    val homCpu: Int?,
    val hoAllapot: Int?,
    val shaderNaplo: Int,
    val naploDb: Int,
    val jelek: List<String>,
    /** paused by GameNative (quick menu / overlay): never a drop, left out of the baselines */
    val szunet: Boolean = false,
    /** summed RSS of the game's .exe processes */
    val jatekRssMb: Int? = null,
    /** swapped-out (zram) memory of the game's .exe processes */
    val jatekSwapMb: Int? = null,
    /** % of the last 10 s with tasks stalled on memory (PSI), if readable */
    val psiMem: Double? = null,
    var eses: Boolean = false,
) {
    /** CPU% of the threads that normally keep a frame going (render + task workers). */
    val dolgozoCpu: Int get() = (katCpu[SzalKategoria.RENDER] ?: 0) + (katCpu[SzalKategoria.MUNKA] ?: 0)
}

object Ok {
    const val SHADER = "shader"
    const val BETOLTES = "betoltes"
    const val HATTERTAR = "hattertar"
    const val CPU = "cpu"
    const val GPU = "gpu"
    const val VARAKOZAS = "varakozas"
    const val HO = "ho"
    const val MEMORIA = "memoria"
    const val ISMERETLEN = "ismeretlen"

    /** Not a per-drop cause: CPU frequency limited for the whole session (power / game mode). */
    const val ORAJEL_KORLAT = "orajelKorlat"

    val MIND = listOf(SHADER, BETOLTES, HATTERTAR, CPU, GPU, VARAKOZAS, HO, MEMORIA)

    fun cimke(ok: String): String = when (ok) {
        SHADER -> "Shaderfordítás"
        BETOLTES -> "Betöltés (CPU-n kitömörítés)"
        HATTERTAR -> "Lassú fájlolvasás"
        CPU -> "CPU-kötött (egy szál a szűk keresztmetszet)"
        GPU -> "GPU-kötött"
        VARAKOZAS -> "A játék vár (GPU vagy szinkron, becsült)"
        HO -> "Melegedés miatti lassítás"
        MEMORIA -> "Memóriahiány (a rendszer elveszi a játék memóriáját)"
        ORAJEL_KORLAT -> "Korlátozott CPU-órajel"
        else -> "Nem egyértelmű"
    }
}

data class Eses(
    val kezdet: Int,
    val veg: Int,
    val minFps: Double?,
    val maxFt: Double?,
    val pontok: Map<String, Double>,
    val fooOk: String,
    val bizonyitekok: List<String>,
    val jelek: List<String>,
) {
    val hossz: Int get() = veg - kezdet + 1
}

object Szint {
    const val MAGAS = "MAGAS"
    const val KOZEPES = "KOZEPES"
    const val ALACSONY = "ALACSONY"

    fun cimke(szint: String): String = when (szint) {
        MAGAS -> "Magas"
        KOZEPES -> "Közepes"
        else -> "Alacsony"
    }
}

data class Javaslat(
    val szint: String,
    val ok: String,
    val cim: String,
    val szoveg: String,
    /** share of drop seconds with this main cause, 0..1 */
    val arany: Double,
)

data class SzalOsszegzes(val nev: String, val kategoria: String, val atlagCpu: Double, val maxCpu: Int)

/** Page-cache history of one big game file over the session. */
data class FajlOsszegzes(
    val nev: String,
    val meretMb: Long,
    val maxBentMb: Long,
    val utolsoBentMb: Long,
    /** sum of increases between snapshots = data paged in */
    val beolvasottMb: Long,
    /** sum of decreases = data evicted from memory */
    val kiszorultMb: Long,
)

data class Elemzes(
    val idovonal: List<Masodperc>,
    val esesek: List<Eses>,
    val javaslatok: List<Javaslat>,
    val hosszMp: Int,
    val medianFps: Double?,
    val atlagFps: Double?,
    val egySzazalekLowFps: Double?,
    val medianFtMs: Double?,
    val esesIdoMp: Int,
    val kategoriaAtlag: Map<String, Double>,
    val topSzalak: List<SzalOsszegzes>,
    val gpuOlvashato: Boolean,
    val frekOlvashato: Boolean,
    val homOlvashato: Boolean,
    /** share of busy threads that have a real (non-generic) name, 0..1, null without threads */
    val szalnevArany: Double?,
    val okEloszlas: Map<String, Int>,
    /** seconds before the first rendered frame (initial loading), not analysed for drops */
    val kezdoBetoltesMp: Int = 0,
    /** typical (90th percentile) CPU frequency ceiling of the fastest cluster vs its hardware max, 0..1 */
    val orajelPlafon: Double? = null,
    /** per cluster: typical ceiling MHz to hardware max MHz */
    val klaszterPlafon: List<Pair<Int, Int>> = emptyList(),
    /** game folder location type from meta (belso / appSajatKulso / megosztott / sdKartya) */
    val jatekHely: String? = null,
    val jatekMappa: String? = null,
    val szunetMp: Int = 0,
    val fajlok: List<FajlOsszegzes> = emptyList(),
    val maxJatekRssMb: Int? = null,
    val minJatekRssMb: Int? = null,
    val maxJatekSwapMb: Int? = null,
    /** how often the game's memory was taken back by the system (RSS −150 MB within 5 s) */
    val memoriaElvetelDb: Int = 0,
    /** Max Device Memory (WRAPPER_VMEM_MAX_SIZE) from the container, MB; 0 = unlimited */
    val maxEszkozMemoria: Int? = null,
    /** session medians of the memory breakdown in MB (keys as in the `memoria` sample field, plus swapHasznalt) */
    val memoriaMegoszlas: Map<String, Int> = emptyMap(),
)

/**
 * Kotlin port of the Figyelő "rendszerező": builds a per-second timeline, finds frame-rate
 * drops and scores their likely causes from the recorded evidence.
 */
object Rendszerezo {

    const val ESES_FPS_ARANY = 0.6
    const val ESES_FT_MIN_MS = 100.0
    const val ESES_FT_SZORZO = 4.0
    const val OSSZEVONAS_RES_MP = 1
    const val FO_OK_MIN_PONT = 0.4

    const val TELITETT_SZAL = 85
    const val GPU_SZABAD = 75
    const val GPU_TELITETT = 90
    const val FREK_KORLAT = 0.9
    const val HO_CSOKKENES = 0.9
    const val MEM_KEVES_MB = 600
    const val OLVASAS_SOK_MBS = 40.0
    const val VARAKOZAS_ARANY = 0.6
    const val FAJL_ABLAK_MP = 6

    private class Kontextus(
        val orajelAlap: Double?,
        val normalOlvasas: Double,
        val normalLaphiba: Double,
        val normalDolgozo: Double,
        val gpuOlvashato: Boolean,
        /** file name -> (t, resident MB) snapshots */
        val fajlIdovonal: Map<String, List<Pair<Double, Long>>>,
        val idovonal: List<Masodperc>,
    )

    fun elemez(m: Munkamenet): Elemzes {
        val idovonal = idovonal(m)
        // Initial loading (before the first frame) and the tail after the game closed are not drops.
        val elso = idovonal.indexOfFirst { (it.fps ?: 0.0) > 0.0 }
        val utolso = idovonal.indexOfLast { (it.fps ?: 0.0) > 0.0 }
        val tartomany = if (elso >= 0) idovonal.subList(elso, utolso + 1) else emptyList()
        val jatekban = tartomany.filter { !it.szunet }

        val fpsErtekek = jatekban.mapNotNull { it.fps }.filter { it > 0.0 }
        val medianFps = median(fpsErtekek)
        val osszesFt = m.kepkockak.flatMap { it.ms }
        val medianFt = median(osszesFt)
        val ftKorlat = medianFt?.let { max(ESES_FT_MIN_MS, ESES_FT_SZORZO * it) } ?: ESES_FT_MIN_MS

        for (s in tartomany) {
            if (s.szunet) continue
            val fpsEses = s.fps != null && medianFps != null && (s.fps == 0.0 || s.fps < ESES_FPS_ARANY * medianFps)
            val ftEses = s.ftMax != null && s.ftMax > ftKorlat
            s.eses = fpsEses || ftEses
        }

        val nyugodt = jatekban.filter { !it.eses }
        val orajelAlap = percentilis(jatekban.mapNotNull { it.frekArany }, 0.9)
        val fajlIdovonal = fajlIdovonal(m)
        val gpuOlvashato = idovonal.any { it.gpu != null }
        val kontextus = Kontextus(
            orajelAlap = orajelAlap,
            normalOlvasas = median(nyugodt.map { it.olvasasMBs }) ?: 0.0,
            normalLaphiba = median(nyugodt.map { it.laphiba.toDouble() }) ?: 0.0,
            normalDolgozo = median(nyugodt.map { it.dolgozoCpu.toDouble() }) ?: 0.0,
            gpuOlvashato = gpuOlvashato,
            fajlIdovonal = fajlIdovonal,
            idovonal = jatekban,
        )

        val esesek = csoportosit(tartomany).map { ablak -> esesElemzes(ablak, kontextus) }
        val okEloszlas = HashMap<String, Int>()
        esesek.forEach { okEloszlas[it.fooOk] = (okEloszlas[it.fooOk] ?: 0) + it.hossz }
        val esesIdo = esesek.sumOf { it.hossz }

        val szalStat = HashMap<String, Pair<String, MutableList<Int>>>()
        val katOsszeg = HashMap<String, Double>()
        for (minta in m.mintak) {
            for (szal in minta.szalak) {
                szalStat.getOrPut(szal.nev) { szal.kategoria to ArrayList() }.second += szal.cpu
            }
        }
        for (s in idovonal) s.katCpu.forEach { (k, v) -> katOsszeg[k] = (katOsszeg[k] ?: 0.0) + v }
        val mintaDb = idovonal.size.coerceAtLeast(1)
        val topSzalak = szalStat.map { (nev, adat) ->
            SzalOsszegzes(nev, adat.first, adat.second.sum().toDouble() / mintaDb, adat.second.maxOrNull() ?: 0)
        }.sortedByDescending { it.atlagCpu }.take(12)

        val folyamatNevek = m.mintak.flatMap { it.folyamatok }.map { it.nev.lowercase().take(15) }.toSet()
        val elfoglaltSzalak = m.mintak.flatMap { it.szalak }.filter { it.cpu >= 5 }
        val szalnevArany = if (elfoglaltSzalak.isEmpty()) {
            null
        } else {
            elfoglaltSzalak.count { !generikusSzalnev(it.nev, folyamatNevek) }.toDouble() / elfoglaltSzalak.size
        }

        val atlagFps = if (fpsErtekek.isEmpty()) null else fpsErtekek.average()
        val egySzazalek = if (osszesFt.size >= 100) percentilis(osszesFt, 0.99)?.let { 1000.0 / it } else null
        val kontener = m.meta.optJSONObject("kontener")

        val elemzes = Elemzes(
            idovonal = idovonal,
            esesek = esesek,
            javaslatok = emptyList(),
            hosszMp = idovonal.lastOrNull()?.t ?: 0,
            medianFps = medianFps,
            atlagFps = atlagFps,
            egySzazalekLowFps = egySzazalek,
            medianFtMs = medianFt,
            esesIdoMp = esesIdo,
            kategoriaAtlag = katOsszeg.mapValues { it.value / mintaDb },
            topSzalak = topSzalak,
            gpuOlvashato = gpuOlvashato,
            frekOlvashato = idovonal.any { it.frekArany != null },
            homOlvashato = idovonal.any { it.homCpu != null },
            szalnevArany = szalnevArany,
            okEloszlas = okEloszlas,
            kezdoBetoltesMp = if (elso > 0) idovonal[elso].t - idovonal.first().t else 0,
            orajelPlafon = orajelAlap,
            klaszterPlafon = klaszterPlafon(m),
            jatekHely = kontener?.optString("jatekMappaTipus")?.takeIf { it.isNotBlank() },
            jatekMappa = kontener?.optString("jatekMappa")?.takeIf { it.isNotBlank() },
            szunetMp = tartomany.count { it.szunet },
            fajlok = fajlOsszegzes(fajlIdovonal, m),
            maxJatekRssMb = idovonal.mapNotNull { it.jatekRssMb }.maxOrNull(),
            minJatekRssMb = jatekban.mapNotNull { it.jatekRssMb }.minOrNull(),
            maxJatekSwapMb = idovonal.mapNotNull { it.jatekSwapMb }.maxOrNull(),
            memoriaElvetelDb = jatekban.indices.count { i -> memoriaElvetel(jatekban, i) },
            memoriaMegoszlas = memoriaMegoszlas(m),
            maxEszkozMemoria = kontener?.optString("driverBeallitas")?.let { Regex("maxDeviceMemory=(\\d+)").find(it) }
                ?.groupValues?.get(1)?.toIntOrNull(),
        )
        return elemzes.copy(javaslatok = javaslatok(elemzes))
    }

    fun idovonal(m: Munkamenet): List<Masodperc> {
        val kepkockak = m.kepkockak.associateBy { it.t.roundToInt() }
        val naploMp = HashMap<Int, IntArray>()
        for (n in m.naplo) {
            val mp = ceil(n.t - 1e-6).toInt().coerceAtLeast(0)
            val darab = naploMp.getOrPut(mp) { IntArray(2) }
            darab[0]++
            if (SzalKategoria.shaderNaploSor(n.sor)) darab[1]++
        }
        val jelMp = m.jelek.groupBy({ ceil(it.t - 1e-6).toInt().coerceAtLeast(0) }, { it.cimke })
        val hwMax = klaszterMaxMhz(m)

        return m.mintak.sortedBy { it.t }.map { minta ->
            val t = minta.t.roundToInt()
            val kocka = kepkockak[t]
            val katCpu = HashMap<String, Int>()
            minta.szalak.forEach { katCpu[it.kategoria] = (katCpu[it.kategoria] ?: 0) + it.cpu }
            Masodperc(
                t = t,
                fps = minta.fps,
                ftMed = kocka?.ms?.let { median(it) },
                ftMax = kocka?.ms?.maxOrNull(),
                kepkockaDb = kocka?.ms?.size ?: 0,
                cpu = minta.cpu,
                iowait = minta.iowait,
                gpu = minta.gpu,
                gpuMhz = minta.gpuMhz,
                topSzal = minta.szalak.maxByOrNull { it.cpu },
                katCpu = katCpu,
                olvasasMBs = minta.folyamatok.sumOf { it.olvasas } / MB,
                rcharMBs = minta.folyamatok.sumOf { it.rchar } / MB,
                laphiba = minta.folyamatok.sumOf { it.mf },
                blkio = minta.folyamatok.maxOfOrNull { it.blk } ?: 0,
                dSzalak = minta.szalak.filter { it.allapot == "D" }.map { it.nev },
                frekArany = frekArany(minta, hwMax),
                memSzabadMb = minta.memSzabadMb,
                homCpu = minta.homCpu,
                hoAllapot = minta.hoAllapot,
                shaderNaplo = naploMp[t]?.get(1) ?: 0,
                naploDb = naploMp[t]?.get(0) ?: 0,
                jelek = jelMp[t] ?: emptyList(),
                szunet = minta.szunet || szunetBecsles(minta),
                jatekRssMb = minta.folyamatok.filter { it.nev.endsWith(".exe", ignoreCase = true) }
                    .mapNotNull { it.rssMb }.takeIf { it.isNotEmpty() }?.maxOrNull(),
                jatekSwapMb = minta.folyamatok.filter { it.nev.endsWith(".exe", ignoreCase = true) }
                    .mapNotNull { it.swapMb }.takeIf { it.isNotEmpty() }?.sum(),
                psiMem = minta.memoria["psiMem"],
            )
        }
    }

    /**
     * For recordings without the explicit pause flag: GameNative pauses by SIGSTOP, so the threads
     * show up as 'T' (stopped), or the game's .exe process uses no CPU at all.
     */
    private fun szunetBecsles(minta: Minta): Boolean {
        val jatekSzalak = minta.szalak.filter { it.kategoria != SzalKategoria.HANG && it.kategoria != SzalKategoria.WINE }
        if (jatekSzalak.isNotEmpty() && jatekSzalak.count { it.allapot == "T" || it.allapot == "t" } * 2 > jatekSzalak.size) return true
        val exe = minta.folyamatok.filter { it.nev.endsWith(".exe", ignoreCase = true) && it.nev.lowercase() !in WINE_EXE }
        return exe.isNotEmpty() && exe.all { it.cpu == 0 }
    }

    private val WINE_EXE = setOf(
        "services.exe", "explorer.exe", "winedevice.exe", "svchost.exe", "plugplay.exe", "rpcss.exe",
        "start.exe", "winhandler.exe", "tabtip.exe", "conhost.exe",
    )

    private fun memoriaMegoszlas(m: Munkamenet): Map<String, Int> {
        val mintak = m.mintak.filter { it.memoria.isNotEmpty() && !it.szunet }
        if (mintak.isEmpty()) return emptyMap()
        val kulcsok = mintak.flatMap { it.memoria.keys }.toSet() - setOf("psiMem", "psiIo")
        val eredmeny = LinkedHashMap<String, Int>()
        for (k in kulcsok) median(mintak.mapNotNull { it.memoria[k] })?.let { eredmeny[k] = it.roundToInt() }
        median(mintak.mapNotNull { mi -> mi.memoria["swapOssz"]?.let { o -> mi.memoria["swapSzabad"]?.let { o - it } } })
            ?.let { eredmeny["swapHasznalt"] = it.roundToInt() }
        return eredmeny
    }

    private fun memoriaElvetel(sorok: List<Masodperc>, i: Int): Boolean {
        val most = sorok[i].jatekRssMb ?: return false
        val elotte = sorok.subList(maxOf(0, i - 5), i).mapNotNull { it.jatekRssMb }.maxOrNull() ?: return false
        val elozoMost = if (i > 0) sorok[i - 1].jatekRssMb else null
        // count each shrink once: only when the previous second was not already 150 MB below
        return elotte - most >= 150 && (elozoMost == null || elotte - elozoMost < 150)
    }

    private fun fajlIdovonal(m: Munkamenet): Map<String, List<Pair<Double, Long>>> {
        val eredmeny = LinkedHashMap<String, MutableList<Pair<Double, Long>>>()
        for (minta in m.mintak.sortedBy { it.t }) {
            for (f in minta.gyorsitotar) eredmeny.getOrPut(f.nev) { ArrayList() } += minta.t to f.bentMb
        }
        return eredmeny
    }

    private fun fajlOsszegzes(idovonal: Map<String, List<Pair<Double, Long>>>, m: Munkamenet): List<FajlOsszegzes> {
        val meretek = HashMap<String, Long>()
        m.mintak.forEach { minta -> minta.gyorsitotar.forEach { meretek[it.nev] = it.meretMb } }
        return idovonal.map { (nev, pontok) ->
            var be = 0L
            var ki = 0L
            pontok.zipWithNext().forEach { (a, b) ->
                val d = b.second - a.second
                if (d > 0) be += d else ki -= d
            }
            FajlOsszegzes(
                nev = nev,
                meretMb = meretek[nev] ?: 0L,
                maxBentMb = pontok.maxOf { it.second },
                utolsoBentMb = pontok.last().second,
                beolvasottMb = be,
                kiszorultMb = ki,
            )
        }.sortedByDescending { it.beolvasottMb }
    }

    /** Groups drop seconds, merging drops separated by at most [OSSZEVONAS_RES_MP] calm seconds. */
    fun csoportosit(idovonal: List<Masodperc>): List<List<Masodperc>> {
        val csoportok = ArrayList<MutableList<Masodperc>>()
        var utolsoEsesT = Int.MIN_VALUE
        for (s in idovonal) {
            if (!s.eses) continue
            if (csoportok.isNotEmpty() && s.t - utolsoEsesT <= OSSZEVONAS_RES_MP + 1) {
                csoportok.last() += idovonal.filter { it.t > utolsoEsesT && it.t <= s.t }
            } else {
                csoportok += mutableListOf(s)
            }
            utolsoEsesT = s.t
        }
        return csoportok
    }

    private fun esesElemzes(ablak: List<Masodperc>, k: Kontextus): Eses {
        val pontok = HashMap<String, Double>()
        val bizonyitek = ArrayList<String>()
        fun add(ok: String, ertek: Double) {
            pontok[ok] = ((pontok[ok] ?: 0.0) + ertek).coerceIn(0.0, 1.0)
        }

        // Shader: log lines (DXVK / vkd3d-proton pipeline creation) + shader compiler threads
        val shaderSorok = ablak.sumOf { it.shaderNaplo }
        if (shaderSorok > 0) {
            add(Ok.SHADER, min(0.5, 0.2 + 0.05 * shaderSorok))
            bizonyitek += "Shaderre / PSO-ra utaló naplósorok: $shaderSorok"
        }
        val shaderCpu = ablak.maxOf { it.katCpu[SzalKategoria.SHADER] ?: 0 }
        if (shaderCpu >= 10) {
            add(Ok.SHADER, min(1.0, shaderCpu / 100.0) * 0.6)
            bizonyitek += "Shaderfordító szálak terhelése: $shaderCpu%"
        }

        // Loading threads (decompression on the CPU)
        val betoltesCpu = ablak.maxOf { it.katCpu[SzalKategoria.BETOLTES] ?: 0 }
        if (betoltesCpu >= 10) {
            add(Ok.BETOLTES, min(1.0, betoltesCpu / 100.0) * 0.6)
            bizonyitek += "Betöltő szálak terhelése: $betoltesCpu%"
        }

        // Storage: reads well above the calm-second baseline, page faults, threads blocked on I/O
        val olvasas = ablak.maxOf { it.olvasasMBs }
        if (olvasas >= max(5.0, 4 * k.normalOlvasas)) {
            add(Ok.HATTERTAR, min(1.0, olvasas / OLVASAS_SOK_MBS) * 0.7)
            bizonyitek += "Fájlolvasás a tárhelyről: ${egyTized(olvasas)} MB/s (nyugodt részeken ${egyTized(k.normalOlvasas)})"
        }
        val laphiba = ablak.maxOf { it.laphiba }
        if (laphiba >= max(50.0, 4 * k.normalLaphiba)) {
            add(Ok.HATTERTAR, 0.2)
            bizonyitek += "Tárhelyről betöltött memórialapok: $laphiba/s"
        }
        val dSzalak = ablak.flatMap { it.dSzalak }
        if (dSzalak.isNotEmpty()) {
            add(Ok.HATTERTAR, min(0.3, 0.1 * dSzalak.size))
            bizonyitek += "Tárhelyre váró szálak: ${dSzalak.groupingBy { it }.eachCount().entries.joinToString { "${it.key}×${it.value}" }}"
        }
        val blkio = ablak.maxOf { it.blkio }
        if (blkio >= 10) {
            add(Ok.HATTERTAR, min(0.4, blkio / 100.0))
            bizonyitek += "I/O-várakozás a játékban: $blkio%"
        }
        val iowait = ablak.mapNotNull { it.iowait }.maxOrNull() ?: 0
        if (iowait >= 10) {
            add(Ok.HATTERTAR, 0.2)
            bizonyitek += "I/O-várakozás: $iowait%"
        }

        // Which big file was paged in around the drop (page-cache snapshots every ~5 s)
        val kezd = ablak.first().t
        val veg = ablak.last().t
        val beolvasott = k.fajlIdovonal.mapNotNull { (nev, pontok) ->
            val elotte = pontok.lastOrNull { it.first <= kezd - 1 }?.second ?: return@mapNotNull null
            val utana = pontok.lastOrNull { it.first <= veg + FAJL_ABLAK_MP }?.second ?: return@mapNotNull null
            (nev to (utana - elotte)).takeIf { it.second >= 16 }
        }.sortedByDescending { it.second }
        if (beolvasott.isNotEmpty()) {
            add(Ok.HATTERTAR, 0.1)
            bizonyitek += "Beolvasott fájl: " + beolvasott.take(3).joinToString { "${it.first} (+${it.second} MB)" }
        }

        // Saturated single thread while the GPU has headroom. A saturated shader / loading
        // thread counts towards that cause instead of generic CPU-boundness.
        val telitettMp = ablak.filter { s ->
            val top = s.topSzal ?: return@filter false
            top.cpu >= TELITETT_SZAL && (s.gpu == null || s.gpu < GPU_SZABAD)
        }
        if (telitettMp.isNotEmpty()) {
            val leggyakoribb = telitettMp.groupBy { it.topSzal!!.kategoria }.maxByOrNull { it.value.size }!!
            val top = leggyakoribb.value.maxByOrNull { it.topSzal!!.cpu }!!.topSzal!!
            val ertek = 0.5 + 0.5 * leggyakoribb.value.size / ablak.size
            val ok = when (leggyakoribb.key) {
                SzalKategoria.SHADER -> Ok.SHADER
                SzalKategoria.BETOLTES -> Ok.BETOLTES
                else -> Ok.CPU
            }
            add(ok, ertek)
            val gpuSzoveg = leggyakoribb.value.mapNotNull { it.gpu }.maxOrNull()?.let { ", GPU közben $it%" } ?: ""
            bizonyitek += "Telített szál: ${top.nev} (${SzalKategoria.cimke(top.kategoria)}) ${top.cpu}%$gpuSzoveg"
        }

        // GPU saturated (only when readable)
        val gpuTelitett = ablak.count { (it.gpu ?: 0) >= GPU_TELITETT }
        if (gpuTelitett > 0) {
            add(Ok.GPU, 0.6 + 0.4 * gpuTelitett / ablak.size)
            bizonyitek += "GPU-terhelés: ${ablak.mapNotNull { it.gpu }.maxOrNull()}%"
        }

        // Threads that normally keep frames going are idle: the game waits on something.
        val dolgozo = ablak.map { it.dolgozoCpu }.average()
        if (k.normalDolgozo >= 20 && dolgozo < VARAKOZAS_ARANY * k.normalDolgozo) {
            bizonyitek += "A renderelő- és munkaszálak a szokásosnál kevesebbet dolgoznak " +
                "(${k.normalDolgozo.roundToInt()}% → ${dolgozo.roundToInt()}%): a játék vár valamire"
            if (!k.gpuOlvashato && telitettMp.isEmpty()) add(Ok.VARAKOZAS, 0.45)
        }

        // Thermal: the frequency ceiling dropped below the session's usual ceiling
        val arany = ablak.mapNotNull { it.frekArany }.minOrNull()
        val alap = k.orajelAlap
        if (arany != null && alap != null && arany < HO_CSOKKENES * alap) {
            add(Ok.HO, 0.5 + (HO_CSOKKENES * alap - arany) * 2)
            val hom = ablak.mapNotNull { it.homCpu }.maxOrNull()?.let { ", CPU $it °C" } ?: ""
            bizonyitek += "CPU-órajel plafonja a szokásos ${(alap * 100).roundToInt()}%-ról ${(arany * 100).roundToInt()}%-ra csökkent$hom"
        }
        val hoAllapot = ablak.mapNotNull { it.hoAllapot }.maxOrNull()
        if (hoAllapot != null && hoAllapot >= 2) {
            add(Ok.HO, if (hoAllapot >= 3) 0.4 else 0.25)
            bizonyitek += "Android hőállapot: $hoAllapot"
        }

        // The system takes memory back from the game (RSS shrinks / swap grows) or tasks stall on memory
        val rssElotte = k.idovonal.filter { it.t in (kezd - 6) until kezd }.mapNotNull { it.jatekRssMb }.maxOrNull()
        val rssMin = ablak.mapNotNull { it.jatekRssMb }.minOrNull()
        if (rssElotte != null && rssMin != null && rssElotte - rssMin >= 150) {
            add(Ok.MEMORIA, 0.35)
            bizonyitek += "A rendszer elvett a játék memóriájából: $rssElotte → $rssMin MB"
        }
        val swapElotte = k.idovonal.filter { it.t in (kezd - 6) until kezd }.mapNotNull { it.jatekSwapMb }.minOrNull()
        val swapMax = ablak.mapNotNull { it.jatekSwapMb }.maxOrNull()
        if (swapElotte != null && swapMax != null && swapMax - swapElotte >= 100) {
            add(Ok.MEMORIA, 0.25)
            bizonyitek += "A játék memóriájából tömörített cserehelyre került: +${swapMax - swapElotte} MB"
        }
        val psi = ablak.mapNotNull { it.psiMem }.maxOrNull()
        if (psi != null && psi >= 10.0) {
            add(Ok.MEMORIA, min(0.4, psi / 100.0 + 0.1))
            bizonyitek += "Memóriára várakozás (PSI): ${psi.roundToInt()}%"
        }

        // Memory
        val mem = ablak.mapNotNull { it.memSzabadMb }.minOrNull()
        if (mem != null && mem < MEM_KEVES_MB) {
            add(Ok.MEMORIA, 0.6 + (MEM_KEVES_MB - mem) / 1000.0)
            bizonyitek += "Szabad memória: $mem MB"
        }

        val legjobb = pontok.maxByOrNull { it.value }
        val fooOk = if (legjobb != null && legjobb.value >= FO_OK_MIN_PONT) legjobb.key else Ok.ISMERETLEN
        return Eses(
            kezdet = ablak.first().t,
            veg = ablak.last().t,
            minFps = ablak.mapNotNull { it.fps }.minOrNull(),
            maxFt = ablak.mapNotNull { it.ftMax }.maxOrNull(),
            pontok = pontok,
            fooOk = fooOk,
            bizonyitekok = bizonyitek,
            jelek = ablak.flatMap { it.jelek }.distinct(),
        )
    }

    fun javaslatok(e: Elemzes): List<Javaslat> {
        val eredmeny = ArrayList<Javaslat>()
        val ossz = e.esesIdoMp.coerceAtLeast(1)
        for ((ok, mp) in e.okEloszlas.entries.sortedByDescending { it.value }) {
            val arany = mp.toDouble() / ossz
            val szint = when {
                arany >= 0.3 -> Szint.MAGAS
                arany >= 0.1 -> Szint.KOZEPES
                else -> Szint.ALACSONY
            }
            val (cim, szoveg) = javaslatSzoveg(ok, e)
            eredmeny += Javaslat(szint, ok, cim, szoveg, arany)
        }
        val plafon = e.orajelPlafon
        if (plafon != null && plafon < FREK_KORLAT) {
            val cpuKotott = (e.okEloszlas[Ok.CPU] ?: 0) > 0 ||
                e.idovonal.count { (it.topSzal?.cpu ?: 0) >= TELITETT_SZAL } >= 3
            val klaszterek = e.klaszterPlafon.filter { it.second > 0 }
                .joinToString(", ") { "${it.first}/${it.second} MHz" }
            eredmeny += Javaslat(
                if (cpuKotott || plafon < 0.8) Szint.MAGAS else Szint.KOZEPES,
                Ok.ORAJEL_KORLAT,
                "A processzor végig korlátozott órajelen fut",
                "A rendszer már a mérés elejétől (hűvös telefonnal is) visszafogja a CPU-t: a leggyorsabb mag a maximumának " +
                    "csak kb. ${(plafon * 100).roundToInt()}%-áig mehet" + (if (klaszterek.isNotEmpty()) " (klaszterenként: $klaszterek)" else "") +
                    ". Ez nem melegedés, hanem a HyperOS energia-/játékmódja. Teendő: Beállítások → Akkumulátor alatt kapcsold " +
                    "„Teljesítmény” módra, és a Biztonság app Game Turbo (Játék turbó) részében add hozzá a GameNative Figyelőt, " +
                    "ott is a teljesítmény módot válaszd. Utána mérj újra: a jelentés megmutatja, feloldódott-e a korlát.",
                0.0,
            )
        }
        if (!e.gpuOlvashato) {
            eredmeny += Javaslat(
                Szint.ALACSONY,
                Ok.GPU,
                "A GPU-terhelés nem olvasható",
                "Ezen a telefonon a rendszer nem engedi kiolvasni a GPU-adatokat, ezért a GPU-kötöttséget csak becsülni tudjuk " +
                    "(„A játék vár” ok). Biztos próba: állíts kisebb felbontást (pl. 960×540) – ha az esések eltűnnek, a GPU a szűk keresztmetszet.",
                0.0,
            )
        }
        if (e.szalnevArany != null && e.szalnevArany < 0.5) {
            eredmeny += Javaslat(
                Szint.ALACSONY,
                Ok.ISMERETLEN,
                "A szálak nevei nagyrészt hiányoznak",
                "A Wine nem adja át a játék szálneveit, ezért a szálak kategóriái pontatlanok. A 2. fázis profilozója ezt pótolja.",
                0.0,
            )
        }
        val sorrend = listOf(Szint.MAGAS, Szint.KOZEPES, Szint.ALACSONY)
        return eredmeny.sortedWith(compareBy<Javaslat> { sorrend.indexOf(it.szint) }.thenByDescending { it.arany })
    }

    private fun javaslatSzoveg(ok: String, e: Elemzes): Pair<String, String> {
        val topNev = e.topSzalak.firstOrNull()?.nev
        return when (ok) {
            Ok.SHADER -> "Menet közbeni shaderfordítás" to
                "Az esések alatt a játék új shadereket / pipeline-okat (PSO) fordít. Ez első alkalommal mindig lassú, utána a " +
                "gyorsítótárból jön. Teendő: 1) nézd meg ugyanazt a menüt többször egymás után – ha másodszorra kisebb az esés, " +
                "ez a shader cache; 2) a konténer beállításaiban próbálj ki újabb grafikus drivert (Turnip / Adreno)."
            Ok.BETOLTES -> "Betöltés a CPU-n" to
                "Az esések alatt a játék betöltő szálai dolgoznak, a processzoron tömörítik ki az adatokat. " +
                "Teendő: 1) próbálj gyorsabb Box64 / FEX presetet, vagy válts a másik fordítóra, és hasonlítsd össze; " +
                "2) ellenőrizd, hogy a konténer minden CPU-magot használhat. A 2. fázis megmutatja, melyik kódrész viszi az időt."
            Ok.HATTERTAR -> "Lassú fájlolvasás" to hattertarSzoveg(e)
            Ok.CPU -> "Egy processzorszál a szűk keresztmetszet" to
                "Egy szál végig dolgozik${topNev?.let { " (leggyakrabban: $it)" } ?: ""}, a többi vár rá. " +
                "Ilyenkor a CPU órajele és a fordító (Box64 / FEX) beállításai számítanak a legtöbbet: oldd fel az órajel-korlátot " +
                "(ha van), és próbálj gyorsabb presetet. A 2. fázis megmutatja, melyik kódrész viszi az időt."
            Ok.GPU -> "A videókártya (GPU) a szűk keresztmetszet" to
                "A GPU teljesen ki van használva. Teendő: kisebb felbontás vagy alacsonyabb grafikai beállítás a játékban, " +
                "FPS-limit, vagy másik grafikus driver."
            Ok.VARAKOZAS -> "A játék vár (valószínűleg a GPU-ra)" to
                "Az esések alatt a renderelő szálak a szokásosnál kevesebbet dolgoznak, fájlolvasás nincs, a GPU-t pedig nem tudjuk " +
                "kiolvasni. A legvalószínűbb, hogy a GPU nem bírja a jelenetet. Próba: állíts kisebb felbontást vagy grafikát, " +
                "és mérj újra – ha ezek az esések eltűnnek, a GPU volt a szűk keresztmetszet."
            Ok.HO -> "Melegedés miatti lassítás" to
                "A mérés közben a telefon a hő miatt tovább csökkentette a processzor órajelét. Teendő: alacsonyabb FPS-limit, " +
                "tok levétele, hűtő használata."
            Ok.MEMORIA -> "Memóriahiány: a rendszer elveszi a játék memóriáját" to memoriaSzoveg(e)
            else -> "Nem egyértelmű ok" to
                "Ezeknél az eséseknél a mért adatokból nem rajzolódik ki egy fő ok. Teendő: a „Jelölés” gombbal jelöld meg, " +
                "mikor lép be a menübe, és vegyél fel még egy mérést."
        }
    }

    private fun memoriaSzoveg(e: Elemzes): String {
        val korlat = e.maxEszkozMemoria
        val korlatSzoveg = when {
            korlat == null -> "Állítsd be a konténer grafikus driver beállításaiban a „Max Device Memory” értékét 4096 MB-ra."
            korlat == 0 -> "A konténerben a „Max Device Memory” most korlátlan (0), így a játék azt hiszi, rengeteg videomemóriája van, " +
                "és sok textúrát tart bent. Állítsd 4096 MB-ra (Grafika fül → Max Device Memory); ha még mindig van ilyen esés, 2048-ra."
            korlat > 2048 -> "A „Max Device Memory” most $korlat MB – próbáld 2048 MB-tal."
            else -> "A „Max Device Memory” már $korlat MB; a játékban vedd lejjebb a textúra- és árnyékminőséget."
        }
        val gpu = e.memoriaMegoszlas["nemKovetett"]
        // without a separate zram figure the estimate also contains the compressed swap, so demand more before blaming the GPU
        val gpuKuszob = if (e.memoriaMegoszlas.containsKey("zram")) 2500 else 6000
        val gpuSzoveg = if (gpu != null && gpu >= gpuKuszob) {
            " A memóriából kb. ${"%.1f".format(Locale.US, gpu / 1024.0)} GB-ot a GPU és a driver foglal " +
                "(ezt az Android nem tudja kiszorítani), ezért a játéktól és a fájlok gyorsítótárától veszi el. A leghatásosabb a játékon belül " +
                "csökkenteni a textúra- és árnyékminőséget, mert az közvetlenül ezt a memóriát csökkenti."
        } else {
            ""
        }
        return "A telefonon kevés a szabad memória, ezért az Android a futó játéktól is elvesz" +
            (e.minJatekRssMb?.let { min -> e.maxJatekRssMb?.let { max -> " (a játék memóriája $max és $min MB között ingadozott)" } } ?: "") +
            ", és amit utána újra használna, azt lassan kapja vissza." + gpuSzoveg + " Teendő: $korlatSzoveg Zárd be a többi appot a játék előtt."
    }

    private fun hattertarSzoveg(e: Elemzes): String {
        val beolvasott = e.fajlok.sumOf { it.beolvasottMb }
        val kiszorult = e.fajlok.sumOf { it.kiszorultMb }
        val fajlSzoveg = e.fajlok.firstOrNull { it.beolvasottMb > 0 }?.let { f ->
            "A legtöbbet ebből olvasott: ${f.nev} (${f.beolvasottMb} MB). " +
                if (kiszorult >= 200 && kiszorult * 4 >= beolvasott) {
                    "A beolvasott adatból $kiszorult MB később ki is szorult a memóriából, vagyis a kevés szabad memória miatt " +
                        "a játék ugyanazt többször olvassa be. "
                } else {
                    ""
                }
        } ?: ""
        val alap = "Az esések alatt a játék a szokásosnál jóval többet olvas a tárhelyről, és közben a szálai várnak. " + fajlSzoveg
        return when (e.jatekHely) {
            "megosztott" ->
                alap + "A játék a telefon megosztott tárhelyén van (${e.jatekMappa ?: "pl. Letöltések"}). Ezt a részt az Android egy " +
                    "lassabb közvetítő rétegen (FUSE) keresztül olvassa, ami a sok apró olvasásnál akadást okoz. Teendő: tedd a játékot " +
                    "az app saját mappájába (Android/data/app.gamenative.figyelo/files/…) vagy töltsd le az appon belül a Steamről – " +
                    "ezek a helyek kikerülik ezt a réteget."
            "sdKartya" ->
                alap + "A játék SD-kártyán van, ami lassabb a belső tárhelynél. Teendő: kapcsold be a konténerben a " +
                    "„Faster loading from external storage” beállítást, vagy tedd a játékot a belső tárhelyre."
            "belso", "appSajatKulso" ->
                alap + "A játék már gyors helyen van: nem a tárhely lassú, hanem a kevés szabad memória miatt a már beolvasott adat " +
                    "kiszorul, és újra be kell olvasni. " + memoriaSzoveg(e)
            else ->
                alap + "Teendő: a játék fájljai a belső tárhelyen legyenek (ne SD-kártyán), és legyen bőven szabad hely. " +
                    "(A játék mappáját az app a következő méréstől rögzíti, abból pontosabb tanács adható.)"
        }
    }

    private fun frekArany(minta: Minta, hwMax: List<Int>): Double? {
        val max = minta.frekMax
        if (max.isEmpty()) return null
        val plafon = minta.frekPlafon.takeIf { it.size == max.size && it.all { v -> v > 0 } } ?: hwMax
        if (plafon.size != max.size || plafon.isEmpty()) return null
        val leggyorsabb = plafon.indices.maxByOrNull { plafon[it] } ?: return null
        val hw = plafon[leggyorsabb]
        val most = max[leggyorsabb]
        if (hw <= 0 || most <= 0) return null
        return (most.toDouble() / hw).coerceIn(0.0, 1.0)
    }

    private fun klaszterMaxMhz(m: Munkamenet): List<Int> {
        val klaszterek = m.meta.optJSONObject("eszkoz")?.optJSONArray("klaszterek") ?: return emptyList()
        return (0 until klaszterek.length()).map { klaszterek.optJSONObject(it)?.optInt("maxMhz", -1) ?: -1 }
    }

    /** Typical (90th percentile) scaling_max_freq per cluster vs the cluster's hardware max. */
    private fun klaszterPlafon(m: Munkamenet): List<Pair<Int, Int>> {
        val hw = klaszterMaxMhz(m)
        return hw.indices.map { i ->
            val ertekek = m.mintak.mapNotNull { it.frekMax.getOrNull(i)?.takeIf { v -> v > 0 }?.toDouble() }
            (percentilis(ertekek, 0.9)?.roundToInt() ?: -1) to hw[i]
        }
    }

    private val generikusSzalnevek = setOf("wine64-preload", "wine-preloader", "wine64", "wine", "box64", "fex", "main", "linker64", "")

    private fun generikusSzalnev(nev: String, folyamatNevek: Set<String>): Boolean {
        val n = nev.lowercase()
        return n in generikusSzalnevek || n in folyamatNevek || folyamatNevek.any { it.startsWith(n) && n.length >= 8 } ||
            n.endsWith(".exe")
    }

    fun median(ertekek: List<Double>): Double? = percentilis(ertekek, 0.5)

    fun percentilis(ertekek: List<Double>, q: Double): Double? {
        if (ertekek.isEmpty()) return null
        val rendezett = ertekek.sorted()
        if (q == 0.5) {
            val k = rendezett.size / 2
            return if (rendezett.size % 2 == 1) rendezett[k] else (rendezett[k - 1] + rendezett[k]) / 2.0
        }
        val i = (ceil(q * rendezett.size).toInt() - 1).coerceIn(0, rendezett.size - 1)
        return rendezett[i]
    }

    private fun egyTized(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

    private const val MB = 1024.0 * 1024.0
}
