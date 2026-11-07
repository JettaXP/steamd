"""Prints the original and rewritten bodies of the native functions steamd replaces.

usage: python patch/tools/gen_native.py index.android.bundle
"""
import sys
from hbcasm import Fn, load, fid_by_name, sid, ins, u16, u32


def tab_bar_item(rd, data):
    """BuiltinTabBarItem: drop the focus line and the debug warnings, wrap the icon in an M3 pill
    (Box with borderRadius 'full', paddingX 'gutter', paddingY 'xxsmall', background 'neutral' when focused)."""
    f = Fn(rd.r, fid_by_name(rd, "BuiltinTabBarItem"), data)
    pill_literal = bytes([0x02, 10]) + literal(rd, data, "PendingRequestDetails", "05000400", 85239)
    add = (pill_literal
           + ins("LoadConstUndefined", 16)
           + ins("JmpFalse", 7, 15)
           + ins("LoadConstString", 16, u16(sid(rd, "neutral")))
           + ins("PutById", 10, 16, 0, u16(sid(rd, "background")))
           + ins("LoadConstString", 16, u16(sid(rd, "gutter")))
           + ins("PutById", 10, 16, 0, u16(sid(rd, "paddingX")))
           + ins("PutNewOwnByIdShort", 10, 8, sid(rd, "children"))
           + ins("LoadFromEnvironment", 16, 9, 14)
           + ins("GetByIdShort", 16, 16, 0, sid(rd, "jsx"))
           + ins("LoadFromEnvironment", 17, 9, 8)
           + ins("GetByIdShort", 17, 17, 0, sid(rd, "Box"))
           + ins("Call3", 8, 16, 3, 17, 10))
    padding = bytes([0x6E, 0x00, 13])                          # paddingVertical 17 -> 13 for the taller pill
    return f, f.rewrite(delete=[(0x9F, 0xBC), (0x115, 0x118), (0x171, 0x189), (0x1F3, 0x209)],
                        insert={0x118: padding, 0x1B0: add})


def link_section(rd, data):
    """LinkSection (main menu groups): each section becomes a rounded card
    ({borderRadius: 'standard', overflow: 'hidden', marginX: 'small', background: 'primary'}).
    Room comes from reusing the Box component already held in r1 instead of reloading it."""
    f = Fn(rd.r, fid_by_name(rd, "LinkSection"), data)
    card = (bytes.fromhex("0200") + literal(rd, data, "PendingRequestDetails", "03000200", 83041)
            + ins("LoadConstString", 4, u16(sid(rd, "small")))
            + ins("PutNewOwnById", 0, 4, u16(sid(rd, "marginX")))
            + ins("LoadConstString", 4, u16(sid(rd, "primary")))
            + ins("PutNewOwnById", 0, 4, u16(sid(rd, "background"))))
    box = lambda reg: ins("Mov", reg, 1)
    return f, f.rewrite(delete=[(0x90, 0x9B), (0xAD, 0xB6), (0x119, 0x122), (0x1CF, 0x1D8), (0x254, 0x25D)],
                        insert={0x9B: card, 0xB6: box(7), 0x122: box(19), 0x1D8: box(21), 0x25D: box(7)})


def literal(rd, data, fn, counts, values):
    """Operands (after the register) of an existing NewObjectWithBufferLong literal in function fn."""
    f = Fn(rd.r, fid_by_name(rd, fn), data)
    for a, b, name, _ in f.ins:
        raw = f.original[a:b]
        if name == "NewObjectWithBufferLong" and raw[2:6] == bytes.fromhex(counts) \
                and int.from_bytes(raw[10:14], "little") == values:
            return raw[2:]
    raise LookupError(f"literal in {fn}")


if __name__ == "__main__":
    rd, data = load(sys.argv[1])
    for name, gen in (("TAB_ITEM", tab_bar_item), ("LINK_SECTION", link_section)):
        f, body = gen(rd, data)
        print(f"{name}_ORIGINAL = \"{f.original.hex()}\"")
        print(f"{name}_PATCHED = \"{body.hex()}\"")
