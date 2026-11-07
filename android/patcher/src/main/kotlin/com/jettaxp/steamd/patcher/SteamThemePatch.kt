package com.jettaxp.steamd.patcher

object SteamThemePatch {
    const val DEFAULT_CSS_URL = "https://steamd.jettax.xyz/theme/m3.css"
    const val DEFAULT_PRIMARY = "#AAC7FF"

    val DEFAULT_PALETTE: Map<String, String> = linkedMapOf(
        "#1F2127" to "#1B1B1F",
        "#23252A" to "#1F1F23",
        "#2A2C34" to "#26272B",
        "#3C3F49" to "#3E4759",
        "#1A9FFF" to "#8AB4F8",
        "#1A97FF" to "#8AB4F8",
        "#DFE3E6" to "#E3E2E6",
        "#8B929A" to "#C4C6D0",
        "#3D4450" to "#44474F",
        "#30333A" to "#2B2C31",
        "#60636A" to "#8E9099",
    )

    enum class Role { Primary, Surface, SurfaceContainerLow, SurfaceContainer, SurfaceContainerHigh,
        SecondaryContainer, OnSurface, OnSurfaceVariant, Outline, OutlineVariant }

    val ROLES: Map<String, Role> = linkedMapOf(
        "#1F2127" to Role.Surface,
        "#23252A" to Role.SurfaceContainerLow,
        "#2A2C34" to Role.SurfaceContainer,
        "#3C3F49" to Role.SecondaryContainer,
        "#1A9FFF" to Role.Primary,
        "#1A97FF" to Role.Primary,
        "#DFE3E6" to Role.OnSurface,
        "#8B929A" to Role.OnSurfaceVariant,
        "#3D4450" to Role.OutlineVariant,
        "#30333A" to Role.SurfaceContainerHigh,
        "#60636A" to Role.Outline,
    )

    fun paletteFrom(roles: Map<Role, Int>): Map<String, String> =
        ROLES.mapValuesTo(LinkedHashMap()) { (_, role) -> hex(roles.getValue(role)) }

    fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

    private const val ORIGINAL_JS_START = "window.navigator.standalone = true;(function() {"
    private val ORIGINAL_JS_MARKERS = listOf("HistoryChanged", "history.pushState = wrap( history.pushState )")

    private const val WEBVIEW_JS =
        "window.navigator.standalone=true;(function(){" +
            "var m='{\"event_name\":\"HistoryChanged\"}'," +
            "p=function(){window.ReactNativeWebView.postMessage(m)}," +
            "w=function(f){return function(){var r=f.apply(this,arguments);p();return r}};" +
            "history.pushState=w(history.pushState);history.replaceState=w(history.replaceState);" +
            "addEventListener(\"popstate\",p);" +
            "(function t(){var d=document.documentElement;if(!d)return setTimeout(t,5);" +
            "d.style.setProperty(\"--sd-p\",\"{SEED}\");" +
            "var l=document.createElement(\"link\");l.rel=\"stylesheet\";l.href=\"{CSS}\";d.appendChild(l)})()" +
            "})();"

    private val COLOR_DONORS = listOf(
        "[expo-av]: Expo AV has been deprecated",
        "This unit of work tag should not have side-effects",
    )
    private val SCRIPT_DONORS = listOf(
        "<div><div>Query Details</div>",
        "<svg width=24 height=24 viewBox=\"0 0 24 24\"fill=none xmlns=http://www.w3.org/2000/svg><path class=copier",
    )

    private val RADIUS_SIGNATURE = hexBytes(
        "73000000001c0200008403000072040000000a0000007428000000200000001800000012000000"
    )
    private const val RADIUS_OFFSET = 13
    val DEFAULT_RADIUS = 12 to 24

