#!/usr/bin/env python3
"""
steamd: Material 3 theme patch for the Steam Android app (personal use only).

What it does to base.apk:
  1. Native theme: repoints the colour strings of the built-in "SteamMobile"
     theme in the Hermes bundle (assets/index.android.bundle) to new values.
  2. Store and news theme: extends the script the app already injects into its
     WebViews so it also loads theme/m3.css and sets the seed colour (--sd-p).
  3. Fonts (optional, --fonts DIR): replaces Motiva Sans with your own TTFs,
     matched by weight and italic.
No bytecode is rewritten: only string-table entries change, and the SHA-1
footer is recomputed. The output APK is UNSIGNED: sign it together with the
split APKs, using one key.

Requirements:  pip install hermes-dec fonttools
Optional:      pip install materialyoucolor   (for --seed)

Usage:
  python patch_steam.py base.apk out/base.apk [--seed "#6750A4"] [--fonts fonts/]
  python patch_steam.py --check index.android.bundle [--seed ...]   (dry run)
"""
import argparse, bisect, hashlib, io, os, re, sys, zipfile

BUNDLE = "assets/index.android.bundle"
DEFAULT_CSS = "https://steamd.jettax.xyz/theme/m3.css"
DEFAULT_SEED_PRIMARY = "#AAC7FF"

# ---------------------------------------------------------------------------
# Default palette.  Key: original colour in the SteamMobile theme -> new colour.
# Format #RRGGBB.  With --seed the right-hand side is generated instead.
PALETTE = {
    "#1F2127": "#1B1B1F",  # background.secondary (status bar, deepest background)
    "#23252A": "#1F1F23",  # background.tertiary
    "#2A2C34": "#26272B",  # background.primary (main surface, menu rows)
    "#3C3F49": "#3E4759",  # background.neutral (chips, tonal buttons)
    "#1A9FFF": "#8AB4F8",  # background.brand (accent)
    "#1A97FF": "#BEC6DC",  # background.info (Guard account selector: calm tone, dark text)
    "#DFE3E6": "#E3E2E6",  # foreground.standard (text)
    "#8B929A": "#C4C6D0",  # foreground.secondary (secondary text)
    "#3D4450": "#44474F",  # foreground.neutral
    "#30333A": "#2B2C31",  # divider "standard" block
    "#60636A": "#8E9099",  # divider "bright"
    "#2F343E": "#2B2C31",  # main menu: expanded sub-items
}

# Which Material 3 role each original colour takes when --seed is used.
SEED_ROLES = {
    "#1F2127": "surface",
    "#23252A": "surfaceContainerLow",
    "#2A2C34": "surfaceContainer",
    "#3C3F49": "secondaryContainer",
    "#1A9FFF": "primary",
    "#1A97FF": "secondary",
    "#DFE3E6": "onSurface",
    "#8B929A": "onSurfaceVariant",
    "#3D4450": "outlineVariant",
    "#30333A": "surfaceContainerHigh",
    "#60636A": "outline",
    "#2F343E": "surfaceContainerHigh",
}

# The script the app injects into its store/news WebViews (Steam 3.10.x).
# Kept byte-for-byte: if it differs, the store theme step is skipped.
ORIGINAL_WEBVIEW_JS_START = "window.navigator.standalone = true;(function() {"
ORIGINAL_WEBVIEW_JS_MARKERS = ("HistoryChanged", "history.pushState = wrap( history.pushState )")

# Same behaviour (history change events), minified, plus the theme loader.
WEBVIEW_JS = (
    'window.navigator.standalone=true;(function(){'
    'var m=\'{"event_name":"HistoryChanged"}\','
    'p=function(){window.ReactNativeWebView.postMessage(m)},'
    'w=function(f){return function(){var r=f.apply(this,arguments);p();return r}};'
    'history.pushState=w(history.pushState);history.replaceState=w(history.replaceState);'
    'addEventListener("popstate",p);'
    # <html> may not exist yet at document start: retry until it does
    '(function t(){var d=document.documentElement;if(!d)return setTimeout(t,5);'
    'd.style.setProperty("--sd-p","{SEED}");'
    'var l=document.createElement("link");l.rel="stylesheet";l.href="{CSS}";d.appendChild(l)})()'
    '})();'
)

# Base theme literals around border.radius {standard: 4, large: 10} in the object value buffer:
# breakpoint {0, 540, 900}, radius {4, 10}, heading levels {40, 32, 24, 18}. Unique in Steam 3.10.x.
RADIUS_SIGNATURE = bytes.fromhex(
    "73 00000000 1c020000 84030000 72 04000000 0a000000 74 28000000 20000000 18000000 12000000".replace(" ", ""))
RADIUS_OFFSET = 13  # start of the radius literal inside the signature
DEFAULT_RADIUS = (12, 24)

# Developer-only strings whose storage is reused for the new values
# (Hermes packs strings with overlap, so values cannot be changed in place).
COLOR_DONORS = (
    "[expo-av]: Expo AV has been deprecated",
    "This unit of work tag should not have side-effects",
)
SCRIPT_DONORS = (
    "<div><div>Query Details</div>",          # TanStack Query devtools template
    '<svg width=24 height=24 viewBox="0 0 24 24"fill=none xmlns=http://www.w3.org/2000/svg><path class=copier',
)

