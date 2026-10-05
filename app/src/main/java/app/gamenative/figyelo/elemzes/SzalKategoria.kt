package app.gamenative.figyelo.elemzes

/**
 * Thread categories used by the Figyelő report. Linux truncates thread names to 15
 * characters, so the patterns only look at the start / short substrings of the names
 * Wine passes through (e.g. "RenderThread 1", "AsyncLoadingThr", "dxvk-shader").
 */
object SzalKategoria {
    const val SHADER = "shader"
    const val BETOLTES = "betoltes"
    const val RENDER = "render"
    const val JATEK = "jatek"
    const val MUNKA = "munkaszal"
    const val HANG = "hang"
    const val WINE = "wine"
    const val EGYEB = "egyeb"

    val MIND = listOf(SHADER, BETOLTES, RENDER, JATEK, MUNKA, HANG, WINE, EGYEB)

    /** Short Hungarian label for the report. */
    fun cimke(kategoria: String): String = when (kategoria) {
        SHADER -> "Shaderfordítás"
        BETOLTES -> "Betöltés"
        RENDER -> "Renderelés"
        JATEK -> "Játék fő szál"
        MUNKA -> "Munkaszálak"
        HANG -> "Hang"
        WINE -> "Wine"
        else -> "Egyéb"
    }

    private val shaderMinta = listOf("shader", "pso", "compil", "pipeline", "spirv")
    private val betoltesMinta = listOf(
        "asyncload", "async load", "loading", "iothread", "iodispatch", "io dispatch", "fileio",
        "decompress", "oodle", "zlib", "pak", "streaming", "loader",
    )
    private val renderMinta = listOf(
        "renderthread", "render thread", "rhithread", "rhi thread", "rhisubmi", "rhiinterrupt",
        "dxvk-cs", "dxvk-submit", "dxvk-queue", "dxvk-present", "dxvk-frame", "vkd3d_queue",
        "vkd3d-queue", "present", "swapchain",
    )
    private val jatekMinta = listOf("gamethread", "game thread")
    private val munkaMinta = listOf(
        "taskgraph", "foreground w", "background w", "worker", "pool", "task",
    )
    private val hangMinta = listOf("audio", "xaudio", "faudio", "pulse", "sound", "mixer", "wasapi")
    private val wineMinta = listOf("wineserver", "wine", "services.exe", "winedevice", "explorer", "plugplay", "svchost", "rpcss")

    /**
     * @param szalNev Linux thread name (comm)
     * @param fofolyamatSzal true when tid == pid of the game process (Wine's main thread)
     * @param folyamatNev process name, e.g. "P3R-Win64-Shipping.exe"
     */
    fun besorol(szalNev: String, fofolyamatSzal: Boolean = false, folyamatNev: String = ""): String {
        val n = szalNev.lowercase()
        return when {
            shaderMinta.any { n.contains(it) } -> SHADER
            betoltesMinta.any { n.contains(it) } -> BETOLTES
            renderMinta.any { n.contains(it) } -> RENDER
            jatekMinta.any { n.contains(it) } -> JATEK
            fofolyamatSzal && !wineMinta.any { folyamatNev.lowercase().startsWith(it) } -> JATEK
            hangMinta.any { n.contains(it) } -> HANG
            munkaMinta.any { n.contains(it) } -> MUNKA
            wineMinta.any { n.contains(it) } -> WINE
            else -> EGYEB
        }
    }

    private val shaderNaploMinta = Regex(
        "shader|pipeline|compil|spir-?v|\\bpso\\b|dxbc|dxil",
        RegexOption.IGNORE_CASE,
    )

    /** True when a Wine/DXVK/VKD3D log line hints at shader or pipeline compilation. */
    fun shaderNaploSor(sor: String): Boolean = shaderNaploMinta.containsMatchIn(sor)
}
