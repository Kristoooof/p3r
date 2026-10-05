package app.gamenative.figyelo.elemzes

import app.gamenative.figyelo.elemzes.Kimenet.r1
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Self-contained (offline) HTML report with an inline SVG timeline chart. */
object JelentesHtml {

    fun html(m: Munkamenet, e: Elemzes): String {
        val meta = m.meta
        val eszkoz = meta.optJSONObject("eszkoz") ?: JSONObject()
        val kontener = meta.optJSONObject("kontener") ?: JSONObject()
        val jatek = meta.optString("jatek").ifEmpty { "Ismeretlen játék" }

        val adat = JSONObject().apply {
            put("t", JSONArray(e.idovonal.map { it.t }))
            put("fps", JSONArray(e.idovonal.map { it.fps?.r1() ?: JSONObject.NULL }))
            put("gpu", JSONArray(e.idovonal.map { it.gpu ?: JSONObject.NULL }))
            put("top", JSONArray(e.idovonal.map { it.topSzal?.cpu?.coerceAtMost(100) ?: JSONObject.NULL }))
            put("topNev", JSONArray(e.idovonal.map { it.topSzal?.nev ?: "" }))
            put("esesek", JSONArray(e.esesek.map { JSONArray().put(it.kezdet).put(it.veg).put(Ok.cimke(it.fooOk)) }))
            put(
                "jelek",
                JSONArray(m.jelek.map { JSONArray().put(it.t.roundToInt()).put(it.cimke) }),
            )
        }.toString().replace("</", "<\\/")

        val fontos = e.javaslatok.filter { it.szint != Szint.ALACSONY }
        val alacsony = e.javaslatok.filter { it.szint == Szint.ALACSONY }

        return buildString {
            append(
                """<!doctype html>
<html lang="hu"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Figyelő jelentés</title>
<style>
:root{--bg:#f6f7f9;--card:#fff;--fg:#1d2330;--muted:#5d6675;--line:#e1e5ec;--fps:#2563eb;--gpu:#d97706;--top:#7c3aed;--drop:rgba(220,38,38,.16);--mark:#059669;--high:#dc2626;--mid:#d97706;--low:#6b7280}
@media (prefers-color-scheme: dark){:root{--bg:#11141a;--card:#1a1f28;--fg:#e8ebf1;--muted:#9aa3b2;--line:#2c3340;--fps:#60a5fa;--gpu:#fbbf24;--top:#a78bfa;--drop:rgba(248,113,113,.20);--mark:#34d399;--high:#f87171;--mid:#fbbf24;--low:#9ca3af}.badge{color:#11141a}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:980px;margin:0 auto;padding:16px}
h1{font-size:20px;margin:4px 0 2px}h2{font-size:17px;margin:22px 0 8px}
.muted{color:var(--muted)}.small{font-size:13px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 14px;margin:10px 0}
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:10px}
.kpi{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:10px 12px}
.kpi b{display:block;font-size:22px}.kpi span{color:var(--muted);font-size:13px}
.chartwrap{overflow-x:auto;-webkit-overflow-scrolling:touch}
.legend{display:flex;flex-wrap:wrap;gap:12px;font-size:13px;color:var(--muted);margin-bottom:6px}
.legend i{display:inline-block;width:14px;height:3px;vertical-align:middle;margin-right:5px;border-radius:2px}
.badge{display:inline-block;font-size:12px;font-weight:600;padding:1px 8px;border-radius:999px;color:#fff;margin-right:6px}
.MAGAS{background:var(--high)}.KOZEPES{background:var(--mid)}.ALACSONY{background:var(--low)}
table{border-collapse:collapse;width:100%;font-size:14px}th,td{text-align:left;padding:6px 6px;border-bottom:1px solid var(--line);vertical-align:top}
th{color:var(--muted);font-weight:600;font-size:13px}
.tablewrap{overflow-x:auto}
details{margin-top:8px}summary{cursor:pointer;color:var(--muted)}
.bar{height:8px;border-radius:4px;background:var(--fps)}
ul{margin:4px 0 0 18px;padding:0}
</style></head><body><main>
""",
            )
            append("<h1>").append(esc(jatek)).append("</h1>")
            append("<div class=\"muted small\">")
                .append(esc(meta.optString("kezdes")))
                .append(" · ").append(idoSzoveg(e.hosszMp)).append(" mérés")
                .append(" · ").append(esc(listOf(eszkoz.optString("gyarto"), eszkoz.optString("modell")).filter { it.isNotBlank() }.joinToString(" ")))
                .append(eszkoz.optString("soc").takeIf { it.isNotBlank() }?.let { " (" + esc(it) + ")" } ?: "")
                .append("</div>")
            append("<div class=\"muted small\">")
                .append(esc(kontenerSor(kontener)))
                .append("</div>")

            append("<div class=\"kpis\" style=\"margin-top:12px\">")
            kpi(e.medianFps?.let { it.roundToInt().toString() } ?: "–", "medián FPS")
            kpi(e.egySzazalekLowFps?.let { it.roundToInt().toString() } ?: "–", "1% low FPS")
            kpi(e.esesek.size.toString(), "esés")
            kpi(idoSzoveg(e.esesIdoMp), "esésben töltött idő")
            append("</div>")

            append("<h2>Idővonal</h2><div class=\"card\">")
            append(
                "<div class=\"legend\"><span><i style=\"background:var(--fps)\"></i>FPS (bal tengely)</span>" +
                    "<span><i style=\"background:var(--gpu)\"></i>GPU % (jobb)</span>" +
                    "<span><i style=\"background:var(--top)\"></i>legterheltebb szál % (jobb)</span>" +
                    "<span><i style=\"background:var(--drop);height:10px\"></i>esés</span>" +
                    "<span><i style=\"background:var(--mark)\"></i>jelölés</span></div>",
            )
            append("<div class=\"chartwrap\"><svg id=\"chart\" height=\"260\"></svg></div>")
            append("<div class=\"muted small\" id=\"tip\">Érintsd meg a grafikont egy pont részleteiért.</div></div>")

            append("<h2>Javaslatok</h2>")
            if (fontos.isEmpty()) {
                append("<div class=\"card muted\">Nincs magas vagy közepes jelentőségű javaslat.</div>")
            }
            fontos.forEach { javaslat(it) }
            if (alacsony.isNotEmpty()) {
                append("<details><summary>Alacsony jelentőségű javaslatok (").append(alacsony.size).append(")</summary>")
                alacsony.forEach { javaslat(it) }
                append("</details>")
            }

            if (e.okEloszlas.isNotEmpty()) {
                append("<h2>Az esések fő okai</h2><div class=\"card\">")
                val ossz = e.esesIdoMp.coerceAtLeast(1)
                e.okEloszlas.entries.sortedByDescending { it.value }.forEach { (ok, mp) ->
                    val szazalek = (mp * 100.0 / ossz).roundToInt()
                    append("<div style=\"margin:6px 0\"><div class=\"small\">").append(esc(Ok.cimke(ok)))
                        .append(" – ").append(idoSzoveg(mp)).append(" (").append(szazalek).append("%)</div>")
                        .append("<div class=\"bar\" style=\"width:").append(szazalek.coerceAtLeast(2)).append("%\"></div></div>")
                }
                append("</div>")
            }

            append("<h2>Esések</h2>")
            if (e.esesek.isEmpty()) {
                append("<div class=\"card muted\">Nem volt esés a mérés alatt.</div>")
            } else {
                append("<div class=\"card tablewrap\"><table><tr><th>Mikor</th><th>Hossz</th><th>Min FPS</th><th>Leghosszabb képkocka</th><th>Fő ok</th><th>Bizonyítékok</th></tr>")
                e.esesek.take(40).forEach { esesSor(it) }
                append("</table>")
                if (e.esesek.size > 40) {
                    append("<details><summary>További ").append(e.esesek.size - 40).append(" esés</summary><table>")
                    e.esesek.drop(40).forEach { esesSor(it) }
                    append("</table></details>")
                }
                append("</div>")
            }

            append("<h2>Szálak</h2><div class=\"card tablewrap\"><table><tr><th>Szál</th><th>Kategória</th><th>Átlag CPU</th><th>Csúcs</th></tr>")
            e.topSzalak.forEach {
                append("<tr><td>").append(esc(it.nev)).append("</td><td>").append(esc(SzalKategoria.cimke(it.kategoria)))
                    .append("</td><td>").append(it.atlagCpu.roundToInt()).append("%</td><td>").append(it.maxCpu).append("%</td></tr>")
            }
            append("</table><p class=\"muted small\">100% = egy teljes CPU-mag.</p>")
            append("<table><tr><th>Kategória</th><th>Átlagos összes CPU</th></tr>")
            e.kategoriaAtlag.entries.sortedByDescending { it.value }.filter { it.value >= 0.5 }.forEach { (k, v) ->
                append("<tr><td>").append(esc(SzalKategoria.cimke(k))).append("</td><td>").append(v.roundToInt()).append("%</td></tr>")
            }
            append("</table></div>")

            append("<h2>Mérési adatforrások</h2><div class=\"card small\"><ul>")
            forras("GPU-terhelés", e.gpuOlvashato, meta.optJSONObject("olvashato")?.optString("gpuForras"))
            forras("CPU-órajelek", e.frekOlvashato, null)
            forras("Hőmérséklet", e.homOlvashato, null)
            append("<li>Szálnevek: ")
            append(
                when {
                    e.szalnevArany == null -> "nincs adat"
                    e.szalnevArany >= 0.5 -> "olvashatók (" + (e.szalnevArany * 100).roundToInt() + "% elnevezett)"
                    else -> "nagyrészt hiányoznak (" + (e.szalnevArany * 100).roundToInt() + "% elnevezett)"
                },
            )
            append("</li></ul></div>")

            append("<p class=\"muted small\">Készítette: GameNative Figyelő ").append(esc(meta.optString("verzio")))
                .append(". Nyers adatok: minta.jsonl, kepkockak.jsonl, naplo.jsonl, jelek.jsonl, meta.json; ")
                .append("összegzés: osszegzes.json, idovonal.csv.</p>")

            append("<script>const D=").append(adat).append(";\n").append(SCRIPT).append("</script>")
            append("</main></body></html>")
        }
    }

