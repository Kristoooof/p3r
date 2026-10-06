package app.gamenative.figyelo

import app.gamenative.figyelo.elemzes.Fajlok
import app.gamenative.figyelo.elemzes.Kimenet
import app.gamenative.figyelo.elemzes.Ok
import app.gamenative.figyelo.elemzes.Szint
import app.gamenative.figyelo.elemzes.SzalKategoria
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RendszerezoTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun szal(tid: Int, nev: String, cpu: Int) = JSONObject().put("pid", 100).put("tid", tid).put("nev", nev)
        .put("kat", SzalKategoria.besorol(nev, tid == 100, "P3R-Win64-Shipping.exe")).put("cpu", cpu).put("all", "R")

    private fun keszit(mappa: File) {
        mappa.mkdirs()
        File(mappa, Fajlok.META).writeText(JSONObject().put("jatek", "Persona 3 Reload").put("kezdes", "2026-10-05 21:00")
            .put("eszkoz", JSONObject().put("gyarto", "Xiaomi").put("modell", "POCO F7").put("soc", "SM8735")
                .put("klaszterek", JSONArray().put(JSONObject().put("maxMhz", 2000)).put(JSONObject().put("maxMhz", 3200))))
            .put("kontener", JSONObject().put("emulator", "FEXCore").put("fexVerzio", "2605").put("driver", "turnip"))
            .toString())
        val minta = StringBuilder(); val kocka = StringBuilder(); val naplo = StringBuilder()
        for (t in 1..120) {
            var fps = 60.0; var gpu = 55; val szalak = JSONArray(); var olv = 0L; var frekMax = 3200
            szalak.put(szal(100, "P3R-Win64-Ship", 70)).put(szal(101, "RenderThread 1", 60)).put(szal(102, "dxvk-cs", 30))
            if (t in 30..34) { fps = 12.0; szalak.put(szal(110, "dxvk-shader", 95)).put(szal(111, "dxvk-shader", 90))
                naplo.append(JSONObject().put("t", t - 0.5).put("sor", "info:  DXVK: Compiling shader PS_abc").toString()).append('\n') }
            if (t in 70..72) { fps = 15.0; gpu = 30; szalak.put(szal(120, "AsyncLoadingThr", 98)); olv = 3L * 1024 * 1024 }
            if (t in 100..101) { fps = 30.0; gpu = 97 }
            if (t in 110..112) { fps = 25.0; frekMax = 1800 }
            minta.append(JSONObject().put("t", t.toDouble()).put("fps", fps).put("cpu", 40).put("gpu", gpu)
                .put("frek", JSONArray(listOf(1500, 2800))).put("frekMax", JSONArray(listOf(2000, frekMax)))
                .put("memSzabadMb", 3000).put("folyamatok", JSONArray().put(JSONObject().put("pid", 100).put("nev", "P3R-Win64-Shipping.exe").put("cpu", 200).put("olv", olv)))
                .put("szalak", szalak).toString()).append('\n')
            val ms = JSONArray(); repeat(fps.toInt()) { ms.put(1000.0 / fps) }
            if (t == 50) ms.put(400.0)
            kocka.append(JSONObject().put("t", t.toDouble()).put("ms", ms).toString()).append('\n')
        }
        File(mappa, Fajlok.MINTA).writeText(minta.toString() + "{broken line\n")
        File(mappa, Fajlok.KEPKOCKAK).writeText(kocka.toString())
        File(mappa, Fajlok.NAPLO).writeText(naplo.toString())
        File(mappa, Fajlok.JELEK).writeText(JSONObject().put("t", 29.2).put("cimke", "menü").toString() + "\n")
    }

    @Test
    fun esesekEsOkok() {
        val mappa = File(tmp.root, "munkamenet"); keszit(mappa)
        val e = Kimenet.feldolgoz(mappa)
        assertEquals(listOf(Ok.SHADER, Ok.ISMERETLEN, Ok.BETOLTES, Ok.GPU, Ok.HO), e.esesek.map { it.fooOk })
        assertEquals(listOf("menü"), e.esesek[0].jelek)
        assertEquals(Szint.MAGAS, e.javaslatok.first().szint)
        assertTrue(File(mappa, Fajlok.JELENTES).readText().contains("Persona 3 Reload"))
        assertEquals(121, File(mappa, Fajlok.IDOVONAL).readLines().size)
    }

    @Test
    fun kategoriak() {
        assertEquals(SzalKategoria.SHADER, SzalKategoria.besorol("dxvk-shader"))
        assertEquals(SzalKategoria.BETOLTES, SzalKategoria.besorol("AsyncLoadingThr"))
        assertEquals(SzalKategoria.BETOLTES, SzalKategoria.besorol("IOThreadPool #0"))
        assertEquals(SzalKategoria.RENDER, SzalKategoria.besorol("RHIThread"))
        assertEquals(SzalKategoria.MUNKA, SzalKategoria.besorol("TaskGraphThread"))
        assertEquals(SzalKategoria.JATEK, SzalKategoria.besorol("P3R-Win64-Ship", true, "P3R-Win64-Shipping.exe"))
        assertEquals(SzalKategoria.WINE, SzalKategoria.besorol("wineserver", true, "wineserver"))
        assertTrue(SzalKategoria.shaderNaploSor("info: Compiling pipeline"))
    }

    @Test
    fun allandoOrajelKorlatNemHo() {
        val mappa = File(tmp.root, "korlat"); keszit(mappa)
        // the whole session runs with the fastest cluster capped to 2300 / 3200 MHz
        val sorok = File(mappa, Fajlok.MINTA).readLines().filter { it.startsWith("{\"") }.map {
            JSONObject(it).put("frekMax", JSONArray(listOf(2000, 2300))).toString()
        }
        File(mappa, Fajlok.MINTA).writeText(sorok.joinToString("\n") + "\n")
        val e = Kimenet.feldolgoz(mappa)
        assertTrue(e.javaslatok.any { it.ok == Ok.ORAJEL_KORLAT })
        assertTrue(e.esesek.none { it.fooOk == Ok.HO })
    }

    @Test
    fun szunetNemEses() {
        val mappa = File(tmp.root, "szunet"); keszit(mappa)
        // seconds 80..84: quick menu open -> paused, 0 FPS
        val sorok = File(mappa, Fajlok.MINTA).readLines().filter { it.startsWith("{\"") }.map {
            val o = JSONObject(it)
            if (o.getDouble("t").toInt() in 80..84) o.put("fps", 0.0).put("szunet", 1) else o
        }
        File(mappa, Fajlok.MINTA).writeText(sorok.joinToString("\n") { it.toString() } + "\n")
        val e = Kimenet.feldolgoz(mappa)
        assertTrue(e.esesek.none { it.kezdet in 79..85 })
        assertEquals(5, e.szunetMp)
    }
}