# Material Symbols Rounded (Apache-2.0) scaled to a 20x20 box, for the tab bar icons
# (IconStore2, IconNews, IconShield3, IconBell2):
# (original path prefix, filled for the active tab, outlined otherwise).
ICON_PATHS = (
    ("M20 10.2216L10.2216 20L0 9.77837V0H9.77837",
     "m17.8 11.9l-6 6q-.2.2-.6.4t-.6.1q-.3 0-.6-.1t-.6-.4l-7.4-7.4q-.2-.2-.4-.5t-.1-.6l0-6q0-.7.5-1.2t1.2-.5l6 0q.3 0 .6.1t.5.4l7.3 7.4q.2.2 .4.6t.1.6q0 .3-.1.6t-.4.6zm-12.4-5.2q.5 0 .9-.4t.4-.9q0-.5-.4-.9t-.9-.4q-.5 0-.9.4t-.4.9q0 .5.4 .9t.9.4z",
     "m17.8 11.9l-6 6q-.2.2-.6.4t-.6.1q-.3 0-.6-.1t-.6-.4l-7.4-7.4q-.2-.2-.4-.5t-.1-.6l0-6q0-.7.5-1.2t1.2-.5l6 0q.3 0 .6.1t.5.4l7.3 7.4q.2.2 .4.6t.1.6q0 .3-.1.6t-.4.6zm-7.1 4.8l6-6l-7.4-7.4l-6 0l0 6l7.4 7.4zm-5.3-10q.5 0 .9-.4t.4-.9q0-.5-.4-.9t-.9-.4q-.5 0-.9.4t-.4.9q0 .5.4 .9t.9.4zm4.6 3.3z"),
    ("M33 3H3V33C3 33.5523 3.44772 34 4 34H32",
     "m3.3 17.5q-.7 0-1.2-.5t-.5-1.2l0-12.8q0-.1.1-.2t.2.1l1 1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1-1q.1-.1.2-.1t.1.2l0 12.8q0 .7-.5 1.2t-1.2.5l-13.3 0zm0-1.7l5.8 0l0-5l-5.8 0l0 5zm7.5 0l5.8 0l0-1.7l-5.8 0l0 1.7zm0-3.3l5.8 0l0-1.7l-5.8 0l0 1.7zm-7.5-3.3l13.3 0l0-2.5l-13.3 0l0 2.5z",
     "m3.3 17.5q-.7 0-1.2-.5t-.5-1.2l0-12.8q0-.1.1-.2t.2.1l1 1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1.1-1.1q.1-.1.3-.1t.3.1l1.1 1.1l1-1q.1-.1.2-.1t.1.2l0 12.8q0 .7-.5 1.2t-1.2.5l-13.3 0zm0-1.7l5.8 0l0-5l-5.8 0l0 5zm7.5 0l5.8 0l0-1.7l-5.8 0l0 1.7zm0-3.3l5.8 0l0-1.7l-5.8 0l0 1.7zm-7.5-3.3l13.3 0l0-2.5l-13.3 0l0 2.5z"),
    ("M2.64516 3.68664C3.93548 3.41014 5.13364",
     "m10 18.2q-.1 0-.3 0t-.2-.1q-2.8-.9-4.5-3.5t-1.7-5.4l0-3.9q0-.5.3-.9t.8-.6l5-1.9q.3-.1.6-.1t.6.1l5 1.9q.5.2 .8.6t.3.9l0 3.9q0 2.9-1.7 5.4t-4.5 3.5q-.1 0-.2.1t-.3 0z",
     "m10 18.2q-.1 0-.3 0t-.2-.1q-2.8-.9-4.5-3.5t-1.7-5.4l0-3.9q0-.5.3-.9t.8-.6l5-1.9q.3-.1.6-.1t.6.1l5 1.9q.5.2 .8.6t.3.9l0 3.9q0 2.9-1.7 5.4t-4.5 3.5q-.1 0-.2.1t-.3 0zm0-1.7q2.2-.7 3.6-2.8t1.4-4.6l0-3.9l-5-1.9l-5 1.9l0 3.9q0 2.5 1.4 4.6t3.6 2.8zm0-6.6z"),
    ("M18 13.75V15H2V13.75L4.28571 10.625V6.25",
     "m4.2 15.8q-.4 0-.6-.2t-.2-.6q0-.4.2-.6t.6-.2l.8 0l0-5.8q0-1.7 1-3.1t2.7-1.8l0-.6q0-.5.4-.9t.9-.4q.5 0 .9.4t.4.9l0 .6q1.7.4 2.7 1.8t1 3.1l0 5.8l.8 0q.4 0 .6.2t.2.6q0 .4-.2.6t-.6.2l-11.7 0zm5.8 2.5q-.7 0-1.2-.5t-.5-1.2l3.3 0q0 .7-.5 1.2t-1.2.5z",
     "m4.2 15.8q-.4 0-.6-.2t-.2-.6q0-.4.2-.6t.6-.2l.8 0l0-5.8q0-1.7 1-3.1t2.7-1.8l0-.6q0-.5.4-.9t.9-.4q.5 0 .9.4t.4.9l0 .6q1.7.4 2.7 1.8t1 3.1l0 5.8l.8 0q.4 0 .6.2t.2.6q0 .4-.2.6t-.6.2l-11.7 0zm5.8-6.2zm0 8.8q-.7 0-1.2-.5t-.5-1.2l3.3 0q0 .7-.5 1.2t-1.2.5zm-3.3-4.2l6.7 0l0-5.8q0-1.4-1-2.4t-2.4-1q-1.4 0-2.4 1t-1 2.4l0 5.8z"),
)
_SVG24 = '<svg width=24 height=24 viewBox="0 0 24 24"fill=none xmlns=http://www.w3.org/2000/svg><path d="'
ICON_DONORS = tuple(_SVG24 + p for p in ("M16 21H16.2", "M2.5 21.4998L8.", "M7.5 12L10.5 15", "M9 9L15 15M15 9"))
ICON_SPARE_ENTRY = _SVG24 + "M9 3H15M3 6H21M"   # devtools icon whose table entry (not storage) is reused
# Common start of the Icon* components: jsx, Icon, {...props}, viewBox.
ICON_FN_HEAD = bytes.fromhex("2900002e01000236030101a32e01000137020102c37203016c070108080159042c037305")
ICON_FN_TAIL = bytes.fromhex("54050600050473045b0046010504015400030002015c00")

