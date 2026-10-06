package app.gamenative.figyelo.elemzes

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Raw Figyelő session files (one JSON object per line), see figyelo/ADATFORMATUM.md:
 *  - meta.json        session / device / container description
 *  - minta.jsonl      one system sample per second
 *  - kepkockak.jsonl  frame times (ms) grouped per second
 *  - naplo.jsonl      Wine / DXVK / VKD3D output lines
 *  - jelek.jsonl      user markers ("Jelölés" button)
 */
object Fajlok {
    const val META = "meta.json"
    const val MINTA = "minta.jsonl"
    const val KEPKOCKAK = "kepkockak.jsonl"
    const val NAPLO = "naplo.jsonl"
    const val JELEK = "jelek.jsonl"
    const val JELENTES = "jelentes.html"
    const val OSSZEGZES = "osszegzes.json"
    const val IDOVONAL = "idovonal.csv"
}

data class Szal(
    val pid: Int,
    val tid: Int,
    val nev: String,
    val kategoria: String,
    val cpu: Int,
    val allapot: String,
    /** major page faults / s (pages read from storage on demand, e.g. memory-mapped files) */
    val mf: Int = 0,
)

data class Folyamat(
    val pid: Int,
    val nev: String,
    val cpu: Int,
    /** bytes/s actually read from storage (/proc/pid/io read_bytes) */
    val olvasas: Long,
    /** bytes/s read through read()-like calls incl. page cache (/proc/pid/io rchar) */
    val rchar: Long,
    /** major page faults / s */
    val mf: Int = 0,
    /** % of the second spent waiting for block I/O (delay accounting; 0 when unavailable) */
    val blk: Int = 0,
)

data class Minta(
    val t: Double,
    val fps: Double?,
    val cpu: Int?,
    val iowait: Int?,
    val magok: List<Int>,
    val frek: List<Int>,
    val frekMax: List<Int>,
    val frekPlafon: List<Int>,
    val gpu: Int?,
    val gpuMhz: Int?,
    val homCpu: Int?,
    val homAkku: Int?,
    val homBor: Int?,
    val hoAllapot: Int?,
    val memSzabadMb: Int?,
    val folyamatok: List<Folyamat>,
    val szalak: List<Szal>,
)

data class KepkockaCsomag(val t: Double, val ms: List<Double>)

data class NaploSor(val t: Double, val sor: String)

data class Jel(val t: Double, val cimke: String)

data class Munkamenet(
    val meta: JSONObject,
    val mintak: List<Minta>,
    val kepkockak: List<KepkockaCsomag>,
    val naplo: List<NaploSor>,
    val jelek: List<Jel>,
)

object Beolvaso {

    fun mappabol(mappa: File): Munkamenet {
        val meta = File(mappa, Fajlok.META).takeIf { it.exists() }?.readText()?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject()
        return Munkamenet(
            meta = meta,
            mintak = sorok(File(mappa, Fajlok.MINTA)).mapNotNull { runCatching { minta(it) }.getOrNull() },
            kepkockak = sorok(File(mappa, Fajlok.KEPKOCKAK)).mapNotNull { runCatching { kepkocka(it) }.getOrNull() },
            naplo = sorok(File(mappa, Fajlok.NAPLO)).mapNotNull { o ->
                runCatching { NaploSor(o.getDouble("t"), o.optString("sor")) }.getOrNull()
            },
            jelek = sorok(File(mappa, Fajlok.JELEK)).mapNotNull { o ->
                runCatching { Jel(o.getDouble("t"), o.optString("cimke")) }.getOrNull()
            },
        )
    }

    /** Reads a JSONL file, skipping broken lines (e.g. the last line after a crash). */
    fun sorok(fajl: File): List<JSONObject> {
        if (!fajl.exists()) return emptyList()
        val eredmeny = ArrayList<JSONObject>()
        fajl.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                runCatching { JSONObject(line) }.getOrNull()?.let { eredmeny += it }
            }
        }
        return eredmeny
    }

    fun minta(o: JSONObject): Minta {
        val hom = o.optJSONObject("hom")
        return Minta(
            t = o.getDouble("t"),
            fps = o.optDoubleOrNull("fps"),
            cpu = o.optIntOrNull("cpu"),
            iowait = o.optIntOrNull("iow"),
            magok = o.optJSONArray("magok").ints(),
            frek = o.optJSONArray("frek").ints(),
            frekMax = o.optJSONArray("frekMax").ints(),
            frekPlafon = o.optJSONArray("frekPlafon").ints(),
            gpu = o.optIntOrNull("gpu"),
            gpuMhz = o.optIntOrNull("gpuMhz"),
            homCpu = hom?.optIntOrNull("cpu"),
            homAkku = hom?.optIntOrNull("akku"),
            homBor = hom?.optIntOrNull("bor"),
            hoAllapot = o.optIntOrNull("hoAllapot"),
            memSzabadMb = o.optIntOrNull("memSzabadMb"),
            folyamatok = o.optJSONArray("folyamatok").objects().map { p ->
                Folyamat(
                    pid = p.optInt("pid"),
                    nev = p.optString("nev"),
                    cpu = p.optInt("cpu"),
                    olvasas = p.optLong("olv"),
                    rchar = p.optLong("rchar"),
                    mf = p.optInt("mf"),
                    blk = p.optInt("blk"),
                )
            },
            szalak = o.optJSONArray("szalak").objects().map { s ->
                Szal(
                    pid = s.optInt("pid"),
                    tid = s.optInt("tid"),
                    nev = s.optString("nev"),
                    kategoria = s.optString("kat").ifEmpty { SzalKategoria.besorol(s.optString("nev")) },
                    cpu = s.optInt("cpu"),
                    allapot = s.optString("all"),
                    mf = s.optInt("mf"),
                )
            },
        )
    }

    fun kepkocka(o: JSONObject): KepkockaCsomag {
        val ms = o.optJSONArray("ms")
        val lista = ArrayList<Double>(ms?.length() ?: 0)
        if (ms != null) for (i in 0 until ms.length()) lista += ms.optDouble(i)
        return KepkockaCsomag(o.getDouble("t"), lista.filter { it.isFinite() && it > 0.0 })
    }
}

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { it.isFinite() } else null

internal fun JSONArray?.ints(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).map { if (isNull(it)) -1 else optInt(it, -1) }
}

internal fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}
