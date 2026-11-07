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
        Triple(
            "M20 10.2216L10.2216 20L0 9.77837V0H9.77837",
            "m17.8 11.9l-6 6q-.2.2-.6.4t-.6.1q-.3 0-.6-.1t-.6-.4l-7.4-7.4q-.2-.2-.4-.5t-.1-.6l0-6q0-.7.5-1.2t1.2-.5l6 0q.3 0 .6.1t.5.4l7.3 7.4q.2.2 .4.6t.1.6q0 .3-.1.6t-.4.6zm-12.4-5.2q.5 0 .9-.4t.4-.9q0-.5-.4-.9t-.9-.4q-.5 0-.9.4t-.4.9q0 .5.4 .9t.9.4z",
            "m17.8 11.9l-6 6q-.2.2-.6.4t-.6.1q-.3 0-.6-.1t-.6-.4l-7.4-7.4q-.2-.2-.4-.5t-.1-.6l0-6q0-.7.5-1.2t1.2-.5l6 0q.3 0 .6.1t.5.4l7.3 7.4q.2.2 .4.6t.1.6q0 .3-.1.6t-.4.6zm-7.1 4.8l6-6l-7.4-7.4l-6 0l0 6l7.4 7.4zm-5.3-10q.5 0 .9-.4t.4-.9q0-.5-.4-.9t-.9-.4q-.5 0-.9.4t-.4.9q0 .5.4 .9t.9.4zm4.6 3.3z",
        ),
        Triple(
            "M33 3H3V33C3 33.5523 3.44772 34 4 34H32",
            "m3.3 17.5q-.7 0-1.2-.5t-.5-1.2l0-12.8q0-.1.1-.2t.2.1l1 1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1-1q.1-.1.2-.1t.1.2l0 12.8q0 .7-.5 1.2t-1.2.5l-13.3 0zm0-1.7l5.8 0l0-5l-5.8 0l0 5zm7.5 0l5.8 0l0-1.7l-5.8 0l0 1.7zm0-3.3l5.8 0l0-1.7l-5.8 0l0 1.7zm-7.5-3.3l13.3 0l0-2.5l-13.3 0l0 2.5z",
            "m3.3 17.5q-.7 0-1.2-.5t-.5-1.2l0-12.8q0-.1.1-.2t.2.1l1 1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1-1q.1-.1.2-.1t.1.2l0 12.8q0 .7-.5 1.2t-1.2.5l-13.3 0zm0-1.7l5.8 0l0-5l-5.8 0l0 5zm7.5 0l5.8 0l0-1.7l-5.8 0l0 1.7zm0-3.3l5.8 0l0-1.7l-5.8 0l0 1.7zm-7.5-3.3l13.3 0l0-2.5l-13.3 0l0 2.5z",
        ),
        Triple(
            "M2.64516 3.68664C3.93548 3.41014 5.13364",
            "m10 18.2q-.1 0-.3 0t-.2-.1q-2.8-.9-4.5-3.5t-1.7-5.4l0-3.9q0-.5.3-.9t.8-.6l5-1.9q.3-.1.6-.1t.6.1l5 1.9q.5.2 .8.6t.3.9l0 3.9q0 2.9-1.7 5.4t-4.5 3.5q-.1 0-.2.1t-.3 0z",
            "m10 18.2q-.1 0-.3 0t-.2-.1q-2.8-.9-4.5-3.5t-1.7-5.4l0-3.9q0-.5.3-.9t.8-.6l5-1.9q.3-.1.6-.1t.6.1l5 1.9q.5.2 .8.6t.3.9l0 3.9q0 2.9-1.7 5.4t-4.5 3.5q-.1 0-.2.1t-.3 0zm0-1.7q2.2-.7 3.6-2.8t1.4-4.6l0-3.9l-5-1.9l-5 1.9l0 3.9q0 2.5 1.4 4.6t3.6 2.8zm0-6.6z",
        ),
        Triple(
            "M18 13.75V15H2V13.75L4.28571 10.625V6.25",
            "m4.2 15.8q-.4 0-.6-.2t-.2-.6q0-.4.2-.6t.6-.2l.8 0l0-5.8q0-1.7 1-3.1t2.7-1.8l0-.6q0-.5.4-.9t.9-.4q.5 0 .9.4t.4.9l0 .6q1.7.4 2.7 1.8t1 3.1l0 5.8l.8 0q.4 0 .6.2t.2.6q0 .4-.2.6t-.6.2l-11.7 0zm5.8 2.5q-.7 0-1.2-.5t-.5-1.2l3.3 0q0 .7-.5 1.2t-1.2.5z",
            "m4.2 15.8q-.4 0-.6-.2t-.2-.6q0-.4.2-.6t.6-.2l.8 0l0-5.8q0-1.7 1-3.1t2.7-1.8l0-.6q0-.5.4-.9t.9-.4q.5 0 .9.4t.4.9l0 .6q1.7.4 2.7 1.8t1 3.1l0 5.8l.8 0q.4 0 .6.2t.2.6q0 .4-.2.6t-.6.2l-11.7 0zm5.8-6.2zm0 8.8q-.7 0-1.2-.5t-.5-1.2l3.3 0q0 .7-.5 1.2t-1.2.5zm-3.3-4.2l6.7 0l0-5.8q0-1.4-1-2.4t-2.4-1q-1.4 0-2.4 1t-1 2.4l0 5.8z",
        ),
    )
    private const val SVG24 = "<svg width=24 height=24 viewBox=\"0 0 24 24\"fill=none xmlns=http://www.w3.org/2000/svg><path d=\""
    private val ICON_DONORS = listOf("M16 21H16.2", "M2.5 21.4998L8.", "M7.5 12L10.5 15", "M9 9L15 15M15 9").map { SVG24 + it }
    private const val ICON_SPARE_ENTRY = SVG24 + "M9 3H15M3 6H21M"
    private val ICON_FN_HEAD = hexBytes("2900002e01000236030101a32e01000137020102c37203016c070108080159042c037305")
    private val ICON_FN_TAIL = hexBytes("54050600050473045b0046010504015400030002015c00")

    private val NATIVE_BODIES = mapOf(
        "tab pill" to (
            "320a6c020137000201f2582a0a000036000202da2909002e01090a3701010347fd7603510101032a0a01012e01090d370101" +
            "0491e151010103370e0105bdd42e01090b37010106e7fd51010103370100073a83360000089a490001002e01090b37010109" +
            "21fd510101033707010a0dcf370c010ba4553701020c51580d04017301a459bd7001000004012e0409143701020c5158490b" +
            "04012a0a020b90240b30013906010d66003605060ef23704020c51587301b82254010506010477015c0137010b0fb3b67704" +
            "0e040104760d900704510d010b37040b10e87073011f86b80c040137010b10e8708e103704020c515873021b29160102042a" +
            "0a030136000011b30f0800012c0a0408760f920708730f825803106e0011401000689e2e00090e36020012a32e0009083701" +
            "0013fe6803002e04090e36060414a42e0409083605041517010408000200fe1f73093f0410e0640a0a74613f040abc400407" +
            "0dcf2e070910400407588573076c8c3f0407c29218082e07090e36100712a32e0a091c0307540810030a0707070300440708" +
            "00370a0b16cc660308731093593f0810d640080f0cfc40080ebdd453080a0b084407080108080d9222082e0a090e360b0a12" +
            "a32e0a091b030940090def6840090ca45554080b030a09440708023f04075b5404060305043f00045b5400020301005c0030" +
            "003902000d66003601020ef273000d17530001020077005c00"
            to
            "320a6c020137000201f2582a0a000036000202da2909002e01090a3701010347fd7603510101032a0a01012e01090d370101" +
            "0491e151010103370e0105bdd42e01090b37010106e7fd51010103370100073a83360000089a490001002e01090b37010109" +
            "21fd510101033707010a0dcf370c010ba4553701020c51580d04017301a459bd7e01000004012e0409143701020c5158490b" +
            "04012a0a020b90070b77015c0137010b0fb3b677040e040104760d900704510d010b37040b10e87073011f86b80c04013701" +
            "0b10e8708e103704020c515873021b29160102042a0a030136000011b30f0800012c0a0408760f920708730f825803106e00" +
            "0d401000689e2e00090e36020012a32e00090837010013fe6803002e04090e36060414a42e04090836050415170104080002" +
            "00fe1f73093f0410e0640a0a74613f040abc4004070dcf2e070910400407588573076c8c3f0407c20707030044070800370a" +
            "0b16cc660308731093593f0810d640080f0cfc40080ebdd453080a0b08020a05000400ef5a0000f74c0100761092070f7310" +
            "55d93b0a100051007310ce6d3b0a10006b9e3f0a085b2e10090e36101000a32e110908361111001754081003110a44070801" +
            "08080d9222082e0a090e360b0a12a32e0a091b030940090def6840090ca45554080b030a09440708023f04075b5404060305" +
            "043f00045b5400020301005c0077005c000000000000000000"),
        "menu cards" to (
            "320a6c000136170001e3360c00025b37150003cc663716000425ae370b000592da7603be110b032900002e00001a370b0006" +
            "847f2a0a000b2909002e00090236010007f02e00091a37000006847f10000b0053020103002e00090036010008716e000254" +
            "01010302007a00490501002a0a01056e0001490001002a0a02002e00091936020009a42e0009073601000a17030073044555" +
            "4000046b9e080617939f010000062e0409193608040ba32e0409073607040a17020406000500b8730000ef530100760d9207" +
            "05730d55d93b040d015100640a0ad6a53b040a02bc002e0a0919360e0a09a42e0a090b370d0a0cb76a030a2e0f091936110f" +
            "0ba32e0f090b37100f0dfe68030f73120d5d3f0f12f42e1209193614120ba32e1209073613120a17021204000300a6730000" +
            "aea008003f12155b5412140313123f0f125b54101103100f070f0300440f10002e1009193612100ba32e10090b3711100dfe" +
            "680310731383003f1013f42e1309193615130ba32e14092203133f1317a840131625ae5413150314133f10135b5410120311" +
            "10440f10012e10091a3710100e0b7c10100b109382000000102e1109193613110ba32e11090b3712110dfe6803117314d390" +
            "3f1114f42e1409193616140ba32e1409073615140a170214040003000b5b0000d6a008002e1709193619170ba32e17090c37" +
            "18170f3fa20317731aff0a920705731a185840171a6c765417190318173f14175b5414160315143f11145b54101303121144" +
            "0f10023f0a0f5b540a0e030d0a3f040a5b54060803070407040200440406009383000000052e0609193608060ba32e060907" +
            "3607060a17020605000300c4730000dda008002e0a091a370a0a0e0b7cb8130b0a030a740b910e0100400a0b72d58e0c030b" +
            "400b0372d5080a0b3f060ae02e0a0919360b0a0ba32e090907370a0910c8570309730d1a8340090dc7903f090c5b54090b03" +
            "0a093f06095b540508030706440405013f00045b5400020301005c00"
            to
            "320a6c000136170001e3360c00025b37150003cc663716000425ae370b000592da7603be110b032900002e00001a370b0006" +
            "847f2a0a000b2909002e00090236010007f02e00091a37000006847f10000b0053020103002e00090036010008716e000254" +
            "01010302007a00490501002a0a01056e0001490001002a0a02002e00091936020009a42e0009073601000a17020003000200" +
            "c15a0000614401007304b8594000049bf77304dd624000045100080617938d010000062e0409193608040ba3080701020406" +
            "000500b8730000ef530100760d920705730d55d93b040d015100640a0ad6a53b040a02bc002e0a0919360e0a09a42e0a090b" +
            "370d0a0cb76a030a2e0f091936110f0ba32e0f090b37100f0dfe68030f73120d5d3f0f12f42e1209193614120ba308130102" +
            "1204000300a6730000aea008003f12155b5412140313123f0f125b54101103100f070f0300440f10002e1009193612100ba3" +
            "2e10090b3711100dfe680310731383003f1013f42e1309193615130ba32e14092203133f1317a840131625ae541315031413" +
            "3f10135b541012031110440f10012e10091a3710100e0b7c10100b10937c000000102e1109193613110ba32e11090b371211" +
            "0dfe6803117314d3903f1114f42e1409193616140ba30815010214040003000b5b0000d6a008002e1709193619170ba32e17" +
            "090c3718170f3fa20317731aff0a920705731a185840171a6c765417190318173f14175b5414160315143f11145b54101303" +
            "1211440f10023f0a0f5b540a0e030d0a3f040a5b5406080307040704020044040600937d000000052e0609193608060ba308" +
            "0701020605000300c4730000dda008002e0a091a370a0a0e0b7cb8130b0a030a740b910e0100400a0b72d58e0c030b400b03" +
            "72d5080a0b3f060ae02e0a0919360b0a0ba32e090907370a0910c8570309730d1a8340090dc7903f090c5b54090b030a093f" +
            "06095b540508030706440405013f00045b5400020301005c00000000"),
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
        if (options.materialIcons) {
            patchIcons(hb, log)
            patchNative(hb, "tab pill", log)
        }
        if (options.radius != null) patchNative(hb, "menu cards", log)
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

    private fun iconBody(p: Int, data: ByteArray, filled: Int, outline: Int, ids: Map<String, Int>): ByteArray {
        val b = java.io.ByteArrayOutputStream()
        fun w(vararg v: Int) = v.forEach { b.write(it) }
        fun u16(v: Int) = w(v and 0xFF, (v ushr 8) and 0xFF)
        fun u32(v: Int) { u16(v and 0xFFFF); u16(v ushr 16) }
        b.write(data, p, 0x16)
        w(0x03, 0x01, 0x73, 0x05); u16(ids.getValue("box"))
        w(0x3F, 0x01, 0x05, ids.getValue("viewBox"))
        w(0x6C, 0x07, 0x01, 0x08, 0x08, 0x01, 0x59, 0x04, 0x2C, 0x03)
        w(0x2E, 0x00, 0x00, 0x00); b.write(data, p + 0x3C, 6)
        w(0x03, 0x04, 0x37, 0x06, 0x01, 0x00); u16(ids.getValue("tone"))
        w(0x74, 0x00); u32(filled)
        w(0x90, 0x09, 0x06)
        w(0x74, 0x00); u32(outline)
        w(0x3F, 0x04, 0x00, ids.getValue("d"))
        w(0x76, 0x00, 0x54, 0x05, 0x03, 0x00, 0x05, 0x04)
        w(0x3B, 0x01, 0x05, 0x00); u16(ids.getValue("children"))
        w(0x54, 0x00, 0x03, 0x00, 0x02, 0x01, 0x5C, 0x00)
        return b.toByteArray()
    }

    private fun indexOf(data: ByteArray, sig: ByteArray, from: Int): Int {
        var p = from
        outer@ while (p <= data.size - sig.size) {
            for (k in sig.indices) if (data[p + k] != sig[k]) { p++; continue@outer }
            return p
        }
        return -1
    }

    private fun matches(data: ByteArray, at: Int, sig: ByteArray) =
        at + sig.size <= data.size && sig.indices.all { data[at + it] == sig[it] }

    private fun u16At(data: ByteArray, at: Int) = (data[at].toInt() and 0xFF) or ((data[at + 1].toInt() and 0xFF) shl 8)

    private fun iconFunctions(hb: HermesBundle): Map<Int, Int> {
        val data = hb.data
        val fns = HashMap<Int, Int>()
        var p = indexOf(data, ICON_FN_HEAD, 0)
        while (p != -1) {
            if (matches(data, p + 0x42, hexBytes("03047300")) && matches(data, p + 0x48, hexBytes("3f0400107600")) &&
                matches(data, p + 0x4E, ICON_FN_TAIL)) {
                fns[u16At(data, p + 0x46)] = p
            } else if (matches(data, p + 0x42, hexBytes("76000204")) && matches(data, p + 0x52, ICON_FN_TAIL)) {
                val v = hb.objectValuesOffset + (u16At(data, p + 0x4E) or (u16At(data, p + 0x50) shl 16))
                val tag = data[v].toInt() and 0xFF
                if (tag ushr 4 == 5) fns[u16At(data, v + 2 * (tag and 0xF) - 1)] = p
            }
            p = indexOf(data, ICON_FN_HEAD, p + 1)
        }
        return fns
    }

    private fun patchIcons(hb: HermesBundle, log: MutableList<String>): Boolean {
        val ids = HashMap<String, Int>()
        for ((key, name) in listOf("tone" to "tone", "d" to "d", "children" to "children", "viewBox" to "viewBox", "box" to "0 0 20 20")) {
            val l = hb.findExact(name)
            val max = if (key == "d" || key == "viewBox") 0xFF else 0xFFFF
            if (l.size != 1 || l[0] > max) { log += "skip icons: strings not found"; return false }
            ids[key] = l[0]
        }
        val fns = iconFunctions(hb)
        val originals = ICON_PATHS.map { (prefix, _, _) ->
            val l = hb.findStartingWith(prefix)
            if (l.size != 1 || l[0] !in fns) { log += "skip icons: tab icons not found"; return false }
            l[0]
        }
        val donors = ICON_DONORS.map { prefix ->
            hb.findDonor(listOf(prefix), 1) ?: run { log += "skip icons: no donor strings"; return false }
        }
        val spare = hb.findStartingWith(ICON_SPARE_ENTRY)
        if (spare.size != 1) { log += "skip icons: no spare string entry"; return false }
        val extras = ArrayDeque(donors + spare)
        val paths = ICON_PATHS.flatMap { (_, f, o) -> listOf(f, o) }.map { it.toByteArray(Charsets.US_ASCII) }
        val entry = IntArray(paths.size)
        originals.forEachIndexed { n, orig ->
            val fits = hb.isOverflow(orig) || paths[2 * n].size < 0xFF
            entry[2 * n] = if (fits) orig else extras.removeFirstOrNull() ?: run { log += "skip icons: no string entries"; return false }
            entry[2 * n + 1] = extras.removeFirstOrNull() ?: run { log += "skip icons: no string entries"; return false }
        }
        val slots = donors.map { intArrayOf(hb.storageOffsetOf(it), hb.storageOffsetOf(it) + hb.byteLength(it)) }
        for (k in paths.indices.sortedWith(compareBy({ -paths[it].size }, { it }))) {
            val raw = paths[k]
            val slot = slots.firstOrNull { it[1] - it[0] >= raw.size } ?: run { log += "skip icons: donors too small"; return false }
            hb.writeStorage(slot[0], raw)
            hb.repoint(entry[k], slot[0], raw.size)
            slot[0] += raw.size
        }
        val bodies = originals.mapIndexed { n, orig ->
            val p = fns.getValue(orig)
            p to iconBody(p, hb.data, entry[2 * n], entry[2 * n + 1], ids)
        }
        for ((p, body) in bodies) {
            check(body.size == 0x65)
            System.arraycopy(body, 0, hb.data, p, body.size)
        }
        log += "icons: Material Symbols on the tab bar (filled when active)"
        return true
    }

    private fun patchNative(hb: HermesBundle, name: String, log: MutableList<String>): Boolean {
        val (oldHex, newHex) = NATIVE_BODIES.getValue(name)
        val old = hexBytes(oldHex)
        val data = hb.data
        val p = indexOf(data, old, 0)
        if (p == -1 || indexOf(data, old, p + 1) != -1) { log += "skip $name: function not found in this version"; return false }
        val new = hexBytes(newHex)
        System.arraycopy(new, 0, data, p, new.size)
        log += "$name: on"
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
