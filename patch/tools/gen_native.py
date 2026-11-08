"""Prints the original and rewritten bodies of the native functions steamd replaces.

usage: python patch/tools/gen_native.py index.android.bundle
"""
import sys
from hbcasm import Fn, load, fid_by_name, sid, ins, u16, u32


LAYOUT_ANIMATION_MODULE = 395   # Metro id of react-native/Libraries/LayoutAnimation (checked against the __d call)


def tab_bar_item(rd, data):
    """BuiltinTabBarItem: Material 3 navigation bar item.
    The focus line goes; the icon sits in a pill Box (borderRadius 'full', paddingY 'xxsmall'):
    focused  -> paddingX 'gutter', marginX 'none', background 'neutral' (secondary container),
    otherwise -> paddingX 'small', marginX 'xsmall' (same total width, so icons never shift).
    On the focused item LayoutAnimation.easeInEaseOut() is armed, so the pill grows from the icon.
    Room: the debug warnings, the focus line, hitSlop, the default position: relative, reloads of jsx."""
    f = Fn(rd.r, fid_by_name(rd, "BuiltinTabBarItem"), data)
    S = lambda x: u16(sid(rd, x))
    anim = (ins("GetGlobalObject", 17)
            + ins("GetById", 17, 17, 0, S("__r"))
            + ins("LoadConstInt", 14, u32(LAYOUT_ANIMATION_MODULE))
            + ins("Call2", 17, 17, 3, 14)
            + ins("GetByIdShort", 17, 17, 0, sid(rd, "default"))
            + ins("GetById", 14, 17, 0, S("easeInEaseOut"))
            + ins("Call1", 17, 14, 17))
    focused = (ins("LoadConstString", 17, S("gutter")) + ins("PutById", 10, 17, 0, S("paddingX"))
               + ins("LoadConstString", 16, S("none")) + ins("LoadConstString", 11, S("neutral")) + anim)
    pill = (bytes([0x02, 10]) + literal(rd, data, "PendingRequestDetails", "05000400", 85239)
            + ins("LoadConstString", 16, S("xsmall"))
            + ins("LoadConstUndefined", 11)
            + ins("JmpFalse", 3 + len(focused), 15)
            + focused
            + ins("PutById", 10, 16, 0, S("marginX"))
            + ins("PutById", 10, 11, 0, S("background"))
            + ins("PutNewOwnByIdShort", 10, 8, sid(rd, "children"))
            + ins("Call3", 8, 2, 3, 5, 10))
    return f, f.rewrite(
        delete=[(0x9F, 0xBC), (0x113, 0x11D), (0x132, 0x13B), (0x14E, 0x152), (0x160, 0x189), (0x1BA, 0x1C3),
                (0x1F3, 0x209)],
        insert={0x13B: ins("Mov", 6, 2),
                0x152: ins("LoadConstString", 16, S("small")) + ins("PutNewOwnById", 4, 16, S("paddingY")),
                0x1B0: pill,
                0x1C3: ins("Mov", 11, 2)})


def row_style(rd, reg, tmp, tmp2):
    """style {backgroundColor: '#2F343E' (surfaceContainerHigh after the palette), borderRadius: 4}."""
    return (ins("NewObject", tmp)
            + ins("LoadConstStringLongIndex", tmp2, u32(sid(rd, "#2F343E")))
            + ins("PutNewOwnById", tmp, tmp2, u16(sid(rd, "backgroundColor")))
            + ins("LoadConstUInt8", tmp2, 4)
            + ins("PutNewOwnById", tmp, tmp2, u16(sid(rd, "borderRadius")))
            + ins("PutNewOwnByIdShort", reg, tmp, sid(rd, "style")))


def link_section(rd, data):
    """LinkSection (Store, Community): the section becomes a segment of the menu group.
    Room: useState's result is indexed directly instead of going through _slicedToArray,
    and the Box component already held in r1 is reused instead of reloaded."""
    f = Fn(rd.r, fid_by_name(rd, "LinkSection"), data)
    head = (ins("NewObject", 0)
            + ins("LoadConstString", 4, u16(sid(rd, "hidden")))
            + ins("PutNewOwnById", 0, 4, u16(sid(rd, "overflow")))
            + row_style(rd, 0, 4, 6))
    box = lambda reg: ins("Mov", reg, 1)
    return f, f.rewrite(
        delete=[(0x57, 0x69), (0x6B, 0x6F), (0x76, 0x7A),
                (0x90, 0x9B), (0xAD, 0xB6), (0x119, 0x122), (0x1CF, 0x1D8), (0x254, 0x25D)],
        insert={0x6F: ins("GetByVal", 5, 2, 0), 0x7A: ins("GetByVal", 0, 2, 0),
                0x9B: head, 0xB6: box(7), 0x122: box(19), 0x1D8: box(21), 0x25D: box(7)})


