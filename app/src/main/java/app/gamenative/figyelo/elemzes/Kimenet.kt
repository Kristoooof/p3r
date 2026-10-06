package app.gamenative.figyelo.elemzes

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** Writes idovonal.csv, osszegzes.json and jelentes.html for an analysed session. */
object Kimenet {

    fun mindentIr(mappa: File, m: Munkamenet, e: Elemzes) {
        File(mappa, Fajlok.IDOVONAL).writeText(csv(e))
        File(mappa, Fajlok.OSSZEGZES).writeText(osszegzes(m, e).toString(2))
        File(mappa, Fajlok.JELENTES).writeText(JelentesHtml.html(m, e))
    }

    /** Analyses the session folder and writes all outputs. */
    fun feldolgoz(mappa: File): Elemzes {
        val m = Beolvaso.mappabol(mappa)
        val e = Rendszerezo.elemez(m)
        mindentIr(mappa, m, e)
        return e
    }

    fun csv(e: Elemzes): String = buildString {
        val katok = SzalKategoria.MIND
        append("t,fps,kepkocka_med_ms,kepkocka_max_ms,cpu,iowait,gpu,gpu_mhz,top_szal,top_szal_kat,top_szal_cpu,")
        append(katok.joinToString(",") { "${it}_cpu" })
        append(",olvasas_mbs,rchar_mbs,laphiba,blkio,d_szalak,frek_arany,mem_szabad_mb,hom_cpu,ho_allapot,naplo_sorok,shader_naplo,jel,eses\n")
        for (s in e.idovonal) {
            val mezok = ArrayList<String>()
            mezok += s.t.toString()
            mezok += s.fps.f1()
            mezok += s.ftMed.f1()
            mezok += s.ftMax.f1()
            mezok += s.cpu.s()
            mezok += s.iowait.s()
            mezok += s.gpu.s()
            mezok += s.gpuMhz.s()
            mezok += idezett(s.topSzal?.nev ?: "")
            mezok += s.topSzal?.kategoria ?: ""
            mezok += s.topSzal?.cpu.s()
            katok.forEach { mezok += (s.katCpu[it] ?: 0).toString() }
            mezok += s.olvasasMBs.f1()
            mezok += s.rcharMBs.f1()
            mezok += s.laphiba.toString()
            mezok += s.blkio.toString()
            mezok += idezett(s.dSzalak.joinToString(" | "))
            mezok += s.frekArany?.let { String.format(Locale.US, "%.2f", it) } ?: ""
            mezok += s.memSzabadMb.s()
            mezok += s.homCpu.s()
            mezok += s.hoAllapot.s()
            mezok += s.naploDb.toString()
            mezok += s.shaderNaplo.toString()
            mezok += idezett(s.jelek.joinToString(" | "))
            mezok += if (s.eses) "1" else "0"
            append(mezok.joinToString(","))
            append('\n')
        }
    }

    fun osszegzes(m: Munkamenet, e: Elemzes): JSONObject = JSONObject().apply {
        put("formatum", 1)
        put("jatek", m.meta.optString("jatek"))
        put("figyeloVerzio", m.meta.optInt("figyeloVerzio", 0))
        put("kezdes", m.meta.optString("kezdes"))
        m.meta.optJSONObject("eszkoz")?.let { put("eszkoz", it) }
        m.meta.optJSONObject("kontener")?.let { put("kontener", it) }
        put(
            "osszegzes",
            JSONObject().apply {
                put("hosszMp", e.hosszMp)
                putOpt("medianFps", e.medianFps?.r1())
                putOpt("atlagFps", e.atlagFps?.r1())
                putOpt("egySzazalekLowFps", e.egySzazalekLowFps?.r1())
                putOpt("medianKepkockaMs", e.medianFtMs?.r1())
                put("esesekSzama", e.esesek.size)
                put("esesIdoMp", e.esesIdoMp)
                put("okEloszlasMp", JSONObject(e.okEloszlas.toMap()))
                put("kezdoBetoltesMp", e.kezdoBetoltesMp)
                putOpt("orajelPlafon", e.orajelPlafon?.r2())
                put(
                    "klaszterPlafonMhz",
                    JSONArray().apply { e.klaszterPlafon.forEach { put(JSONArray().put(it.first).put(it.second)) } },
                )
                putOpt("jatekHely", e.jatekHely)
                put("szunetMp", e.szunetMp)
                putOpt("maxJatekRssMb", e.maxJatekRssMb)
                putOpt("minJatekRssMb", e.minJatekRssMb)
                putOpt("maxJatekSwapMb", e.maxJatekSwapMb)
                put("memoriaElvetelDb", e.memoriaElvetelDb)
                putOpt("maxEszkozMemoria", e.maxEszkozMemoria)
                put("memoriaMegoszlasMb", JSONObject(e.memoriaMegoszlas.toMap()))
            },
        )
        put(
            "adatforrasok",
            JSONObject().apply {
                put("gpu", e.gpuOlvashato)
                put("cpuFrekvencia", e.frekOlvashato)
                put("homerseklet", e.homOlvashato)
                putOpt("szalnevArany", e.szalnevArany?.r2())
                putOpt("gpuForras", m.meta.optJSONObject("olvashato")?.optString("gpuForras"))
            },
        )
        put(
            "fajlok",
            JSONArray().apply {
                e.fajlok.forEach {
                    put(
                        JSONObject().put("nev", it.nev).put("meretMb", it.meretMb).put("maxBentMb", it.maxBentMb)
                            .put("utolsoBentMb", it.utolsoBentMb).put("beolvasottMb", it.beolvasottMb).put("kiszorultMb", it.kiszorultMb),
                    )
                }
            },
        )
        put("kategoriaAtlagCpu", JSONObject().apply { e.kategoriaAtlag.forEach { (k, v) -> put(k, v.r1()) } })
        put(
            "topSzalak",
            JSONArray().apply {
                e.topSzalak.forEach {
                    put(
                        JSONObject().put("nev", it.nev).put("kategoria", it.kategoria)
                            .put("atlagCpu", it.atlagCpu.r1()).put("maxCpu", it.maxCpu),
                    )
                }
            },
        )
        put(
            "esesek",
            JSONArray().apply {
                e.esesek.forEach { es ->
                    put(
                        JSONObject().apply {
                            put("kezdet", es.kezdet)
                            put("veg", es.veg)
                            put("hosszMp", es.hossz)
                            putOpt("minFps", es.minFps?.r1())
                            putOpt("maxKepkockaMs", es.maxFt?.r1())
                            put("fooOk", es.fooOk)
                            put("pontok", JSONObject().apply { es.pontok.forEach { (k, v) -> put(k, v.r2()) } })
                            put("bizonyitekok", JSONArray(es.bizonyitekok))
                            put("jelek", JSONArray(es.jelek))
                        },
                    )
                }
            },
        )
        put(
            "javaslatok",
            JSONArray().apply {
                e.javaslatok.forEach {
                    put(
                        JSONObject().put("szint", it.szint).put("ok", it.ok).put("cim", it.cim)
                            .put("szoveg", it.szoveg).put("arany", it.arany.r2()),
                    )
                }
            },
        )
    }

    private fun idezett(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"").replace('\n', ' ') + "\"" else s

    private fun Double?.f1(): String = this?.let { String.format(Locale.US, "%.1f", it) } ?: ""
    private fun Int?.s(): String = this?.toString() ?: ""
    internal fun Double.r1(): Double = (this * 10).roundToInt() / 10.0
    internal fun Double.r2(): Double = (this * 100).roundToInt() / 100.0
}