# Rewritten native function bodies for Steam 3.10.11 (generated by patch/tools/gen_native.py).
# BuiltinTabBarItem: Material 3 pill behind the active tab icon instead of the line above it.
# LinkSection, HeaderLink, MainMenuScreen: main menu rows as rounded cards without divider lines.
NATIVE_BODIES = {
    "tab pill": (
        "320a6c020137000201f2582a0a000036000202da2909002e01090a3701010347fd7603510101032a0a01012e01090d370101"
        "0491e151010103370e0105bdd42e01090b37010106e7fd51010103370100073a83360000089a490001002e01090b37010109"
        "21fd510101033707010a0dcf370c010ba4553701020c51580d04017301a459bd7001000004012e0409143701020c5158490b"
        "04012a0a020b90240b30013906010d66003605060ef23704020c51587301b82254010506010477015c0137010b0fb3b67704"
        "0e040104760d900704510d010b37040b10e87073011f86b80c040137010b10e8708e103704020c515873021b29160102042a"
        "0a030136000011b30f0800012c0a0408760f920708730f825803106e0011401000689e2e00090e36020012a32e0009083701"
        "0013fe6803002e04090e36060414a42e0409083605041517010408000200fe1f73093f0410e0640a0a74613f040abc400407"
        "0dcf2e070910400407588573076c8c3f0407c29218082e07090e36100712a32e0a091c0307540810030a0707070300440708"
        "00370a0b16cc660308731093593f0810d640080f0cfc40080ebdd453080a0b084407080108080d9222082e0a090e360b0a12"
        "a32e0a091b030940090def6840090ca45554080b030a09440708023f04075b5404060305043f00045b5400020301005c0030"
        "003902000d66003601020ef273000d17530001020077005c00",
        "320a6c020137000201f2582a0a000036000202da2909002e01090a3701010347fd7603510101032a0a01012e01090d370101"
        "0491e151010103370e0105bdd42e01090b37010106e7fd51010103370100073a83360000089a490001002e01090b37010109"
        "21fd510101033707010a0dcf370c010ba4553701020c51580d04017301a459bd7e01000004012e0409143701020c5158490b"
        "04012a0a020b90070b77015c0137010b0fb3b677040e040104760d900704510d010b37040b10e87073011f86b80c04013701"
        "0b10e8708e103704020c515873021b29160102042a0a030136000011b30f0800012c0a0408760f920708730f825803106e00"
        "0d401000689e2e00090e36020012a32e00090837010013fe6803002e04090e36060414a42e04090836050415170104080002"
        "00fe1f73093f0410e0640a0a74613f040abc4004070dcf2e070910400407588573076c8c3f0407c20707030044070800370a"
        "0b16cc660308731093593f0810d640080f0cfc40080ebdd453080a0b08020a05000400ef5a0000f74c0100761092070f7310"
        "55d93b0a100051007310ce6d3b0a10006b9e3f0a085b2e10090e36101000a32e110908361111001754081003110a44070801"
        "08080d9222082e0a090e360b0a12a32e0a091b030940090def6840090ca45554080b030a09440708023f04075b5404060305"
        "043f00045b5400020301005c0077005c000000000000000000"),
    "menu sections": (
        "320a6c000136170001e3360c00025b37150003cc663716000425ae370b000592da7603be110b032900002e00001a370b0006"
        "847f2a0a000b2909002e00090236010007f02e00091a37000006847f10000b0053020103002e00090036010008716e000254"
        "01010302007a00490501002a0a01056e0001490001002a0a02002e00091936020009a42e0009073601000a17030073044555"
        "4000046b9e080617939f010000062e0409193608040ba32e0409073607040a17020406000500b8730000ef530100760d9207"
        "05730d55d93b040d015100640a0ad6a53b040a02bc002e0a0919360e0a09a42e0a090b370d0a0cb76a030a2e0f091936110f"
        "0ba32e0f090b37100f0dfe68030f73120d5d3f0f12f42e1209193614120ba32e1209073613120a17021204000300a6730000"
        "aea008003f12155b5412140313123f0f125b54101103100f070f0300440f10002e1009193612100ba32e10090b3711100dfe"
        "680310731383003f1013f42e1309193615130ba32e14092203133f1317a840131625ae5413150314133f10135b5410120311"
        "10440f10012e10091a3710100e0b7c10100b109382000000102e1109193613110ba32e11090b3712110dfe6803117314d390"
        "3f1114f42e1409193616140ba32e1409073615140a170214040003000b5b0000d6a008002e1709193619170ba32e17090c37"
        "18170f3fa20317731aff0a920705731a185840171a6c765417190318173f14175b5414160315143f11145b54101303121144"
        "0f10023f0a0f5b540a0e030d0a3f040a5b54060803070407040200440406009383000000052e0609193608060ba32e060907"
        "3607060a17020605000300c4730000dda008002e0a091a370a0a0e0b7cb8130b0a030a740b910e0100400a0b72d58e0c030b"
        "400b0372d5080a0b3f060ae02e0a0919360b0a0ba32e090907370a0910c8570309730d1a8340090dc7903f090c5b54090b03"
        "0a093f06095b540508030706440405013f00045b5400020301005c00",
        "320a6c000136170001e3360c00025b37150003cc663716000425ae370b000592da7603be110b032900002e00001a370b0006"
        "847f2a0a000b2909002e00090236010007f02e00091a37000006847f10000b0053020103007a00490502002a0a01056e0001"
        "490002002a0a02002e00091936020009a42e0009073601000a17020003000200c15a0000614401007304b8594000049bf773"
        "048e7a400004c79603047406910e010040040672d53f0004e0080617938d010000062e0409193608040ba308070102040600"
        "0500b8730000ef530100760d920705730d55d93b040d015100640a0ad6a53b040a02bc002e0a0919360e0a09a42e0a090b37"
        "0d0a0cb76a030a2e0f091936110f0ba32e0f090b37100f0dfe68030f73120d5d3f0f12f42e1209193614120ba30813010212"
        "04000300a6730000aea008003f12155b5412140313123f0f125b54101103100f070f0300440f10002e1009193612100ba32e"
        "10090b3711100dfe680310731383003f1013f42e1309193615130ba32e14092203133f1317a840131625ae5413150314133f"
        "10135b541012031110440f10012e10091a3710100e0b7c10100b10937c000000102e1109193613110ba32e11090b3712110d"
        "fe6803117314d3903f1114f42e1409193616140ba30815010214040003000b5b0000d6a008002e1709193619170ba32e1709"
        "0c3718170f3fa20317731aff0a920705731a185840171a6c765417190318173f14175b5414160315143f11145b5410130312"
        "11440f10023f0a0f5b540a0e030d0a3f040a5b5406080307040704020044040600937d000000052e0609193608060ba30807"
        "01020605000300c4730000dda008002e0a091a370a0a0e0b7cb8130b0a030a740b910e0100400a0b72d58e0c030b400b0372"
        "d5080a0b3f060ae02e0a0919360b0a0ba32e090907370a0910c8570309730d1a8340090dc7903f090c5b54090b030a093f06"
        "095b540508030706440405013f00045b5400020301005c0000000000"),
    "menu rows": (
        "6c16012913002e00130737000001e7fd7603510000033600000260360000035137000004dd622e00131936020005a32e0013"
        "073601000617020004000200e8620000f053010036041607bc3f0004bc2e04131936060408a42e04130b37050409b76a0304"
        "2e07131936090705a32e07130b3708070afe680307730a0d5d3f070af42e0a1319360c0a05a32e0a1307360b0a0617020a04"
        "000300a6730000aea00800370d160bcc663f0a0d5b540a0c030b0a3f070a5b54080903080707070300440708002e08131936"
        "0a0805a32e08130b3709080afe680308730b83003f080bf4360b160ca892210b2e0c1319360e0c05a32e0d1322030c360f16"
        "0ca83f0c0fa8540b0e030d0c3f080b5b54080a030908440708013708160d79d50d090873084b590f080908920f08370a160d"
        "79d57a0914080a0993f0000000082e091319360b0905a32e09130b370a090afe680309730cd3903f090cf42e0c1319360e0c"
        "05a32e0c1307360d0c0617030c730f34db400c0faa6f2e0f131936110f08a42e0f130736100f0617020f05000400ae5b0000"
        "ed8b01002e12131936151205a32e1213083614120e3e031273175a7840121789763716160d79d53f12165b54141503141207"
        "120200441214002e14131936151405a32e1313033714130fd57c021304000300ad730000b5a00800021603000300c00b0000"
        "c3a008003f1316e0541315031413441213013f0f125b540f1103100f3f0c0f5b540c0e030d0c3f090c5b54080b030a094407"
        "08023f04075b5404060305043f00045b5400020301005c00",
        "6c160129130076032e00131936020005a32e0013073601000617020004000200e8620000f053010036041607bc3f0004bc73"
        "04cf57400004f3d57304b8594000049bf773048e7a400004c79603047405910e010040040572d53f0004e02e041319360604"
        "08a42e04130b37050409b76a03042e07131936090705a32e07130b3708070afe680307730a0d5d3f070af42e0a1319360c0a"
        "05a3080b01020a04000300a6730000aea00800370d160bcc663f0a0d5b540a0c030b0a3f070a5b5408090308070707030044"
        "0708002e081319360a0805a32e08130b3709080afe680308730b83003f080bf4360b160ca892210b2e0c1319360e0c05a32e"
        "0d1322030c360f160ca83f0c0fa8540b0e030d0c3f080b5b54080a030908440708013708160d79d50d090873084b590f0809"
        "08920f08370a160d79d57a0914080a0993e4000000082e091319360b0905a32e09130b370a090afe680309730cd3903f090c"
        "f42e0c1319360e0c05a3080d01030c730f34db400c0faa6f2e0f131936110f08a4081001020f05000400ae5b0000ed8b0100"
        "2e12131936151205a32e1213083614120e3e031273175a7840121789763716160d79d53f12165b5414150314120712020044"
        "1214002e14131936151405a32e1313033714130fd57c021304000300ad730000b5a00800021603000300c00b0000c3a00800"
        "3f1316e0541315031413441213013f0f125b540f1103100f3f0c0f5b540c0e030d0c3f090c5b54080b030a09440708023f04"
        "075b5404060305043f00045b5400020301005c0000000000"),
    "menu dividers": (
        "32186c000137040001f2582a180004290f002e000f0a37000002317c7603510000032a1801002e000f0a370000035eba5100"
        "00032a1802002e000f163700000435dd510000032a1803002e000f1c511600032e000f1437000005b2fd5100000337110006"
        "5af62e000f1437000007aea4510e00032e000f1537000008647c510100032e000f0036000009716e08015401000301087a00"
        "490001003610000a332e000f193602000ba32e000f093701000c7aa303002e050f053606050d277405133201005305060305"
        "3f0005e3400004f2582e040f193606040ea42e040f063705040f6c670304030740070878563f0407e02e070f193609070ea4"
        "2e070f07360807101703072e0a0f19360c0a0ba32e0b0f1e030a540b0c030b0a070a1200440a0b002e0b0f19360d0b0ea42e"
        "0c0f23030b2e120f053613120d2774120a32010053121303123f0b12e33712161173e0921b123614160a3377130e15141376"
        "1390081536131412ad0812139245122e130f193615130ea42e130f08361413133e03137317d6604013170cfc3617160a3306"
        "1603000100041301004416170173175301441617023f13165b541215031413400b1225ae2e120f193614120ba32e120f0c37"
        "131214cbb60312541214031312400b12cc662e120f193613120ba32e140f1f03122e150f053616150d2774150b3201005315"
        "1603153f1215a8641518b5a53f1215bc54131303141207120700441213002e130f193615130ba303132e160f053617160d27"
        "74161132010053161703163f1316a8641618b6a53f1316bc541315031413441213012e130f193615130ba303132e160f0536"
        "17160d2774160c32010053161703163f1316a8641618b7a53f1316bc541315031413441213022e130f193615130ba303132e"
        "160f053617160d2774162b18010053161703163f1316a8641618b8a53f1316bc541315031413441213032e130f193615130b"
        "a303132e160f053617160d2774160d32010053161703163f1316a8641618b9a53f1316bc541315031413441213042e130f19"
        "3615130ba303132e160f053617160d277316480653161703163f1316a8641618baa53f1316bc541315031413441213052e13"
        "0f193615130ba303132e160f053617160d2774161432010053161703163f1316a8641618bba53f1316bc5413150314134412"
        "13063f0b125b540b0d030c0b440a0b012e0b0f19360d0b0ba32e0b0f07370c0b1572a8030b540b0d030c0b440a0b022e0b0f"
        "19360c0b0ba32e0d0f21030b2e120f053613120d2774120532010053121303123f0b12a8641218bca53f0b12bc2e120f1936"
        "15120ba32e120f0c37131216d76c0312541215031312400b12cc66540b0c030d0b440a0b032e0b0f1936120b0ba32e0b0f07"
        "370c0b1572a8030b540b12030c0b440a0b042e0b0f19360c0b0ba3030b2e120f053613120d277412852f010053121303123f"
        "0b12a8641218bda53f0b12bc2e120f193615120ba32e120f0c3713121777b50312541215031312400b12cc66540b0c030d0b"
        "440a0b052e0b0f1936120b0ba32e0b0f07370c0b1572a8030b540b12030c0b440a0b062e0b0f19360c0b0ba3030b2e120f05"
        "3613120d2774120732010053121303123f0b12a8641218bea53f0b12bc2e120f193615120ba32e120f0c371312186ab60312"
        "541215031312400b12cc66400b1179d5540b0c030d0b440a0b072e0b0f1936110b0ba32e0b0f07370c0b1572a8030b540b11"
        "030c0b440a0b082e0b0f19360c0b0ba3030b2e110f053612110d2774113d27010053111203113f0b11a8641118bfa53f0b11"
        "bc2e110f193613110ba32e110f0c3712111980970311541113031211400b11cc66400b0e79d5540b0c030d0b440a0b092e0b"
        "0f19360e0b0ba32e0b0f07370c0b1572a8030b540b0e030c0b440a0b0a2e0b0f19360c0b0ba3030b2e0e0f0536110e0d2774"
        "0e03320100530e11030e3f0b0ea8640e18c0a53f0b0ebc2e0e0f1936120e0ba32e0e0f0c37110e1acd97030e540e1203110e"
        "400b0ecc66540b0c030d0b440a0b0b2e0b0f19360e0b0ba32e0b0f07370c0b1572a8030b540b0e030c0b440a0b0c2e0b0f19"
        "360e0b0ea42e0c0f23030b2e110f053612110d277411fd31010053111203113f0b11e32e110f193613110ba32e110f0c3712"
        "111b6c540311541113031211400b11cc662e110f193612110ba303112e130f053615130d277313460653131503133f1113a8"
        "641318c1a53f1113bc54121203141107110500441112002e120f193613120ba303122e150f053616150d277415fc31010053"
        "151603153f1215a8641518c2a53f1215bc541213031412441112012e120f193613120ba303122e150f053616150d277415fe"
        "31010053151603153f1215a8641518c3a53f1215bc541213031412441112022e120f193613120ba303122e150f053616150d"
        "277415fb31010053151603153f1215a8641518c4a53f1215bc541213031412441112032e120f193613120ba303122e150f05"
        "3616150d2774150032010053151603153f1215a8641518c5a53f1215bc541213031412441112043f0b115b540b0e030c0b44"
        "0a0b0d2e0b0f19360e0b0ba32e0b0f07370c0b1572a8030b540b0e030c0b440a0b0e2e0b0f19360c0b0ba3030b2e0e0f0536"
        "110e0d27740ef9310100530e11030e3f0b0ea82e0e0f1936120e0ba32e0e0f0c37110e1cf5a1030e6e131a3f0e13d6540e12"
        "03110e400b0ecc66640e18c6a53f0b0ebc540b0c030d0b440a0b0f2e0b0f19360d0b0ba32e0b0f07370c0b1572a8030b540b"
        "0d030c0b440a0b102e0b0f19360d0b0ea42e0b0f07360c0b1017030b730eb859400b0e7d922e0e0f1936110e0ba32e130f20"
        "030e2e120f053614120d2774120432010053121403123f0e12a82e120f193615120ba32e120f0c3714121dc8a103127319f0"
        "f64012190cfc541215031412400e12cc66641218c7a53f0e12bc54111103130e070e0500440e11002e110f193612110ba303"
        "112e140f053615140d2774140832010053141503143f1114a82e140f193616140ba32e140f0c3715141e79ba03144014190c"
        "fc541416031514401114cc66641418c8a53f1114bc541112031311440e11012e110f193612110ba303112e140f053615140d"
        "2774141232010053141503143f1114a82e140f193616140ba32e140f0c3715141fddbb03144014190cfc5414160315144011"
        "14cc66641418c9a53f1114bc541112031311440e11022e110f193612110ba303112e140f053615140d277414093201005314"
        "1503143f1114a82e140f193616140ba32e140f0c3715142040b603144014190cfc541416031514401114cc66641418caa53f"
        "1114bc541112031311440e11039250102e110f193613110ba32e120f2003117414533101003f1114a82e140f193616140ba3"
        "2e140f0c3715141fddbb03144014190cfc541416031514401114cc66641418cba53f1114bc541013031211440e10043f0b0e"
        "5b540b0d030c0b440a0b113f070a5b54080903080707070200440708002e080f19360a080ea42e080f073609081017020805"
        "000400947300009e160800730e93592e0b0f19360d0b0ba32e0b0f07360c0b1017030b400b0ea1802e0e0f1936110e0ba32e"
        "0e0f0c37100e21e0bb030e540e1103100e3f0b0e5b540c0d030c0b070b0200440b0c002e0c0f19360e0c0ba32e0c0f08360d"
        "0c133e030c400c190cfc2e100f0537151022288f2e100f053612100d273010391010231a0036111024386a11111008211150"
        "1010016b1111103710112560dc511110117410063201005414120310112e100f193612100ba32e100f08371110264a9b0310"
        "4010190cfc641318cca53f1013bc5413120311102e100f193612100ba32e100f08371110264a9b03104010190cfc641618cd"
        "a53f1016bc541e120311102e100f193616100ba32e100f08371110264a9b03104010190cfc641718cea53f1017bc541d1603"
        "11102e100f193617100ba32e100f08371610264a9b03104010190cfc641a18cfa53f101abc541c170316102e160f19361716"
        "0ba32e0f0f0837160f264a9b030f400f190cfc641818d0a53f0f18bc541b1703160f7621082014081f134f0f15073f0c0f5b"
        "540c0e030d0c440b0c013f080b5b54080a030908440708013f04075b5404060305043f00045b5400020301005c00",
        "32186c000137040001f2582a180004290f002e000f0a37000002317c7603510000032a1801002e000f0a370000035eba5100"
        "00032a1802002e000f163700000435dd510000032a1803002e000f1c511600032e000f1437000005b2fd5100000337110006"
        "5af62e000f1437000007aea4510e00032e000f1537000008647c510100032e000f0036000009716e08015401000301087a00"
        "490001003610000a332e000f193602000ba32e000f093701000c7aa303002e050f053606050d277405133201005305060305"
        "3f0005e3400004f2582e040f193606040ea42e040f063705040f6c670304030740070878563f0407e02e070f193609070ea4"
        "2e070f07360807101703072e0a0f19360c0a0ba32e0b0f1e030a540b0c030b0a070a1200440a0b002e0b0f19360d0b0ea42e"
        "0c0f23030b2e120f053613120d2774120a32010053121303123f0b12e33712161173e0921b123614160a3377130e15141376"
        "1390081536131412ad0812139245122e130f193615130ea42e130f08361413133e03137317d6604013170cfc3617160a3306"
        "1603000100041301004416170173175301441617023f13165b541215031413400b1225ae2e120f193614120ba32e120f0c37"
        "131214cbb60312541214031312400b12cc662e120f193613120ba32e140f1f03122e150f053616150d2774150b3201005315"
        "1603153f1215a8641518b5a53f1215bc54131303141207120700441213002e130f193615130ba303132e160f053617160d27"
        "74161132010053161703163f1316a8641618b6a53f1316bc541315031413441213012e130f193615130ba303132e160f0536"
        "17160d2774160c32010053161703163f1316a8641618b7a53f1316bc541315031413441213022e130f193615130ba303132e"
        "160f053617160d2774162b18010053161703163f1316a8641618b8a53f1316bc541315031413441213032e130f193615130b"
        "a303132e160f053617160d2774160d32010053161703163f1316a8641618b9a53f1316bc541315031413441213042e130f19"
        "3615130ba303132e160f053617160d277316480653161703163f1316a8641618baa53f1316bc541315031413441213052e13"
        "0f193615130ba303132e160f053617160d2774161432010053161703163f1316a8641618bba53f1316bc5413150314134412"
        "13063f0b125b540b0d030c0b440a0b012e0b0f19360d0b0ba32e0b0f07370c0b001700030b540b0d030c0b440a0b022e0b0f"
        "19360c0b0ba32e0d0f21030b2e120f053613120d2774120532010053121303123f0b12a8641218bca53f0b12bc2e120f1936"
        "15120ba32e120f0c37131216d76c0312541215031312400b12cc66540b0c030d0b440a0b032e0b0f1936120b0ba32e0b0f07"
        "370c0b001700030b540b12030c0b440a0b042e0b0f19360c0b0ba3030b2e120f053613120d277412852f010053121303123f"
        "0b12a8641218bda53f0b12bc2e120f193615120ba32e120f0c3713121777b50312541215031312400b12cc66540b0c030d0b"
        "440a0b052e0b0f1936120b0ba32e0b0f07370c0b001700030b540b12030c0b440a0b062e0b0f19360c0b0ba3030b2e120f05"
        "3613120d2774120732010053121303123f0b12a8641218bea53f0b12bc2e120f193615120ba32e120f0c371312186ab60312"
        "541215031312400b12cc66400b1179d5540b0c030d0b440a0b072e0b0f1936110b0ba32e0b0f07370c0b001700030b540b11"
        "030c0b440a0b082e0b0f19360c0b0ba3030b2e110f053612110d2774113d27010053111203113f0b11a8641118bfa53f0b11"
        "bc2e110f193613110ba32e110f0c3712111980970311541113031211400b11cc66400b0e79d5540b0c030d0b440a0b092e0b"
        "0f19360e0b0ba32e0b0f07370c0b001700030b540b0e030c0b440a0b0a2e0b0f19360c0b0ba3030b2e0e0f0536110e0d2774"
        "0e03320100530e11030e3f0b0ea8640e18c0a53f0b0ebc2e0e0f1936120e0ba32e0e0f0c37110e1acd97030e540e1203110e"
        "400b0ecc66540b0c030d0b440a0b0b2e0b0f19360e0b0ba32e0b0f07370c0b001700030b540b0e030c0b440a0b0c2e0b0f19"
        "360e0b0ea42e0c0f23030b2e110f053612110d277411fd31010053111203113f0b11e32e110f193613110ba32e110f0c3712"
        "111b6c540311541113031211400b11cc662e110f193612110ba303112e130f053615130d277313460653131503133f1113a8"
        "641318c1a53f1113bc54121203141107110500441112002e120f193613120ba303122e150f053616150d277415fc31010053"
        "151603153f1215a8641518c2a53f1215bc541213031412441112012e120f193613120ba303122e150f053616150d277415fe"
        "31010053151603153f1215a8641518c3a53f1215bc541213031412441112022e120f193613120ba303122e150f053616150d"
        "277415fb31010053151603153f1215a8641518c4a53f1215bc541213031412441112032e120f193613120ba303122e150f05"
        "3616150d2774150032010053151603153f1215a8641518c5a53f1215bc541213031412441112043f0b115b540b0e030c0b44"
        "0a0b0d2e0b0f19360e0b0ba32e0b0f07370c0b001700030b540b0e030c0b440a0b0e2e0b0f19360c0b0ba3030b2e0e0f0536"
        "110e0d27740ef9310100530e11030e3f0b0ea82e0e0f1936120e0ba32e0e0f0c37110e1cf5a1030e6e131a3f0e13d6540e12"
        "03110e400b0ecc66640e18c6a53f0b0ebc540b0c030d0b440a0b0f2e0b0f19360d0b0ba32e0b0f07370c0b001700030b540b"
        "0d030c0b440a0b102e0b0f19360d0b0ea42e0b0f07360c0b1017030b730eb859400b0e7d922e0e0f1936110e0ba32e130f20"
        "030e2e120f053614120d2774120432010053121403123f0e12a82e120f193615120ba32e120f0c3714121dc8a103127319f0"
        "f64012190cfc541215031412400e12cc66641218c7a53f0e12bc54111103130e070e0500440e11002e110f193612110ba303"
        "112e140f053615140d2774140832010053141503143f1114a82e140f193616140ba32e140f0c3715141e79ba03144014190c"
        "fc541416031514401114cc66641418c8a53f1114bc541112031311440e11012e110f193612110ba303112e140f053615140d"
        "2774141232010053141503143f1114a82e140f193616140ba32e140f0c3715141fddbb03144014190cfc5414160315144011"
        "14cc66641418c9a53f1114bc541112031311440e11022e110f193612110ba303112e140f053615140d277414093201005314"
        "1503143f1114a82e140f193616140ba32e140f0c3715142040b603144014190cfc541416031514401114cc66641418caa53f"
        "1114bc541112031311440e11039250102e110f193613110ba32e120f2003117414533101003f1114a82e140f193616140ba3"
        "2e140f0c3715141fddbb03144014190cfc541416031514401114cc66641418cba53f1114bc541013031211440e10043f0b0e"
        "5b540b0d030c0b440a0b113f070a5b54080903080707070200440708002e080f19360a080ea42e080f073609081017020805"
        "000400947300009e160800730e93592e0b0f19360d0b0ba32e0b0f07360c0b1017030b400b0ea1802e0e0f1936110e0ba32e"
        "0e0f0c37100e21e0bb030e540e1103100e3f0b0e5b540c0d030c0b070b0200440b0c002e0c0f19360e0c0ba32e0c0f08360d"
        "0c133e030c400c190cfc2e100f0537151022288f2e100f053612100d273010391010231a0036111024386a11111008211150"
        "1010016b1111103710112560dc511110117410063201005414120310112e100f193612100ba32e100f08371110264a9b0310"
        "4010190cfc641318cca53f1013bc5413120311102e100f193612100ba32e100f08371110264a9b03104010190cfc641618cd"
        "a53f1016bc541e120311102e100f193616100ba32e100f08371110264a9b03104010190cfc641718cea53f1017bc541d1603"
        "11102e100f193617100ba32e100f08371610264a9b03104010190cfc641a18cfa53f101abc541c170316102e160f19361716"
        "0ba32e0f0f0837160f264a9b030f400f190cfc641818d0a53f0f18bc541b1703160f7621082014081f134f0f15073f0c0f5b"
        "540c0e030d0c440b0c013f080b5b54080a030908440708013f04075b5404060305043f00045b5400020301005c00"),
}