def header_link(rd, data):
    """HeaderLink (News, Steam Guard, ...): each row becomes a segment of the menu group.
    Room: an unused useTheme().color.background.primary lookup and Box reloads."""
    f = Fn(rd.r, fid_by_name(rd, "HeaderLink"), data)
    box = lambda reg: ins("Mov", reg, 1)
    return f, f.rewrite(delete=[(0x06, 0x10), (0x12, 0x26), (0x8A, 0x93), (0x160, 0x169), (0x17D, 0x186)],
                        insert={0x4F: row_style(rd, 0, 4, 5), 0x93: box(11), 0x169: box(13), 0x186: box(16)})


def main_menu(rd, data):
    """MainMenuScreen: the rows from Store to Chat form one group (a Box with a 24dp radius that clips
    its children, 12dp side margins); dividers become 4dp transparent spacers; the small links below
    move out of the group so it ends at Chat.
    Room: jsx is taken from r9 (jsxs, the same function in production React) and Box from r8."""
    f = Fn(rd.r, fid_by_name(rd, "MainMenuScreen"), data)
    o, ins_ = f.original, f.ins
    div = sid(rd, "Divider")
    delete, insert = [], {}
    # group container: r7 = NewObject at the start of the menu Box props
    box_id = sid(rd, "Box")
    a7 = next(ins_[k + 1][0] for k, (a, b, n, _) in enumerate(ins_)
              if n == "GetByIdShort" and o[a + 1] == 8 and o[a + 4] == box_id and ins_[k + 1][2] == "NewObject" and o[ins_[k + 1][0] + 1] == 7)
    after7 = next(a for a, b, n, _ in ins_ if a > a7)
    insert[after7] = (ins("LoadConstString", 10, u16(sid(rd, "large"))) + ins("PutNewOwnById", 7, 10, u16(sid(rd, "borderRadius")))
                      + ins("LoadConstString", 10, u16(sid(rd, "hidden"))) + ins("PutNewOwnById", 7, 10, u16(sid(rd, "overflow")))
                      + ins("LoadConstString", 10, u16(sid(rd, "small"))) + ins("PutNewOwnById", 7, 10, u16(sid(rd, "marginX"))))
    sites = [k for k, (a, b, n, _) in enumerate(ins_) if n == "GetById" and int.from_bytes(o[a + 4:a + 6], "little") == div]
    assert len(sites) == 8
    for k in sites[:-1]:
        lj, gj, le, gd, no, call, put = ins_[k - 3:k + 4]
        assert (lj[2], gj[2], le[2], no[2], call[2], put[2]) == ("LoadFromEnvironment", "GetByIdShort", "LoadFromEnvironment",
                                                                 "NewObject", "Call3", "PutOwnByIndex"), k
        rj = o[gj[0] + 1]
        delete.append((lj[0], call[0]))
        insert[call[0]] = (ins("Mov", rj, 9) + ins("NewObject", 11) + ins("LoadConstString", 12, u16(sid(rd, "xxsmall")))
                           + ins("PutNewOwnById", 11, 12, u16(sid(rd, "paddingTop"))) + ins("Mov", 12, 8))
    # the divider after Chat goes away (its jsx load stays: the register is used again below)
    k = sites[-1]
    delete.append((ins_[k - 1][0], ins_[k + 3][1]))
    # small links: out of the group, into the screen's outer array
    puts = [x for x in ins_ if x[2] == "PutOwnByIndex" and o[x[0] + 1] == 10 and o[x[0] + 3] == 17]
    assert len(puts) == 1 and o[puts[0][0] + 2] == 11
    delete.append((puts[0][0], puts[0][1]))
    arr = next(x for x in ins_ if x[2] == "NewArray" and o[x[0] + 1] == 7 and o[x[0] + 2] == 2)
    delete.append((arr[0], arr[1]))
    insert[arr[1]] = bytes(o[arr[0]:arr[0] + 2]) + (3).to_bytes(2, "little")
    first = next(x for x in ins_ if x[0] > arr[0] and x[2] == "PutOwnByIndex" and o[x[0] + 1] == 7 and o[x[0] + 3] == 0)
    insert[first[1]] = bytes(o[first[0]:first[0] + 2]) + bytes([11, 1])
    second = next(x for x in ins_ if x[0] > first[0] and x[2] == "PutOwnByIndex" and o[x[0] + 1] == 7 and o[x[0] + 3] == 1)
    delete.append((second[0], second[1]))
    insert[second[1]] = bytes(o[second[0]:second[0] + 3]) + bytes([2])
    return f, f.rewrite(delete=delete, insert=insert)