    private fun hexBytes(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val HEX = Regex("#[0-9A-Fa-f]{6}")
    private val CSS_URL = Regex("https://[\\w.\\-/~%]+\\.css")

    data class Options(
        val palette: Map<String, String> = DEFAULT_PALETTE,
        val primary: String = DEFAULT_PRIMARY,
        val cssUrl: String = DEFAULT_CSS_URL,
        val colors: Boolean = true,
        val storeTheme: Boolean = true,
        val radius: Pair<Int, Int>? = DEFAULT_RADIUS,
        val uppercase: Boolean = false,
    )

    class Result(val bundle: ByteArray, val hermesVersion: Int, val colorsReplaced: Int, val storeTheme: Boolean,
                 val radius: Boolean, val log: List<String>)

    fun apply(bundle: ByteArray, options: Options): Result {
        val hb = HermesBundle(bundle)
        val log = mutableListOf<String>()
        val colors = if (options.colors) patchColors(hb, options.palette, log) else 0
        val store = if (options.storeTheme) patchWebView(hb, options.primary, options.cssUrl, log) else false
        val radius = options.radius?.let { patchRadius(hb, it, log) } ?: false
        if (!options.uppercase) patchUppercase(hb, log)
        return Result(hb.finish(), hb.version, colors, store, radius, log)
    }

    private fun patchUppercase(hb: HermesBundle, log: MutableList<String>): Boolean {
        val up = hb.findExact("uppercase")
        val esm = hb.findExact("__esModule")
        if (up.size != 1 || esm.size != 1 || up[0] > 0xFFFF || esm[0] > 0xFFFF) {
            log += "skip uppercase: strings not found in this version"; return false
        }
        val sig = byteArrayOf(0x37, 0x00, 0x02, 0x05, (up[0] and 0xFF).toByte(), (up[0] shr 8).toByte(), 0x79, 0x01)
        val hits = hb.findInFile(sig)
        if (hits.size != 1) { log += "skip uppercase: text component not found (${hits.size} matches)"; return false }
        hb.putShort(hits[0] + 4, esm[0])
        log += "uppercase text: off"
        return true
    }

    private fun patchRadius(hb: HermesBundle, radius: Pair<Int, Int>, log: MutableList<String>): Boolean {
        val hits = hb.findInObjectValues(RADIUS_SIGNATURE)
        if (hits.size != 1) { log += "skip corner radius: base theme not found in this version"; return false }
        val p = hits[0] + RADIUS_OFFSET
        hb.putInt(p + 1, radius.first)
        hb.putInt(p + 5, radius.second)
        log += "corner radius: ${radius.first}, ${radius.second}"
        return true
    }

    private fun patchColors(hb: HermesBundle, palette: Map<String, String>, log: MutableList<String>): Int {
        data class Job(val index: Int, val old: String, val new: String)
        val jobs = mutableListOf<Job>()
        for ((old, new) in palette) {
            if (!HEX.matches(old) || !HEX.matches(new)) throw PatchException("Bad colour pair $old -> $new")
            val idx = hb.findExact(old)
            if (idx.isEmpty()) log += "skip $old: not in this version"
            for (i in idx) {
                if (!hb.isAscii(i) || hb.isOverflow(i)) { log += "skip $old: unexpected string kind"; continue }
                jobs += Job(i, old, new.uppercase())
            }
        }
        if (jobs.isEmpty()) return 0
        val values = jobs.map { it.new }.toSortedSet().toList()
        val donor = hb.findDonor(COLOR_DONORS, 7 * values.size)
            ?: throw PatchException("This Steam version is not supported yet (no room for colours)")
        val base = hb.storageOffsetOf(donor)
        val pos = HashMap<String, Int>()
        values.forEachIndexed { k, v ->
            hb.writeStorage(base + 7 * k, v.toByteArray(Charsets.US_ASCII))
            pos[v] = base + 7 * k
        }
        for (j in jobs) {
            hb.repoint(j.index, pos.getValue(j.new), 7)
            log += "${j.old} -> ${j.new}"
        }
        return jobs.size
    }

    private fun patchWebView(hb: HermesBundle, primary: String, cssUrl: String, log: MutableList<String>): Boolean {
        val idx = hb.findStartingWith(ORIGINAL_JS_START)
            .filter { i -> hb.string(i)!!.let { s -> ORIGINAL_JS_MARKERS.all { it in s } } }
        if (idx.size != 1) { log += "skip store theme: WebView script not found in this version"; return false }
        if (!HEX.matches(primary)) throw PatchException("Bad primary colour $primary")
        if (!CSS_URL.matches(cssUrl)) throw PatchException("Bad CSS URL $cssUrl")
        val js = WEBVIEW_JS.replace("{SEED}", primary).replace("{CSS}", cssUrl).toByteArray(Charsets.US_ASCII)
        val donor = hb.findDonor(SCRIPT_DONORS, js.size)
            ?: throw PatchException("No room for the ${js.size}-byte loader: use a shorter CSS URL")
        val off = hb.storageOffsetOf(donor)
        hb.writeStorage(off, js)
        hb.repoint(idx[0], off, js.size)
        log += "store theme: $cssUrl (seed $primary, ${js.size} bytes)"
        return true
    }
}
