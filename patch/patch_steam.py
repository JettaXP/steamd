#!/usr/bin/env python3
"""
Material 3-style theme patch for the Steam Android app (personal use only).

What it does to base.apk:
  1. Hermes bundle (assets/index.android.bundle): replaces the colour strings
     of the built-in "SteamMobile" theme in place (same length) and
     recomputes the SHA-1 footer. No bytecode is rewritten.
  2. Fonts (optional, --fonts DIR): replaces the Motiva Sans .ttf files with
     your own fonts (for example Roboto), matched by weight and italic.
The output is an UNSIGNED apk: sign it, together with the split APKs, with one key.

Requirements:  pip install hermes-dec fonttools
Usage:
  python patch_steam.py base.apk base-patched.apk [--fonts roboto_dir] [--no-colors]
  python patch_steam.py --check index.android.bundle     (palette dry run)
"""
import argparse, hashlib, io, os, sys, zipfile

# ---------------------------------------------------------------------------
# Palette.  Key: original colour in the SteamMobile theme -> new colour.
# Same length (#RRGGBB) only.  Edit freely.
PALETTE = {
    # background.*
    "#1F2127": "#1B1B1F",  # secondary (status bar, deepest background)
    "#23252A": "#1F1F23",  # tertiary
    "#2A2C34": "#26272B",  # primary (main surface, menu rows)
    "#3C3F49": "#3E4759",  # neutral (chips, tonal buttons; M3 secondaryContainer)
    "#1A9FFF": "#8AB4F8",  # brand (accent)
    "#1A97FF": "#8AB4F8",  # info
    # foreground.*
    "#DFE3E6": "#E3E2E6",  # standard text
    "#8B929A": "#C4C6D0",  # secondary text
    "#3D4450": "#44474F",  # neutral
    # other UI colours seen next to the theme
    "#30333A": "#2B2C31",  # "standard" divider block
    "#60636A": "#8E9099",  # "bright" divider
}

BUNDLE = "assets/index.android.bundle"


# Developer-only messages whose storage we reuse for the new colours
# (Hermes packs strings with overlap, so colours cannot be changed in place).
DONORS = (
    "[expo-av]: Expo AV has been deprecated",
    "This unit of work tag should not have side-effects",
)


class _Reader:
    """Thin wrapper around hermes-dec that remembers where the string table and storage start."""
    def __init__(self, data):
        from hermes_dec.parsers.hbc_file_parser import HBCReader
        outer = self
        class R(HBCReader):
            def read_small_string_table(self):
                self.align_over_padding()
                outer.table_start = self.file_buffer.tell()
                self.file_buffer.seek(outer.table_start)
                return super().read_small_string_table()
            def read_string_storage(self):
                self.align_over_padding()
                outer.storage_start = self.file_buffer.tell()
                self.file_buffer.seek(outer.storage_start)
                return super().read_string_storage()
        self.r = R()
        self.r.read_whole_file(io.BytesIO(data))

    def entries(self):
        r = self.r
        for i in range(r.header.stringCount):
            e = r.small_string_table[i]
            utf16 = e.isUTF16
            if e.length == 0xFF:
                e = r.overflow_string_table[e.offset]
            n = e.length * (2 if utf16 else 1)
            yield i, utf16, e.offset, n