    private fun StringBuilder.kpi(ertek: String, cimke: String) {
        append("<div class=\"kpi\"><b>").append(esc(ertek)).append("</b><span>").append(esc(cimke)).append("</span></div>")
    }

    private fun StringBuilder.javaslat(j: Javaslat) {
        append("<div class=\"card\"><span class=\"badge ").append(j.szint).append("\">").append(Szint.cimke(j.szint)).append("</span>")
            .append("<b>").append(esc(j.cim)).append("</b>")
        if (j.arany > 0.0) append(" <span class=\"muted small\">(az esésidő ").append((j.arany * 100).roundToInt()).append("%-a)</span>")
        append("<div style=\"margin-top:6px\">").append(esc(j.szoveg)).append("</div></div>")
    }

    private fun StringBuilder.esesSor(es: Eses) {
        append("<tr><td>").append(perc(es.kezdet))
        if (es.jelek.isNotEmpty()) append("<br><span class=\"muted small\">").append(esc(es.jelek.joinToString(", "))).append("</span>")
        append("</td><td>").append(es.hossz).append(" mp</td><td>")
            .append(es.minFps?.roundToInt()?.toString() ?: "–").append("</td><td>")
            .append(es.maxFt?.roundToInt()?.let { "$it ms" } ?: "–").append("</td><td>")
            .append(esc(Ok.cimke(es.fooOk))).append("</td><td class=\"small\">")
            .append(esc(es.bizonyitekok.joinToString("; ").ifEmpty { "–" })).append("</td></tr>")
    }

