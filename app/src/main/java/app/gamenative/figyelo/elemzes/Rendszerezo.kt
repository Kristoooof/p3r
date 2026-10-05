package app.gamenative.figyelo.elemzes

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
    /** scaling_max_freq / cpuinfo_max_freq of the fastest cluster, 0..1 */
    val frekArany: Double?,
    val memSzabadMb: Int?,
    val homCpu: Int?,
    val hoAllapot: Int?,
    val shaderNaplo: Int,
    val naploDb: Int,
    val jelek: List<String>,
    var eses: Boolean = false,
)

object Ok {
    const val SHADER = "shader"
    const val BETOLTES = "betoltes"
    const val HATTERTAR = "hattertar"
    const val CPU = "cpu"
    const val GPU = "gpu"
    const val HO = "ho"
    const val MEMORIA = "memoria"
    const val ISMERETLEN = "ismeretlen"

    val MIND = listOf(SHADER, BETOLTES, HATTERTAR, CPU, GPU, HO, MEMORIA)

    fun cimke(ok: String): String = when (ok) {
        SHADER -> "Shaderfordítás"
        BETOLTES -> "Betöltés (CPU-n kitömörítés)"
        HATTERTAR -> "Lassú fájlolvasás"
        CPU -> "CPU-kötött (egy szál a szűk keresztmetszet)"
        GPU -> "GPU-kötött"
        HO -> "Melegedés miatti lassítás"
        MEMORIA -> "Kevés szabad memória"
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
    const val FREK_PLAFON = 0.75
    const val MEM_KEVES_MB = 600
    const val OLVASAS_SOK_MBS = 40.0

    fun elemez(m: Munkamenet): Elemzes {
        val idovonal = idovonal(m)
        val fpsErtekek = idovonal.mapNotNull { it.fps }.filter { it > 0.0 }
        val medianFps = median(fpsErtekek)
        val osszesFt = m.kepkockak.flatMap { it.ms }
        val medianFt = median(osszesFt)
        val ftKorlat = medianFt?.let { max(ESES_FT_MIN_MS, ESES_FT_SZORZO * it) } ?: ESES_FT_MIN_MS

        for (s in idovonal) {
            val fpsEses = s.fps != null && medianFps != null && (s.fps == 0.0 || s.fps < ESES_FPS_ARANY * medianFps)
            val ftEses = s.ftMax != null && s.ftMax > ftKorlat
            s.eses = fpsEses || ftEses
        }

        val hwMax = klaszterMaxMhz(m)
        val esesek = csoportosit(idovonal).map { ablak -> esesElemzes(ablak, medianFps, hwMax) }
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
        val egySzazalek = if (osszesFt.size >= 100) {
            percentilis(osszesFt, 0.99)?.let { 1000.0 / it }
        } else {
            null
        }

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
            gpuOlvashato = idovonal.any { it.gpu != null },
            frekOlvashato = idovonal.any { it.frekArany != null },
            homOlvashato = idovonal.any { it.homCpu != null },
            szalnevArany = szalnevArany,
            okEloszlas = okEloszlas,
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
                frekArany = frekArany(minta, hwMax),
                memSzabadMb = minta.memSzabadMb,
                homCpu = minta.homCpu,
                hoAllapot = minta.hoAllapot,
                shaderNaplo = naploMp[t]?.get(1) ?: 0,
                naploDb = naploMp[t]?.get(0) ?: 0,
                jelek = jelMp[t] ?: emptyList(),
            )
        }
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