def patch_bundle(data: bytes, palette=PALETTE, verbose=True) -> bytes:
    if data[:8] != bytes.fromhex("c61fbc03c103191f"):
        sys.exit("Not a Hermes bundle")
    ver = int.from_bytes(data[8:12], "little")
    if hashlib.sha1(data[:-20]).digest() != data[-20:]:
        sys.exit("SHA-1 footer mismatch: the file is already modified or damaged")
    rd = _Reader(data)
    S = rd.r.strings
    R = list(rd.entries())
    by_off = sorted(R, key=lambda e: e[2])
    offs = [e[2] for e in by_off]
    import bisect

    def overlaps(i, a, b):
        k = bisect.bisect_left(offs, a - 70000)  # overflow strings can be long
        return [j for j, u, o, m in by_off[k:bisect.bisect_left(offs, b)] if j != i and o < b and o + m > a]

    # 1) colour entries to patch
    jobs = []
    for old, new in palette.items():
        assert len(old) == len(new) == 7 and old.startswith("#") and new.startswith("#"), (old, new)
        idx = [i for i, s in enumerate(S) if s == old]
        if not idx:
            print(f"  [skip] {old}: not in this version"); continue
        for i in idx:
            if R[i][1]: print(f"  [skip] {old}: UTF-16"); continue
            if data[rd.table_start + 4*i + 3] == 0xFF:
                print(f"  [skip] {old}: overflow entry"); continue
            jobs.append((i, old, new))
    if not jobs:
        print("Nothing to patch"); return data

    # 2) donor: a dev-only message with no overlapping strings
    news = sorted({n for _, _, n in jobs})
    need = 7 * len(news)
    donor = None
    for pref in DONORS:
        for j, u, o, m in R:
            if not u and m >= need and S[j].startswith(pref) and not overlaps(j, o, o + m):
                donor = (j, o, m); break
        if donor: break
    if not donor:
        sys.exit("No suitable donor string found: the app version changed, the script needs updating")
    j, doff, _ = donor
    if verbose: print(f"  donor: {S[j][:60]!r}...")

    out = bytearray(data)
    pos = {}
    for k, n in enumerate(news):
        p = rd.storage_start + doff + 7*k
        out[p:p+7] = n.encode()
        pos[n] = doff + 7*k
    # 3) repoint the table entries (bit0 isUTF16, bits1-23 offset, bits24-31 length)
    for i, old, new in jobs:
        t = rd.table_start + 4*i
        v = int.from_bytes(out[t:t+4], "little")
        assert (v >> 24) == 7 and (v & 1) == 0
        v = (v & 0xFF000001) | (pos[new] << 1)
        out[t:t+4] = v.to_bytes(4, "little")
        if verbose: print(f"  {old} -> {new}")
    out[-20:] = hashlib.sha1(bytes(out[:-20])).digest()
    print(f"Hermes v{ver}: colours replaced: {len(jobs)}")
    return bytes(out)


# ---------------------------------------------------------------------------
# Fonts
def _font_style(blob):
    from fontTools.ttLib import TTFont
    f = TTFont(io.BytesIO(blob), lazy=True)
    fam = (f["name"].getDebugName(16) or f["name"].getDebugName(1) or "")
    sub = (f["name"].getDebugName(17) or f["name"].getDebugName(2) or "")
    weight = f["OS/2"].usWeightClass if "OS/2" in f else 400
    italic = "italic" in sub.lower() or bool(f["head"].macStyle & 2)
    return fam, sub, weight, italic


def load_replacement_fonts(d):
    res = []
    for fn in sorted(os.listdir(d)):
        if fn.lower().endswith((".ttf", ".otf")):
            blob = open(os.path.join(d, fn), "rb").read()
            fam, sub, w, it = _font_style(blob)
            res.append((w, it, fn, blob))
    if not res:
        sys.exit(f"No .ttf files in {d}")
    return res


def pick(repl, weight, italic):
    cand = [r for r in repl if r[1] == italic] or repl
    return min(cand, key=lambda r: abs(r[0] - weight))


# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src"); ap.add_argument("dst", nargs="?")
    ap.add_argument("--fonts", help="folder with replacement .ttf files (static, not variable)")
    ap.add_argument("--no-colors", action="store_true")
    ap.add_argument("--check", action="store_true", help="bundle only: dry run without writing")
    a = ap.parse_args()

    if a.check:
        out = patch_bundle(open(a.src, "rb").read())
        if a.dst: open(a.dst, "wb").write(out)
        return
    if not a.dst:
        ap.error("dst is required")

    repl = load_replacement_fonts(a.fonts) if a.fonts else None
    zin = zipfile.ZipFile(a.src)
    zout = zipfile.ZipFile(a.dst, "w")
    for info in zin.infolist():
        data = zin.read(info)
        name = info.filename
        if name.startswith("META-INF/") and name.split("/")[-1].upper().endswith((".SF", ".RSA", ".EC", ".DSA", ".MF")):
            continue  # old signature
        if name == BUNDLE and not a.no_colors:
            print("Bundle:"); data = patch_bundle(data)
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
    print(f"\nDone: {a.dst} (unsigned; run zipalign + apksigner)")


if __name__ == "__main__":
    main()