    private fun StringBuilder.forras(nev: String, ok: Boolean, reszlet: String?) {
        append("<li>").append(nev).append(": ").append(if (ok) "olvasható" else "nem olvasható ezen a telefonon")
        if (ok && !reszlet.isNullOrBlank()) append(" <span class=\"muted\">(").append(esc(reszlet)).append(")</span>")
        append("</li>")
    }

    private fun kontenerSor(k: JSONObject): String {
        val reszek = ArrayList<String>()
        val emulator = k.optString("emulator")
        if (emulator.isNotBlank()) {
            val verzio = if (emulator.contains("fex", ignoreCase = true)) k.optString("fexVerzio") else k.optString("box64Verzio")
            reszek += listOf(emulator, verzio).filter { it.isNotBlank() }.joinToString(" ")
        }
        k.optString("wineVerzio").takeIf { it.isNotBlank() }?.let { reszek += it }
        val driver = listOf(k.optString("driver"), k.optString("driverVerzio")).filter { it.isNotBlank() }.joinToString(" ")
        if (driver.isNotBlank()) reszek += driver
        k.optString("dxwrapper").takeIf { it.isNotBlank() }?.let { reszek += it }
        return reszek.joinToString(" · ")
    }

    private fun perc(mp: Int): String = "%d:%02d".format(mp / 60, mp % 60)

    private fun idoSzoveg(mp: Int): String = if (mp >= 60) "${mp / 60} p ${mp % 60} mp" else "$mp mp"