# ---------------------------------------------------------------------------
class _Reader:
    """hermes-dec reader that also remembers where the string tables and storage start."""
    def __init__(self, data):
        from hermes_dec.parsers.hbc_file_parser import HBCReader
        outer = self

        class R(HBCReader):
            def read_small_string_table(self):
                self.align_over_padding()
                outer.table_start = self.file_buffer.tell()
                self.file_buffer.seek(outer.table_start)
                return super().read_small_string_table()

            def read_overflow_string_table(self):
                self.align_over_padding()
                outer.overflow_start = self.file_buffer.tell()
                self.file_buffer.seek(outer.overflow_start)
                return super().read_overflow_string_table()

            def read_string_storage(self):
                self.align_over_padding()
                outer.storage_start = self.file_buffer.tell()
                self.file_buffer.seek(outer.storage_start)
                return super().read_string_storage()

        self.r = R()
        self.r.read_whole_file(io.BytesIO(data))
        self.strings = self.r.strings
        self.entries = []
        for i in range(self.r.header.stringCount):
            e = self.r.small_string_table[i]
            utf16, overflow = e.isUTF16, e.length == 0xFF
            if overflow:
                e = self.r.overflow_string_table[e.offset]
            self.entries.append((i, utf16, e.offset, e.length * (2 if utf16 else 1), overflow))
        by_off = sorted(self.entries, key=lambda x: x[2])
        self._by_off, self._offs = by_off, [x[2] for x in by_off]

    def shared(self, i, a, b):
        """Other strings whose storage intersects [a, b)."""
        k = bisect.bisect_left(self._offs, a - 70000)
        return [j for j, _, o, m, _ in self._by_off[k:bisect.bisect_left(self._offs, b)]
                if j != i and o < b and o + m > a]

    def find_donor(self, prefixes, need):
        for pref in prefixes:
            for i, u, o, m, _ in self.entries:
                if not u and m >= need and self.strings[i].startswith(pref) and not self.shared(i, o, o + m):
                    return i, o, m
        return None