    private fun esesElemzes(ablak: List<Masodperc>, medianFps: Double?, hwMax: List<Int>): Eses {
        val pontok = HashMap<String, Double>()
        val bizonyitek = ArrayList<String>()
        fun add(ok: String, ertek: Double) {
            pontok[ok] = ((pontok[ok] ?: 0.0) + ertek).coerceIn(0.0, 1.0)
        }

        // Shader: log lines + shader compiler threads
        val shaderSorok = ablak.sumOf { it.shaderNaplo }
        if (shaderSorok > 0) {
            add(Ok.SHADER, min(0.5, 0.2 + 0.05 * shaderSorok))
            bizonyitek += "Shaderre utaló naplósorok: $shaderSorok"
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

        // Storage reads
        val olvasas = ablak.maxOf { it.olvasasMBs }
        val iowait = ablak.mapNotNull { it.iowait }.maxOrNull() ?: 0
        if (olvasas >= 5.0) {
            add(Ok.HATTERTAR, min(1.0, olvasas / OLVASAS_SOK_MBS) * 0.7)
            bizonyitek += "Fájlolvasás a tárhelyről: ${egyTized(olvasas)} MB/s"
        }
        if (iowait >= 10) {
            add(Ok.HATTERTAR, 0.2)
            bizonyitek += "I/O-várakozás: $iowait%"
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

        // GPU saturated
        val gpuTelitett = ablak.count { (it.gpu ?: 0) >= GPU_TELITETT }
        if (gpuTelitett > 0) {
            add(Ok.GPU, 0.6 + 0.4 * gpuTelitett / ablak.size)
            bizonyitek += "GPU-terhelés: ${ablak.mapNotNull { it.gpu }.maxOrNull()}%"
        }

        // Thermal: CPU frequency ceiling lowered
        val arany = ablak.mapNotNull { it.frekArany }.minOrNull()
        if (arany != null && arany < FREK_PLAFON) {
            add(Ok.HO, 0.5 + (FREK_PLAFON - arany))
            bizonyitek += "CPU-órajel plafonja: a maximum ${(arany * 100).roundToInt()}%-a"
        }
        val hoAllapot = ablak.mapNotNull { it.hoAllapot }.maxOrNull()
        if (hoAllapot != null && hoAllapot >= 3) {
            add(Ok.HO, 0.2)
            bizonyitek += "Android hőállapot: $hoAllapot (súlyos)"
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
        if (!e.gpuOlvashato) {
            eredmeny += Javaslat(
                Szint.ALACSONY,
                Ok.GPU,
                "A GPU-terhelés nem olvasható",
                "Ezen a telefonon az app nem fér hozzá a GPU-adatokhoz, ezért a GPU-kötöttséget nem tudjuk kimutatni.",
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
                "Az esések alatt a játék új shadereket fordít. Ez első alkalommal mindig lassú, utána a gyorsítótárból jön. " +
                "Teendő: 1) nézd meg ugyanazt a menüt többször egymás után – ha másodszorra kisebb az esés, ez a shader cache; " +
                "2) a konténer beállításaiban próbálj ki újabb grafikus drivert (Turnip / Adreno); " +
                "3) ha a DX-wrapper listában van „async” vagy „gplasync” DXVK, próbáld ki."
            Ok.BETOLTES -> "Betöltés a CPU-n" to
                "Az esések alatt a játék betöltő szálai dolgoznak, a processzoron tömörítik ki az adatokat. " +
                "Teendő: 1) próbálj gyorsabb Box64 / FEX presetet, vagy válts a másik fordítóra, és hasonlítsd össze; " +
                "2) ellenőrizd, hogy a konténer minden CPU-magot használhat. A 2. fázis megmutatja, melyik kódrész viszi az időt."
            Ok.HATTERTAR -> "Lassú fájlolvasás" to
                "Az esések alatt a játék sokat olvas a tárhelyről. Teendő: a játék fájljai a belső tárhelyen legyenek " +
                "(ne SD-kártyán), és legyen bőven szabad hely a telefonon."
            Ok.CPU -> "Egy processzorszál a szűk keresztmetszet" to
                "Egy szál végig dolgozik${topNev?.let { " (leggyakrabban: $it)" } ?: ""}, a GPU közben ráérne. " +
                "Ilyenkor a CPU-fordító (Box64 / FEX) beállításai számítanak a legtöbbet: próbálj gyorsabb presetet. " +
                "A 2. fázis megmutatja, melyik kódrész viszi az időt."
            Ok.GPU -> "A videókártya (GPU) a szűk keresztmetszet" to
                "A GPU teljesen ki van használva. Teendő: kisebb felbontás vagy alacsonyabb grafikai beállítás a játékban, " +
                "FPS-limit, vagy másik grafikus driver."
            Ok.HO -> "Melegedés miatti lassítás" to
                "A telefon a hő miatt visszavette a processzor órajelét. Teendő: alacsonyabb FPS-limit (pl. 30), " +
                "tok levétele, hűtő használata."
            Ok.MEMORIA -> "Kevés szabad memória" to
                "Az esések alatt alig volt szabad memória. Teendő: zárd be a többi appot a játék indítása előtt."
            else -> "Nem egyértelmű ok" to
                "Ezeknél az eséseknél a mért adatokból nem rajzolódik ki egy fő ok. Teendő: a „Jelölés” gombbal jelöld meg, " +
                "mikor lép be a menübe, és vegyél fel még egy mérést."
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

    private val generikusSzalnevek = setOf("wine64-preload", "wine-preloader", "wine64", "wine", "box64", "fex", "main", "")

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