    fun esc(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private val SCRIPT = """
(function(){
  var svg=document.getElementById('chart'),tip=document.getElementById('tip');
  var n=D.t.length; if(!n){tip.textContent='Nincs adat.';return;}
  var H=260,padL=34,padR=34,padT=10,padB=24;
  var wrapW=svg.parentNode.clientWidth||600;
  var W=Math.max(wrapW,n*3+padL+padR); svg.setAttribute('width',W); svg.setAttribute('viewBox','0 0 '+W+' '+H);
  var tMax=D.t[n-1]||1, fpsMax=10;
  D.fps.forEach(function(v){if(v!=null&&v>fpsMax)fpsMax=v;}); fpsMax=Math.ceil(fpsMax/10)*10;
  function x(t){return padL+(W-padL-padR)*t/tMax;}
  function yF(v){return padT+(H-padT-padB)*(1-v/fpsMax);}
  function yP(v){return padT+(H-padT-padB)*(1-v/100);}
  var css=getComputedStyle(document.documentElement);
  function c(n){return css.getPropertyValue(n).trim();}
  var ns='http://www.w3.org/2000/svg';
  function el(name,attrs,text){var e=document.createElementNS(ns,name);for(var k in attrs)e.setAttribute(k,attrs[k]);if(text!=null)e.textContent=text;svg.appendChild(e);return e;}
  D.esesek.forEach(function(d){el('rect',{x:x(d[0]-1),y:padT,width:Math.max(2,x(d[1])-x(d[0]-1)),height:H-padT-padB,fill:c('--drop')});});
  for(var i=0;i<=4;i++){var v=fpsMax*i/4,y=yF(v);
    el('line',{x1:padL,x2:W-padR,y1:y,y2:y,stroke:c('--line'),'stroke-width':1});
    el('text',{x:padL-4,y:y+4,'text-anchor':'end','font-size':11,fill:c('--muted')},Math.round(v));
    el('text',{x:W-padR+4,y:y+4,'font-size':11,fill:c('--muted')},(25*i)+'%');}
  var step=tMax>1200?300:(tMax>300?60:(tMax>60?15:5));
  for(var s=0;s<=tMax;s+=step){el('text',{x:x(s),y:H-6,'text-anchor':'middle','font-size':11,fill:c('--muted')},Math.floor(s/60)+':'+('0'+(s%60)).slice(-2));}
  function path(arr,yf,color,width){var d='',pen=false;
    for(var i=0;i<n;i++){var v=arr[i];if(v==null){pen=false;continue;}d+=(pen?'L':'M')+x(D.t[i]).toFixed(1)+' '+yf(v).toFixed(1);pen=true;}
    if(d)el('path',{d:d,fill:'none',stroke:color,'stroke-width':width,'stroke-linejoin':'round'});}
  path(D.top,yP,c('--top'),1.2); path(D.gpu,yP,c('--gpu'),1.2); path(D.fps,yF,c('--fps'),2);
  D.jelek.forEach(function(j){var xx=x(j[0]);el('line',{x1:xx,x2:xx,y1:padT,y2:H-padB,stroke:c('--mark'),'stroke-width':1.5,'stroke-dasharray':'4 3'});
    el('text',{x:xx+3,y:padT+12,'font-size':11,fill:c('--mark')},j[1]);});
  var cursor=el('line',{x1:0,x2:0,y1:padT,y2:H-padB,stroke:c('--muted'),'stroke-width':1,visibility:'hidden'});
  function show(ev){var r=svg.getBoundingClientRect();var px=(ev.touches?ev.touches[0].clientX:ev.clientX)-r.left;
    var t=(px-padL)/(W-padL-padR)*tMax,best=0;for(var i=0;i<n;i++){if(Math.abs(D.t[i]-t)<Math.abs(D.t[best]-t))best=i;}
    var xx=x(D.t[best]);cursor.setAttribute('x1',xx);cursor.setAttribute('x2',xx);cursor.setAttribute('visibility','visible');
    var tt=D.t[best];tip.textContent=Math.floor(tt/60)+':'+('0'+(tt%60)).slice(-2)+' · FPS '+(D.fps[best]==null?'–':D.fps[best])+
      ' · GPU '+(D.gpu[best]==null?'–':D.gpu[best]+'%')+' · '+(D.topNev[best]||'?')+' '+(D.top[best]==null?'–':D.top[best]+'%');}
  svg.addEventListener('click',show);svg.addEventListener('touchstart',show,{passive:true});svg.addEventListener('touchmove',show,{passive:true});
})();
"""
}