class _Patch:
    def __init__(self, data):
        self.rd = _Reader(data)
        self.out = bytearray(data)

    def write_storage(self, off, raw):
        p = self.rd.storage_start + off
        self.out[p:p + len(raw)] = raw

    def repoint(self, i, off, length):
        """Point string #i at storage [off, off+length)."""
        rd = self.rd
        t = rd.table_start + 4 * i
        v = int.from_bytes(self.out[t:t + 4], "little")
        if (v >> 24) != 0xFF:                       # small entry: bit0 utf16, bits1-23 offset, bits24-31 length
            if length >= 0xFF or off >= 1 << 23:
                sys.exit(f"string #{i}: new value does not fit a small entry")
            v = (v & 0x00000001) | (off << 1) | (length << 24)
            self.out[t:t + 4] = v.to_bytes(4, "little")
        else:                                       # overflow entry: (offset u32, length u32)
            k = (v >> 1) & 0x7FFFFF
            q = rd.overflow_start + 8 * k
            self.out[q:q + 8] = off.to_bytes(4, "little") + length.to_bytes(4, "little")

    def finish(self):
        self.out[-20:] = hashlib.sha1(bytes(self.out[:-20])).digest()
        return bytes(self.out)


# ---------------------------------------------------------------------------
def palette_from_seed(seed):
    """Material You dark scheme (tonal spot) from a seed colour."""
    try:
        from materialyoucolor.hct import Hct
        from materialyoucolor.scheme.scheme_tonal_spot import SchemeTonalSpot
        from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors as MDC
    except ImportError:
        sys.exit("--seed needs: pip install materialyoucolor")
    hct = Hct.from_int(0xFF000000 | int(seed.lstrip("#"), 16))
    try:  # 2021 spec = classic Material You colours, same as the site preview
        scheme = SchemeTonalSpot(hct, True, 0.0, spec_version="2021")
    except TypeError:
        scheme = SchemeTonalSpot(hct, True, 0.0)

    def role(name):
        dc = getattr(MDC, name)
        argb = dc.get_hct(scheme).to_int() if hasattr(dc, "get_hct") else dc.get_argb(scheme)
        return "#%06X" % (argb & 0xFFFFFF)

    pal = {old: role(r) for old, r in SEED_ROLES.items()}
    return pal, role("primary")


