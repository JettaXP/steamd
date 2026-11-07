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
        "#1A97FF" to "#BEC6DC",
        "#DFE3E6" to "#E3E2E6",
        "#8B929A" to "#C4C6D0",
        "#3D4450" to "#44474F",
        "#30333A" to "#2B2C31",
        "#60636A" to "#8E9099",
        "#2F343E" to "#2B2C31",
    )

    enum class Role { Primary, Secondary, Surface, SurfaceContainerLow, SurfaceContainer, SurfaceContainerHigh,
        SecondaryContainer, OnSurface, OnSurfaceVariant, Outline, OutlineVariant }

    val ROLES: Map<String, Role> = linkedMapOf(
        "#1F2127" to Role.Surface,
        "#23252A" to Role.SurfaceContainerLow,
        "#2A2C34" to Role.SurfaceContainer,
        "#3C3F49" to Role.SecondaryContainer,
        "#1A9FFF" to Role.Primary,
        "#1A97FF" to Role.Secondary,
        "#DFE3E6" to Role.OnSurface,
        "#8B929A" to Role.OnSurfaceVariant,
        "#3D4450" to Role.OutlineVariant,
        "#30333A" to Role.SurfaceContainerHigh,
        "#60636A" to Role.Outline,
        "#2F343E" to Role.SurfaceContainerHigh,
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

    private val ICON_PATHS = listOf(
        "M18.7666 10.6777L10.6777 18.7666L2.22217" to
            "m17.8 11.9l-6 6q-.2.2-.6.4t-.6.1q-.3 0-.6-.1t-.6-.4l-7.4-7.4q-.2-.2-.4-.5t-.1-.6l0-6q0-.7.5-1.2t1.2-.5l6 0q.3 0 .6.1t.5.4l7.3 7.4q.2.2 .4.6t.1.6q0 .3-.1.6t-.4.6zm-12.4-5.2q.5 0 .9-.4t.4-.9q0-.5-.4-.9t-.9-.4q-.5 0-.9.4t-.4.9q0 .5.4 .9t.9.4z",
        "M3.08634 17.7158H16.9428C17.8015 17.7158" to
            "m3.33 17.5q-.69 0-1.18-.49t-.49-1.18l0-12.83q0-.15.12-.2t.23.05l1.04 1.04l1.08-1.1q.12-.12.29-.12t.29.12l1.1 1.1l1.1-1.1q.12-.12.29-.12t.29.12l1.08 1.1l1.1-1.1q.12-.12.29-.12t.29.12l1.1 1.1l1.08-1.1q.12-.12.29-.12t.29.12l1.1 1.1l1.1-1.1q.12-.12.29-.12t.29.12l1.08 1.1l1.04-1.04q.1-.1.23-.05t.12.2l0 12.83q0 .69-.49 1.18t-1.18.49l-13.33 0zm0-1.67l5.83 0l0-5l-5.83 0l0 5zm7.5 0l5.83 0l0-1.67l-5.83 0l0 1.67zm0-3.33l5.83 0l0-1.67l-5.83 0l0 1.67zm-7.5-3.33l13.33 0l0-2.5l-13.33 0l0 2.5z",
        "M3.54839 4.13364C4.64516 3.89862 5.66359" to
            "m10 18.25q-.15 0-.27-.02t-.25-.06q-2.81-.94-4.48-3.47t-1.67-5.45l0-3.94q0-.52.3-.94t.78-.6l5-1.88q.29-.1.58-.1t.58.1l5 1.88q.48.19 .78.6t.3.94l0 3.94q0 2.92-1.67 5.45t-4.48 3.47q-.12.04-.25.06t-.27.02z",
        "M17.7777 13.3333V14.4444H2.22217V13.3333" to
            "m4.17 15.83q-.35 0-.59-.24t-.24-.59q0-.35.24-.59t.59-.24l.83 0l0-5.83q0-1.73 1.04-3.07t2.71-1.76l0-.58q0-.52.36-.89t.89-.36q.52 0 .89.36t.36.89l0 .58q1.67.42 2.71 1.76t1.04 3.07l0 5.83l.83 0q.35 0 .59.24t.24.59q0 .35-.24.59t-.59.24l-11.67 0zm5.83 2.5q-.69 0-1.18-.49t-.49-1.18l3.33 0q0 .69-.49 1.18t-1.18.49z",
    )
    private const val SVG24 = "<svg width=24 height=24 viewBox=\"0 0 24 24\"fill=none xmlns=http://www.w3.org/2000/svg><path d=\""
    private val ICON_DONORS = listOf(SVG24 + "M16 21H16.2", SVG24 + "M2.5 21.4998L8.")

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
        val materialIcons: Boolean = true,
    )

    class Result(val bundle: ByteArray, val hermesVersion: Int, val colorsReplaced: Int, val storeTheme: Boolean,
                 val radius: Boolean, val log: List<String>)

    fun apply(bundle: ByteArray, options: Options): Result {
        val hb = HermesBundle(bundle)
        val log = mutableListOf<String>()
        val colorOffsets = HashMap<String, Int>()
        val colors = if (options.colors) patchColors(hb, options.palette, log, colorOffsets) else 0
        val store = if (options.storeTheme) patchWebView(hb, options.primary, options.cssUrl, log) else false
        val radius = options.radius?.let { patchRadius(hb, it, log) } ?: false
        if (options.colors) patchTabMarker(hb, options.palette, colorOffsets, log)
        if (!options.uppercase) patchUppercase(hb, log)
        patchGuardCode(hb, log)
        if (options.materialIcons) patchIcons(hb, log)
        return Result(hb.finish(), hb.version, colors, store, radius, log)
    }

    private fun patchTabMarker(hb: HermesBundle, palette: Map<String, String>, offsets: Map<String, Int>,
                               log: MutableList<String>): Boolean {
        val accent = palette["#1A9FFF"]?.uppercase()
        val a = hb.findExact("#66C0F4")
        val b = hb.findExact("#2D73FF")
        val holder = hb.findExact("Custom tabs are NYI")
        if (accent == null || accent !in offsets || a.size != 1 || b.size != 1 || holder.size != 1 ||
            maxOf(a[0], b[0], holder[0]) > 0xFFFF) {
            log += "skip tab marker: not found in this version"; return false
        }
        val sig = byteArrayOf(0x52, (a[0] and 0xFF).toByte(), (a[0] shr 8).toByte(), (b[0] and 0xFF).toByte(), (b[0] shr 8).toByte())
        val hits = hb.findInArrays(sig)
        if (hits.size != 1) { log += "skip tab marker: colour array not found"; return false }
        hb.putShort(hits[0] + 1, holder[0])
        hb.putShort(hits[0] + 3, holder[0])
        hb.repoint(holder[0], offsets.getValue(accent), 7)
        log += "tab marker: $accent"
        return true
    }

    private fun patchGuardCode(hb: HermesBundle, log: MutableList<String>): Boolean {
        val ids = HashMap<String, Int>()
        for (name in listOf("1", "medium", "light", "center")) {
            val l = hb.findExact(name)
            if (l.size != 1 || l[0] > 0xFFFF) { log += "skip guard code: strings not found"; return false }
            ids[name] = l[0]
        }
        fun le(v: Int) = byteArrayOf((v and 0xFF).toByte(), (v shr 8).toByte())
        val sig = byteArrayOf(0x52) + le(ids.getValue("1")) + le(ids.getValue("medium")) +
            byteArrayOf(0x01, 0x51) + le(ids.getValue("center"))
        val hits = hb.findInFile(sig).filter { it >= hb.objectValuesOffset && it < hb.objectValuesOffset + hb.objectValuesSize }
        if (hits.size != 1) { log += "skip guard code: style not found"; return false }
        hb.putShort(hits[0] + 3, ids.getValue("light"))
        log += "guard code: light"
        return true
    }

    private fun patchIcons(hb: HermesBundle, log: MutableList<String>): Boolean {
        val targets = ICON_PATHS.map { (prefix, path) ->
            val l = hb.findStartingWith(prefix)
            if (l.size != 1) { log += "skip icons: original paths not found"; return false }
            l[0] to path.toByteArray(Charsets.US_ASCII)
        }
        val slots = ICON_DONORS.map { prefix ->
            val d = hb.findDonor(listOf(prefix), 1) ?: run { log += "skip icons: no donor strings"; return false }
            intArrayOf(hb.storageOffsetOf(d), hb.storageOffsetOf(d) + hb.byteLength(d))
        }
        for ((i, raw) in targets) {
            val slot = slots.firstOrNull { it[1] - it[0] >= raw.size } ?: run { log += "skip icons: donors too small"; return false }
            hb.writeStorage(slot[0], raw)
            hb.repoint(i, slot[0], raw.size)
            slot[0] += raw.size
        }
        log += "icons: Material Symbols"
        return true
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

    private fun patchColors(hb: HermesBundle, palette: Map<String, String>, log: MutableList<String>,
                            offsets: MutableMap<String, Int>): Int {
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
        offsets.putAll(pos)
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
