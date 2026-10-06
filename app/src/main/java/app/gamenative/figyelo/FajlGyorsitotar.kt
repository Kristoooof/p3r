package app.gamenative.figyelo

import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import java.io.File

/**
 * Tracks how much of the game's big files (pak archives, EXE/DLLs, caches) sits in the kernel
 * page cache, using mmap + mincore on our own read-only mappings (this never reads file data).
 * Growth between snapshots = data the game paged in; shrinking = evicted under memory pressure.
 */
internal class FajlGyorsitotar {

    private class Kovetett(val ut: String, val nev: String, val meret: Long, val cim: Long, val vektor: ByteArray)

    private val lap = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(4096L).coerceAtLeast(1L)
    private val kovetett = LinkedHashMap<String, Kovetett>()
    private var utolsoLista = 0L

    /** Re-reads the game process's open / mapped files at most every [LISTA_MS]. */
    fun frissit(pidek: List<Int>, mostMs: Long) {
        if (mostMs - utolsoLista < LISTA_MS && kovetett.isNotEmpty()) return
        utolsoLista = mostMs
        val utak = HashSet<String>()
        for (pid in pidek) {
            runCatching {
                File("/proc/$pid/maps").forEachLine { sor ->
                    val i = sor.indexOf('/')
                    if (i > 0) utak += sor.substring(i).trim()
                }
            }
            runCatching {
                File("/proc/$pid/fd").listFiles()?.forEach { fd ->
                    runCatching { Os.readlink(fd.path) }.getOrNull()?.let { if (it.startsWith("/")) utak += it }
                }
            }
        }
        val jeloltek = utak.asSequence()
            .filter { u -> KIZART.none { u.startsWith(it) } && !u.endsWith(" (deleted)") }
            .mapNotNull { u -> runCatching { File(u) }.getOrNull()?.takeIf { it.isFile }?.let { u to it.length() } }
            .filter { it.second >= MIN_MERET }
            .sortedByDescending { it.second }
            .take(MAX_FAJL)
            .toList()
        for ((ut, meret) in jeloltek) {
            if (kovetett.containsKey(ut)) continue
            if (kovetett.size >= MAX_FAJL) break
            runCatching { megnyit(ut, meret) }.getOrNull()?.let { kovetett[ut] = it }
        }
    }

    private fun megnyit(ut: String, meret: Long): Kovetett {
        val fd = Os.open(ut, OsConstants.O_RDONLY, 0)
        try {
            val cim = Os.mmap(0L, meret, OsConstants.PROT_READ, OsConstants.MAP_SHARED, fd, 0L)
            val lapok = ((meret + lap - 1) / lap).toInt()
            val reszek = ut.split('/').filter { it.isNotEmpty() }
            return Kovetett(ut, reszek.takeLast(2).joinToString("/"), meret, cim, ByteArray(lapok))
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    /** `[[name, residentMB, sizeMB], …]` for every tracked file. */
    fun pillanatkep(): JSONArray {
        val eredmeny = JSONArray()
        for (k in kovetett.values) {
            val mb = runCatching {
                Os.mincore(k.cim, k.meret, k.vektor)
                var db = 0L
                for (b in k.vektor) if ((b.toInt() and 1) != 0) db++
                db * lap / MB
            }.getOrNull() ?: continue
            eredmeny.put(JSONArray().put(k.nev).put(mb).put(k.meret / MB))
        }
        return eredmeny
    }

    fun bezar() {
        for (k in kovetett.values) runCatching { Os.munmap(k.cim, k.meret) }
        kovetett.clear()
    }

    companion object {
        private const val MB = 1024L * 1024L
        private const val MIN_MERET = 16L * MB
        private const val MAX_FAJL = 16
        private const val LISTA_MS = 30_000L
        private val KIZART = listOf("/dev/", "/proc/", "/sys/", "/system/", "/apex/", "/vendor/", "/memfd:", "/dmabuf")
    }
}