def patch_colors(pt, palette, verbose=True):
    S, E = pt.rd.strings, pt.rd.entries
    jobs = []
    for old, new in palette.items():
        if not (re.fullmatch(r"#[0-9A-Fa-f]{6}", old) and re.fullmatch(r"#[0-9A-Fa-f]{6}", new)):
            sys.exit(f"Bad colour pair {old} -> {new}: use #RRGGBB")
        idx = [i for i, s in enumerate(S) if s == old]
        if not idx:
            print(f"  [skip] {old}: not in this version")
        for i in idx:
            if E[i][1] or E[i][4]:
                print(f"  [skip] {old}: unexpected string kind"); continue
            jobs.append((i, old, new.upper()))
    if not jobs:
        return 0
    news = sorted({n for _, _, n in jobs})
    donor = pt.rd.find_donor(COLOR_DONORS, 7 * len(news))
    if not donor:
        sys.exit("No donor string for colours: this app version is not supported yet")
    _, doff, _ = donor
    pos = {}
    for k, n in enumerate(news):
        pt.write_storage(doff + 7 * k, n.encode())
        pos[n] = doff + 7 * k
    pt.color_offsets = pos
    for i, old, new in jobs:
        pt.repoint(i, pos[new], 7)
        if verbose:
            print(f"  {old} -> {new}")
    return len(jobs)


def patch_webview(pt, seed_primary, css_url):
    S = pt.rd.strings
    idx = [i for i, s in enumerate(S)
           if s.startswith(ORIGINAL_WEBVIEW_JS_START) and all(m in s for m in ORIGINAL_WEBVIEW_JS_MARKERS)]
    if len(idx) != 1:
        print("  [skip] store theme: injected WebView script not found in this version")
        return False
    if not re.fullmatch(r"https://[\w.\-/~%]+\.css", css_url):
        sys.exit(f"Bad --css-url: {css_url}")
    js = WEBVIEW_JS.replace("{SEED}", seed_primary).replace("{CSS}", css_url).encode("ascii")
    donor = pt.rd.find_donor(SCRIPT_DONORS, len(js))
    if not donor:
        sys.exit(f"No donor string for the {len(js)}-byte loader: use a shorter --css-url")
    _, doff, _ = donor
    pt.write_storage(doff, js)
    pt.repoint(idx[0], doff, len(js))
    print(f"  store theme: {css_url}  (seed {seed_primary}, {len(js)} bytes)")
    return True


def patch_radius(pt, radius):
    """Rounder corners for the native UI: rewrites the base theme's border.radius integers in place."""
    h = pt.rd.r.header
    align = lambda x: (x + 3) & ~3
    values = align(align(align(pt.rd.storage_start + h.stringStorageSize) + h.arrayBufferSize) + h.objKeyBufferSize)
    buf = bytes(pt.out[values:values + h.objValueBufferSize])
    if buf.count(RADIUS_SIGNATURE) != 1:
        print("  [skip] corner radius: base theme not found in this version")
        return False
    p = values + buf.find(RADIUS_SIGNATURE) + RADIUS_OFFSET
    standard, large = radius
    pt.out[p + 1:p + 5] = int(standard).to_bytes(4, "little", signed=True)
    pt.out[p + 5:p + 9] = int(large).to_bytes(4, "little", signed=True)
    print(f"  corner radius: standard 4 -> {standard}, large 10 -> {large}")
    return True