def list_item(rd, data):
    """Profile menu ListItem (Friend Activity, Friends, ...): 4dp corners, like the main menu rows.
    Room: the pressed opacity (pressedProps becomes an empty object), a jsx reload and the undefined `this`."""
    f = Fn(rd.r, fid_by_name(rd, "ListItem", 138), data)
    o = f.original
    style = (ins("NewObject", 5) + ins("LoadConstUInt8", 6, 4) + ins("PutNewOwnById", 5, 6, u16(sid(rd, "borderRadius")))
             + ins("PutById", 1, 5, 0, u16(sid(rd, "style")))
             # the Box literal has pressedProps: null and Box reads pressedProps.style, so it needs an object
             + ins("NewObject", 6) + ins("PutById", 1, 6, 0, u16(sid(rd, "pressedProps"))))
    calls = [x for x in f.ins if x[2] == "Call3"]
    assert len(calls) == 2
    c1, c2 = calls
    return f, f.rewrite(delete=[(0x36, 0x5B), (0x76, 0x78), (c1[0], c1[1]), (c2[0], c2[1])],
                        insert={0x5B: style, c1[1]: ins("Call3", 4, 3, 3, 5, 4), c2[1]: ins("Call3", 0, 3, 3, 2, 1)})


def persona_menu(rd, data):
    """PersonaMenuScreenContent: the My Content rows form one group like the main menu
    (24dp clipping Box, 12dp side margins, 4dp spacers instead of dividers); the title stays above
    and the account rows below. Room: jsx is taken from r19 (jsxs) and Box from r18."""
    f = Fn(rd.r, fid_by_name(rd, "PersonaMenuScreenContent"), data)
    o, L = f.original, f.ins
    div, jsx = sid(rd, "Divider"), sid(rd, "jsx")
    puts = [k for k, x in enumerate(L) if x[2] == "PutOwnByIndex" and o[x[0] + 1] == 22]
    assert [o[L[k][0] + 3] for k in puts] == list(range(14)), [o[L[k][0] + 3] for k in puts]
    delete, insert = [], {}
    first, last = L[puts[0]][0], L[puts[13]][0]
    # title: keep it in r35 instead of the list
    delete.append((first, L[puts[0]][1])); insert[L[puts[0]][1]] = ins("Mov", 35, 23)
    # the divider after Inventory goes
    delete.append((L[puts[11]][1], L[puts[12]][1]))
    lo, hi = L[puts[0]][1], L[puts[11]][1]
    for k, (a, b, n, _) in enumerate(L):
        if not (lo <= a < hi):
            continue
        if n == "LoadFromEnvironment" and o[a + 2] == 17 and o[a + 3] == 25 and L[k + 1][2] == "GetByIdShort" \
                and o[L[k + 1][0] + 4] == jsx:
            delete.append((a, L[k + 1][1])); insert[L[k + 1][1]] = ins("Mov", o[L[k + 1][0] + 1], 19)
        if n == "GetById" and int.from_bytes(o[a + 4:a + 6], "little") == div:
            le, no = L[k - 1], L[k + 1]
            assert le[2] == "LoadFromEnvironment" and no[2] == "NewObject"
            rd_, ro = o[a + 1], o[no[0] + 1]
            delete.append((le[0], no[1]))
            insert[no[1]] = (ins("NewObject", ro) + ins("LoadConstString", rd_, u16(sid(rd, "xxsmall")))
                             + ins("PutNewOwnById", ro, rd_, u16(sid(rd, "paddingTop"))) + ins("Mov", rd_, 18))
    S = lambda x: u16(sid(rd, x))
    group = (ins("Mov", 36, 23) + ins("NewObject", 23)
             + ins("LoadConstString", 24, S("large")) + ins("PutNewOwnById", 23, 24, S("borderRadius"))
             + ins("LoadConstString", 24, S("hidden")) + ins("PutNewOwnById", 23, 24, S("overflow"))
             + ins("LoadConstString", 24, S("small")) + ins("PutNewOwnById", 23, 24, S("marginX"))
             + ins("PutNewOwnByIdShort", 23, 22, sid(rd, "children"))
             + ins("Call3", 23, 19, 3, 18, 23)
             + ins("NewArray", 22, u16(3))
             + ins("PutOwnByIndex", 22, 35, 0) + ins("PutOwnByIndex", 22, 23, 1) + ins("PutOwnByIndex", 22, 36, 2))
    delete.append((last, L[puts[13]][1])); insert[L[puts[13]][1]] = group
    # the list array no longer holds 14 items
    return f, f.rewrite(delete=delete, insert=insert)


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
    for name, gen in (("TAB_ITEM", tab_bar_item), ("LINK_SECTION", link_section), ("HEADER_LINK", header_link), ("MAIN_MENU", main_menu), ("LIST_ITEM", list_item), ("PERSONA_MENU", persona_menu)):
        f, body = gen(rd, data)
        print(f"{name}_ORIGINAL = \"{f.original.hex()}\"")
        print(f"{name}_PATCHED = \"{body.hex()}\"")
