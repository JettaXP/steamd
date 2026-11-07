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
    "#1A97FF": "#8AB4F8",  # background.info
    "#DFE3E6": "#E3E2E6",  # foreground.standard (text)
    "#8B929A": "#C4C6D0",  # foreground.secondary (secondary text)
    "#3D4450": "#44474F",  # foreground.neutral
    "#30333A": "#2B2C31",  # divider "standard" block
    "#60636A": "#8E9099",  # divider "bright"
}

# Which Material 3 role each original colour takes when --seed is used.
SEED_ROLES = {
    "#1F2127": "surface",
    "#23252A": "surfaceContainerLow",
    "#2A2C34": "surfaceContainer",
    "#3C3F49": "secondaryContainer",
    "#1A9FFF": "primary",
    "#1A97FF": "primary",
    "#DFE3E6": "onSurface",
    "#8B929A": "onSurfaceVariant",
    "#3D4450": "outlineVariant",
    "#30333A": "surfaceContainerHigh",
    "#60636A": "outline",
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
            if length >= 0xFF:
                sys.exit(f"string #{i}: new value too long for a small entry")
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
                 colors=True, store=True, radius=DEFAULT_RADIUS, uppercase=False):
    if data[:8] != bytes.fromhex("c61fbc03c103191f"):
        sys.exit("Not a Hermes bundle")
    ver = int.from_bytes(data[8:12], "little")
    if hashlib.sha1(data[:-20]).digest() != data[-20:]:
        sys.exit("SHA-1 footer mismatch: the bundle is already modified or damaged")
    pt = _Patch(data)
    n = patch_colors(pt, palette) if colors else 0
    s = patch_webview(pt, seed_primary, css_url) if store else False
    rr = patch_radius(pt, radius) if radius else False
    if not uppercase:
        patch_uppercase(pt)
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
              uppercase=a.keep_uppercase)

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