def patch_tab_marker(pt, palette):
    """Tab bar marker gradient (#66C0F4 -> #2D73FF) in the theme accent: repoint its array to one string."""
    accent = palette.get("#1A9FFF", "").upper()
    offs = getattr(pt, "color_offsets", {})
    S = pt.rd.strings
    a = [i for i, s in enumerate(S) if s == "#66C0F4"]
    b = [i for i, s in enumerate(S) if s == "#2D73FF"]
    holder = [i for i, s in enumerate(S) if s == "Custom tabs are NYI"]
    if accent not in offs or len(a) != 1 or len(b) != 1 or len(holder) != 1 or max(a[0], b[0], holder[0]) > 0xFFFF:
        print("  [skip] tab marker: not found in this version")
        return False
    h = pt.rd.r.header
    align = lambda x: (x + 3) & ~3
    arrays = align(pt.rd.storage_start + h.stringStorageSize)
    sig = bytes([0x52]) + a[0].to_bytes(2, "little") + b[0].to_bytes(2, "little")
    buf = bytes(pt.out[arrays:arrays + h.arrayBufferSize])
    if buf.count(sig) != 1:
        print("  [skip] tab marker: colour array not found")
        return False
    p = arrays + buf.find(sig)
    pt.out[p + 1:p + 5] = holder[0].to_bytes(2, "little") * 2
    pt.repoint(holder[0], offs[accent], 7)
    print(f"  tab marker: {accent}")
    return True


def patch_guard_code(pt):
    """Lighter Steam Guard code (CircleCodeScreen heading weight medium -> light)."""
    S = pt.rd.strings
    ids = {}
    for name in ("1", "medium", "light", "center"):
        l = [i for i, s in enumerate(S) if s == name]
        if len(l) != 1 or l[0] > 0xFFFF:
            print("  [skip] guard code: strings not found"); return False
        ids[name] = l[0]
    h = pt.rd.r.header
    align = lambda x: (x + 3) & ~3
    values = align(align(align(pt.rd.storage_start + h.stringStorageSize) + h.arrayBufferSize) + h.objKeyBufferSize)
    sig = (bytes([0x52]) + ids["1"].to_bytes(2, "little") + ids["medium"].to_bytes(2, "little")
           + bytes([0x01, 0x51]) + ids["center"].to_bytes(2, "little"))
    buf = bytes(pt.out[values:values + h.objValueBufferSize])
    if buf.count(sig) != 1:
        print("  [skip] guard code: style not found"); return False
    p = values + buf.find(sig) + 3
    pt.out[p:p + 2] = ids["light"].to_bytes(2, "little")
    print("  guard code: light")
    return True


def _icon_body(p, data, filled, outline, ids):
    """props -> Icon({viewBox: '0 0 20 20', ...props, children: Path({d: props.tone ? filled : outline})})."""
    u16 = lambda v: v.to_bytes(2, "little")
    u32 = lambda v: v.to_bytes(4, "little")
    b = bytearray(data[p:p + 0x16])                                                  # r3 = jsx, r2 = Icon
    b += bytes([0x03, 0x01, 0x73, 0x05]) + u16(ids["box"])                           # r1 = {}, r5 = '0 0 20 20'
    b += bytes([0x3F, 0x01, 0x05, ids["viewBox"]])                                   # r1.viewBox = r5
    b += bytes([0x6C, 0x07, 0x01, 0x08, 0x08, 0x01, 0x59, 0x04, 0x2C, 0x03])         # copyDataProperties(r1, props)
    b += bytes([0x2E, 0x00, 0x00, 0x00]) + data[p + 0x3C:p + 0x42]                   # r5 = Path
    b += bytes([0x03, 0x04, 0x37, 0x06, 0x01, 0x00]) + u16(ids["tone"])              # r4 = {}, r6 = r1.tone
    b += bytes([0x74, 0x00]) + u32(filled)                                           # r0 = filled
    b += bytes([0x90, 0x09, 0x06])                                                   # if (r6) skip
    b += bytes([0x74, 0x00]) + u32(outline)                                          # r0 = outline
    b += bytes([0x3F, 0x04, 0x00, ids["d"]])                                         # r4.d = r0
    b += bytes([0x76, 0x00, 0x54, 0x05, 0x03, 0x00, 0x05, 0x04])                     # r5 = jsx(Path, r4)
    b += bytes([0x3B, 0x01, 0x05, 0x00]) + u16(ids["children"])                      # r1.children = r5
    b += bytes([0x54, 0x00, 0x03, 0x00, 0x02, 0x01, 0x5C, 0x00])                     # return jsx(Icon, r1)
    return bytes(b)


def _icon_functions(pt):
    """Icon* components keyed by the string id of their path."""
    h = pt.rd.r.header
    align = lambda x: (x + 3) & ~3
    values = align(align(align(pt.rd.storage_start + h.stringStorageSize) + h.arrayBufferSize) + h.objKeyBufferSize)
    data = bytes(pt.out)
    fns = {}
    p = data.find(ICON_FN_HEAD)
    while p != -1:
        if data[p + 0x42:p + 0x46] == b"\x03\x04\x73\x00" and data[p + 0x48:p + 0x4E] == b"\x3f\x04\x00\x10\x76\x00" \
                and data[p + 0x4E:p + 0x65] == ICON_FN_TAIL:
            fns[int.from_bytes(data[p + 0x46:p + 0x48], "little")] = p
        elif data[p + 0x42:p + 0x46] == b"\x76\x00\x02\x04" and data[p + 0x52:p + 0x69] == ICON_FN_TAIL:
            v = values + int.from_bytes(data[p + 0x4E:p + 0x52], "little")
            if data[v] >> 4 == 5:
                n = data[v] & 0xF
                fns[int.from_bytes(data[v + 2 * n - 1:v + 2 * n + 1], "little")] = p
        p = data.find(ICON_FN_HEAD, p + 1)
    return fns


def patch_icons(pt):
    """Material Symbols on the tab bar: filled on the active tab (tone is set), outlined on the others."""
    S = pt.rd.strings
    ids = {}
    for key, name in (("tone", "tone"), ("d", "d"), ("children", "children"), ("viewBox", "viewBox"), ("box", "0 0 20 20")):
        l = [i for i, s in enumerate(S) if s == name]
        if len(l) != 1 or l[0] > (0xFF if key in ("d", "viewBox") else 0xFFFF):
            print("  [skip] icons: strings not found"); return False
        ids[key] = l[0]
    fns = _icon_functions(pt)
    originals = []
    for pref, _, _ in ICON_PATHS:
        l = [i for i, s in enumerate(S) if s.startswith(pref)]
        if len(l) != 1 or l[0] not in fns:
            print("  [skip] icons: tab icons not found"); return False
        originals.append(l[0])
    donors = []
    for pref in ICON_DONORS:
        d = pt.rd.find_donor((pref,), 1)
        if not d:
            print("  [skip] icons: no donor strings"); return False
        donors.append(d)
    spare = [i for i, s in enumerate(S) if s.startswith(ICON_SPARE_ENTRY)]
    if len(spare) != 1:
        print("  [skip] icons: no spare string entry"); return False
    extras = [d[0] for d in donors] + spare
    paths = [x.encode("ascii") for _, f, o in ICON_PATHS for x in (f, o)]
    entry = {}
    for n, orig in enumerate(originals):
        fits = pt.rd.entries[orig][4] or len(paths[2 * n]) < 0xFF
        entry[2 * n] = orig if fits else extras.pop(0)
        entry[2 * n + 1] = extras.pop(0) if extras else None
    if None in entry.values():
        print("  [skip] icons: no string entries"); return False
    slots = [[o, o + m] for _, o, m in donors]
    for k in sorted(range(len(paths)), key=lambda k: (-len(paths[k]), k)):
        raw = paths[k]
        slot = next((s for s in slots if s[1] - s[0] >= len(raw)), None)
        if slot is None:
            print("  [skip] icons: donors too small"); return False
        pt.write_storage(slot[0], raw)
        pt.repoint(entry[k], slot[0], len(raw))
        slot[0] += len(raw)
    data = bytes(pt.out)
    for n, orig in enumerate(originals):
        p = fns[orig]
        body = _icon_body(p, data, entry[2 * n], entry[2 * n + 1], ids)
        assert len(body) == 0x65
        pt.out[p:p + len(body)] = body
    print("  icons: Material Symbols on the tab bar (filled when active)")
    return True

