"""Regenerates NATIVE_BODIES in patch_steam.py and SteamThemePatch.kt from gen_native.py output.

usage: python patch/tools/gen_native.py bundle > native.txt && python patch/tools/update_native.py native.txt
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
PY = os.path.join(ROOT, "patch", "patch_steam.py")
KT = os.path.join(ROOT, "android", "patcher", "src", "main", "kotlin", "com", "jettaxp", "steamd", "patcher", "SteamThemePatch.kt")
NAMES = [("tab pill", "TAB_ITEM"), ("menu sections", "LINK_SECTION"), ("menu rows", "HEADER_LINK"), ("menu dividers", "MAIN_MENU")]

nat = dict(re.findall(r'(\w+) = "([0-9a-f]+)"', open(sys.argv[1]).read()))
chunks = lambda h: [h[i:i + 100] for i in range(0, len(h), 100)]

py = "NATIVE_BODIES = {\n" + "".join(
    f'    "{name}": (\n' + "\n".join(f'        "{c}"' for c in chunks(nat[k + "_ORIGINAL"])) + ",\n"
    + "\n".join(f'        "{c}"' for c in chunks(nat[k + "_PATCHED"])) + "),\n" for name, k in NAMES) + "}\n"
s = open(PY).read()
s = re.sub(r"NATIVE_BODIES = \{\n.*?\n\}\n", lambda m: py, s, flags=re.S)
open(PY, "w").write(s)

kt = "    private val NATIVE_BODIES = mapOf(\n" + "".join(
    f'        "{name}" to (\n' + " +\n".join(f'            "{c}"' for c in chunks(nat[k + "_ORIGINAL"])) + "\n            to\n"
    + " +\n".join(f'            "{c}"' for c in chunks(nat[k + "_PATCHED"])) + "),\n" for name, k in NAMES) + "    )\n"
s = open(KT).read()
s = re.sub(r"    private val NATIVE_BODIES = mapOf\(\n.*?\n    \)\n", lambda m: kt, s, flags=re.S)
open(KT, "w").write(s)