def patch_native(pt, name):
    """Swap a whole function body when it matches the known original exactly."""
    old, new = (bytes.fromhex(x) for x in NATIVE_BODIES[name])
    data = bytes(pt.out)
    if data.count(old) != 1:
        print(f"  [skip] {name}: function not found in this version"); return False
    p = data.find(old)
    pt.out[p:p + len(new)] = new
    print(f"  {name}: on")
    return True


def patch_uppercase(pt):
    """Sentence case everywhere: the shared Text component reads props.uppercase; make it read props.__esModule."""
    S = pt.rd.strings
    up = [i for i, s in enumerate(S) if s == "uppercase"]
    esm = [i for i, s in enumerate(S) if s == "__esModule"]
    if len(up) != 1 or len(esm) != 1 or up[0] > 0xFFFF or esm[0] > 0xFFFF:
        print("  [skip] uppercase: strings not found in this version")
        return False
    sig = bytes([0x37, 0x00, 0x02, 0x05]) + up[0].to_bytes(2, "little") + bytes([0x79, 0x01])
    data = bytes(pt.out)
    hits = []
    p = data.find(sig)
    while p != -1:
        hits.append(p)
        p = data.find(sig, p + 1)
    if len(hits) != 1:
        print(f"  [skip] uppercase: text component not found ({len(hits)} matches)")
        return False
    pt.out[hits[0] + 4:hits[0] + 6] = esm[0].to_bytes(2, "little")
    print("  uppercase text: off")
    return True


def patch_bundle(data, palette=PALETTE, seed_primary=DEFAULT_SEED_PRIMARY, css_url=DEFAULT_CSS,
                 colors=True, store=True, radius=DEFAULT_RADIUS, uppercase=False, icons=True):
    if data[:8] != bytes.fromhex("c61fbc03c103191f"):
        sys.exit("Not a Hermes bundle")
    ver = int.from_bytes(data[8:12], "little")
    if hashlib.sha1(data[:-20]).digest() != data[-20:]:
        sys.exit("SHA-1 footer mismatch: the bundle is already modified or damaged")
    pt = _Patch(data)
    n = patch_colors(pt, palette) if colors else 0
    s = patch_webview(pt, seed_primary, css_url) if store else False
    rr = patch_radius(pt, radius) if radius else False
    if colors:
        patch_tab_marker(pt, palette)
    if not uppercase:
        patch_uppercase(pt)
    patch_guard_code(pt)
    if icons:
        patch_icons(pt)
        patch_native(pt, "tab pill")
    if radius:
        for name in ("menu sections", "menu rows", "menu dividers"):
            patch_native(pt, name)
    print(f"Hermes v{ver}: colours replaced: {n}; store theme: {'yes' if s else 'no'}; radius: {'yes' if rr else 'no'}")
    return pt.finish()


# ---------------------------------------------------------------------------
# Fonts
def _font_style(blob):
    from fontTools.ttLib import TTFont
    f = TTFont(io.BytesIO(blob), lazy=True)
    fam = f["name"].getDebugName(16) or f["name"].getDebugName(1) or ""
    sub = f["name"].getDebugName(17) or f["name"].getDebugName(2) or ""
    weight = f["OS/2"].usWeightClass if "OS/2" in f else 400
    italic = "italic" in sub.lower() or bool(f["head"].macStyle & 2)
    return fam, sub, weight, italic


def load_replacement_fonts(d):
    res = []
    for fn in sorted(os.listdir(d)):
        if fn.lower().endswith((".ttf", ".otf")):
            blob = open(os.path.join(d, fn), "rb").read()
            _, _, w, it = _font_style(blob)
            res.append((w, it, fn, blob))
    if not res:
        sys.exit(f"No .ttf files in {d}")
    return res


def pick(repl, weight, italic):
    cand = [r for r in repl if r[1] == italic] or repl
    return min(cand, key=lambda r: abs(r[0] - weight))


# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description="steamd: Material 3 theme patch for Steam on Android")
    ap.add_argument("src", help="base.apk (or index.android.bundle with --check)")
    ap.add_argument("dst", nargs="?", help="output apk (unsigned)")
    ap.add_argument("--seed", help='seed colour for a Material You palette, e.g. "#6750A4"')
    ap.add_argument("--fonts", help="folder with replacement static .ttf files")
    ap.add_argument("--css-url", default=DEFAULT_CSS, help="stylesheet for the store and news")
    ap.add_argument("--radius", default="%d,%d" % DEFAULT_RADIUS,
                    help='corner radius "standard,large" in dp (Steam: 4,10), or "off"')
    ap.add_argument("--keep-uppercase", action="store_true", help="keep Steam's UPPERCASE titles")
    ap.add_argument("--steam-icons", action="store_true", help="keep Steam's own tab bar icons and focus line")
    ap.add_argument("--no-colors", action="store_true", help="keep the native theme colours")
    ap.add_argument("--no-store-theme", action="store_true", help="do not load CSS in the store and news")
    ap.add_argument("--check", action="store_true", help="patch a bare bundle (dry run unless dst is given)")
    a = ap.parse_args()

    palette, primary = PALETTE, DEFAULT_SEED_PRIMARY
    if a.seed:
        if not re.fullmatch(r"#?[0-9A-Fa-f]{6}", a.seed):
            ap.error("--seed must look like #RRGGBB")
        palette, primary = palette_from_seed(a.seed)
        print(f"Seed {a.seed}: primary {primary}")
    if a.radius == "off":
        radius = None
    else:
        try:
            radius = tuple(int(x) for x in a.radius.split(","))
            assert len(radius) == 2 and all(0 <= x <= 100 for x in radius)
        except (ValueError, AssertionError):
            ap.error('--radius must look like "12,24" or "off"')
    kw = dict(palette=palette, seed_primary=primary, css_url=a.css_url,
              colors=not a.no_colors, store=not a.no_store_theme, radius=radius,
              uppercase=a.keep_uppercase, icons=not a.steam_icons)

    if a.check:
        out = patch_bundle(open(a.src, "rb").read(), **kw)
        if a.dst:
            open(a.dst, "wb").write(out)
        return
    if not a.dst:
        ap.error("dst is required")

    repl = load_replacement_fonts(a.fonts) if a.fonts else None
    zin = zipfile.ZipFile(a.src)
    os.makedirs(os.path.dirname(os.path.abspath(a.dst)), exist_ok=True)
    zout = zipfile.ZipFile(a.dst, "w")
    for info in zin.infolist():
        data = zin.read(info)
        name = info.filename
        if name.startswith("META-INF/") and name.split("/")[-1].upper().endswith((".SF", ".RSA", ".EC", ".DSA", ".MF")):
            continue  # old signature
        if name == BUNDLE:
            print("Bundle:")
            data = patch_bundle(data, **kw)
        elif repl and name.lower().endswith(".ttf"):
            try:
                fam, sub, w, it = _font_style(data)
            except Exception:
                fam = ""
            if "motiva" in fam.lower():
                r = pick(repl, w, it)
                print(f"Font {name}: {fam} {sub} (w{w}{' i' if it else ''}) -> {r[2]}")
                data = r[3]
        ni = zipfile.ZipInfo(name, info.date_time)
        ni.compress_type = info.compress_type
        ni.external_attr = info.external_attr
        zout.writestr(ni, data)
    zout.close()
    print(f"\nDone: {a.dst} (unsigned: sign it together with the split APKs)")


if __name__ == "__main__":
    main()
