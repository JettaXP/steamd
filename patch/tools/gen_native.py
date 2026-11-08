"""Prints the original and rewritten bodies of the native functions steamd replaces.

usage: python patch/tools/gen_native.py index.android.bundle
"""
import sys
from hbcasm import Fn, load, fid_by_name, sid, ins, u16, u32


LAYOUT_ANIMATION_MODULE = 395   # Metro id of react-native/Libraries/LayoutAnimation (checked against the __d call)


REANIMATED_MODULE = 572          # Metro id of react-native-reanimated's index (checked against the __d call)


def fill_literal(rd, data):
    """Operands of a short {'position': 'absolute', 'left': 0, 'right': 0, 'top': 0, 'bottom': 0} literal."""
    f = Fn(rd.r, 3287, data)
    for a, b, name, _ in f.ins:
        raw = f.original[a:b]
        if name == "NewObjectWithBuffer" and raw[2:10] == bytes.fromhex("050005002d008d42"):
            return raw[2:]
    raise LookupError("absolute fill literal")


def tab_bar_item(rd, data, label=False, size=None):
    """BuiltinTabBarItem: Material 3 navigation bar item.
    The focus line goes; the icon sits in a 64x32 pill Box (borderRadius 'full', padding 'xxsmall',
    paddingX 'gutter') that is the same on every tab, so nothing shifts. On the focused tab a Reanimated
    Animated.View fills the pill behind the icon (absolute fill, radius 16, '#3C3F49' = secondary container
    after the palette) and enters with StretchInX, so the indicator grows out from the centre while the icon
    stays put. (LayoutAnimation is off on Android in this React Native build.)
    Room: the debug warnings, the focus line, hitSlop, the default position: relative, jsx reloads and the
    object-tab check (an unknown tab already returns null through the undefined check)."""
    f = Fn(rd.r, fid_by_name(rd, "BuiltinTabBarItem"), data)
    S = lambda x: u16(sid(rd, x))
    paging = Fn(rd.r, fid_by_name(rd, "PagingDots"), data)
    pill_lit = next(paging.original[a + 2:b] for a, b, n, _ in paging.ins
                    if n == "NewObjectWithBufferLong" and paging.original[a + 2:a + 6] == bytes.fromhex("03000200"))
    bg = (bytes([0x01, 16]) + fill_literal(rd, data)                              # r16 = absolute fill style
          + ins("LoadConstUInt8", 17, 16) + ins("PutNewOwnById", 16, 17, S("borderRadius"))
          + ins("LoadConstString", 17, S("#3C3F49")) + ins("PutNewOwnById", 16, 17, S("backgroundColor"))
          + ins("NewObject", 11) + ins("PutNewOwnByIdShort", 11, 16, sid(rd, "style"))
          + ins("GetGlobalObject", 17) + ins("GetById", 17, 17, 0, S("__r"))
          + ins("LoadConstInt", 16, u32(REANIMATED_MODULE)) + ins("Call2", 17, 17, 3, 16)
          + ins("GetById", 16, 17, 0, S("StretchInX")) + ins("PutNewOwnById", 11, 16, S("entering"))
          + ins("GetByIdShort", 17, 17, 0, sid(rd, "default")) + ins("GetById", 17, 17, 0, S("View"))
          + ins("Call3", 11, 2, 3, 17, 11)                                       # r11 = <Animated.View>
          + ins("PutOwnByIndex", 14, 11, 0))
    pill = (bytes([0x02, 10]) + pill_lit
            + ins("LoadConstString", 11, S("gutter")) + ins("PutNewOwnById", 10, 11, S("paddingX"))
            + ins("NewArray", 14, u16(2)) + ins("PutOwnByIndex", 14, 8, 1)
            + ins("JmpFalse", 3 + len(bg), 15) + bg
            + ins("PutNewOwnByIdShort", 10, 14, sid(rd, "children"))
            + ins("Call3", 8, 2, 3, 5, 10))
    delete = [(0x76, 0x8A), (0x9F, 0xBC), (0x113, 0x11D), (0x132, 0x13B), (0x14E, 0x152), (0x160, 0x189),
              (0x1BA, 0x1C3), (0x1F3, 0x20D)]
    insert = {0x13B: ins("Mov", 6, 2),
              0x152: ins("LoadConstString", 16, S("small")) + ins("PutNewOwnById", 4, 16, S("paddingY")),
              0x1B0: pill,
              0x1C3: ins("Mov", 11, 2)}
    if label:
        # <Text size="small" tone={focused ? 'info' : undefined}>{Localize(tab.name_token)}</Text> under the pill
        # The menu tab's token (#MainMenuScreen_Title) has no translation; #MainMenu_Title is "Menu" / "Меню".
        menu = sid(rd, "#MainMenu_Title")
        text = (ins("GetGlobalObject", 16) + ins("GetById", 16, 16, 0, S("__r"))
                + ins("GetById", 14, 11, 0, S("name_token"))
                + ins("LoadConstString", 17, S("#MainMenuScreen_Title"))
                + ins("JStrictNotEqual", 4 + 6, 14, 17) + ins("LoadConstStringLongIndex", 14, u32(menu))
                + ins("LoadConstInt", 10, u32(LOCALIZE_MODULE)) + ins("Call2", 10, 16, 3, 10)
                + ins("GetById", 10, 10, 0, S("Localize")) + ins("Call2", 14, 10, 3, 14)
                + ins("LoadConstInt", 10, u32(TEXT_MODULE)) + ins("Call2", 10, 16, 3, 10)
                + ins("GetByIdShort", 10, 10, 0, sid(rd, "Text"))
                + ins("NewObject", 17) + ins("PutNewOwnByIdShort", 17, 14, sid(rd, "children"))
                + ins("LoadConstString", 14, S("small")) + ins("PutNewOwnById", 17, 14, S("size"))
                + ins("PutNewOwnById", 17, 15, S("tone"))
                + ins("NewObject", 16)                                        # style: 12/16 like an M3 label
                + ins("LoadConstUInt8", 14, 12) + ins("PutNewOwnById", 16, 14, S("fontSize"))
                + ins("LoadConstUInt8", 14, 16) + ins("PutNewOwnById", 16, 14, S("lineHeight"))
                + ins("PutNewOwnByIdShort", 17, 16, sid(rd, "style"))
                + ins("LoadConstUInt8", 14, 1) + ins("PutNewOwnById", 17, 14, S("numberOfLines"))
                + ins("Call3", 14, 2, 3, 10, 17) + ins("PutOwnByIndex", 7, 14, 3))
        insert[0x1B0] = text + pill
        delete.append((0x189, 0x18D)); insert[0x18D] = ins("NewArray", 7, u16(4))
        # Equal tab widths: the root Column (flexGrow only) becomes a Box with style {flex: 1}.
        assert f.original[0x12A] == 55 and f.original[0x12E:0x130] == S("Column")[0].to_bytes(2, "little")
        delete.append((0x12A, 0x130))
        insert[0x130] = ins("GetByIdShort", 1, 0, 0, sid(rd, "Box"))
        insert[0x13B] = (ins("NewObject", 16) + ins("LoadConstUInt8", 17, 1) + ins("PutNewOwnById", 16, 17, S("flex"))
                         + ins("PutNewOwnByIdShort", 0, 16, sid(rd, "style")) + insert[0x13B])
    return f, f.rewrite(delete=delete, insert=insert, size=size)


LOCALIZE_MODULE = 1450          # exports.Localize
TEXT_MODULE = 1589              # exports.Text (design system)
TAB_DONOR = "FormZoo"           # Developer Zoo screen, only reachable for Valve staff


def tab_bar_relocated(rd, data):
    """BuiltinTabBarItem with labels does not fit its 525 bytes, so it is written over the body of an unused
    function (FormZoo) and the tab bar module creates the tab item closure from that function instead.
    Returns three byte replacements: donor body, donor function header, closure in the tab bar module."""
    import struct
    zoo_id, tab_id = fid_by_name(rd, TAB_DONOR), fid_by_name(rd, "BuiltinTabBarItem")
    zoo = Fn(rd.r, zoo_id, data)
    _, body = tab_bar_item(rd, data, label=True, size=zoo.size)
    hz, ht = 128 + 16 * zoo_id, 128 + 16 * tab_id
    old_hdr, tab_hdr = data[hz:hz + 16], data[ht:ht + 16]
    w0, w1, w2 = struct.unpack("<III", tab_hdr[:12])
    new_hdr = struct.pack("<III", (zoo.offset & 0x1FFFFFF) | (w0 & ~0x1FFFFFF), (zoo.size & 0x7FFF) | (w1 & ~0x7FFF), w2) + tab_hdr[12:]
    assert struct.unpack("<I", old_hdr[:4])[0] & 0x1FFFFFF == zoo.offset
    mod = Fn(rd.r, fid_by_name(rd, "useTabBarLayout") - 22, data)   # the tab bar module function (24934)
    assert any(x[2] == "CreateClosure" and int.from_bytes(mod.original[x[0] + 3:x[0] + 5], "little") == tab_id for x in mod.ins)
    k = next(k for k, x in enumerate(mod.ins) if x[2] == "CreateClosure" and int.from_bytes(mod.original[x[0] + 3:x[0] + 5], "little") == tab_id)
    a, b = mod.ins[k][0], mod.ins[k + 1][1]
    old_cl = bytes(mod.original[a:b])
    new_cl = bytearray(old_cl); new_cl[3:5] = zoo_id.to_bytes(2, "little")
    return (zoo.original, body), (old_hdr, new_hdr), (old_cl, bytes(new_cl))


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
    # the list container gets the dark surface, the "My Content" title loses its own band
    box_id = sid(rd, "Box")
    k16 = next(k for k, (a, b, n, _) in enumerate(L)
               if n == "PutNewOwnByIdShort" and o[a + 1] == 16 and o[a + 3] == sid(rd, "flexDirection"))
    insert[L[k16 + 1][0]] = (ins("LoadConstString", 22, u16(sid(rd, "secondary")))
                             + ins("PutNewOwnById", 16, 22, u16(sid(rd, "background"))))
    k22 = next(k for k in range(k16, len(L)) if L[k][2] == "NewObjectWithBufferLong" and o[L[k][0] + 1] == 22)
    insert[L[k22 + 1][0]] = ins("PutById", 22, 3, 0, u16(sid(rd, "background")))
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


def circular_progress(rd, data):
    """CircularProgress (Steam Guard code ring): a 4dp progress arc with round ends over a thin dashed track,
    as in the Material 3 concept. Room: Math is read once into r16, HermesInternal.concat is reused from r17."""
    f = Fn(rd.r, fid_by_name(rd, "CircularProgress", 531), data)
    o, L = f.original, f.ins
    math, concat, hi = sid(rd, "Math"), sid(rd, "concat"), sid(rd, "HermesInternal")
    delete, insert = [], {}
    # stroke width 10 -> 4 (also the radius inset)
    k = next(k for k, x in enumerate(L) if x[2] == "LoadConstUInt8" and o[x[0] + 1] == 10 and o[x[0] + 2] == 10)
    delete.append((L[k][0], L[k][1])); insert[L[k][1]] = ins("LoadConstUInt8", 10, 4)
    # Math: keep it in r16
    first = True
    for a, b, n, _ in L:
        if n == "TryGetById" and int.from_bytes(o[a + 4:a + 6], "little") == math:
            if first:
                delete.append((a, b)); insert[b] = bytes(o[a:a + 1]) + bytes([16]) + bytes(o[a + 2:b]) + ins("Mov", o[a + 1], 16)
                first = False
            else:
                delete.append((a, b)); insert[b] = ins("Mov", o[a + 1], 16)
    # second HermesInternal.concat -> r17 (still holds concat from the path string)
    his = [k for k, x in enumerate(L) if x[2] == "TryGetById" and int.from_bytes(o[x[0] + 4:x[0] + 6], "little") == hi]
    assert len(his) == 2
    k = his[1]
    assert L[k + 1][2] == "GetByIdShort" and o[L[k + 1][0] + 4] == concat
    delete.append((L[k][0], L[k + 1][1])); insert[L[k + 1][1]] = ins("Mov", o[L[k + 1][0] + 1], 17)
    # track: thin dashed line
    k = next(k for k, x in enumerate(L) if x[2] == "PutById" and int.from_bytes(o[x[0] + 4:x[0] + 6], "little") == sid(rd, "d")
             and o[x[0] + 1] == 7)
    insert[L[k][1]] = (ins("LoadConstUInt8", 4, 3) + ins("PutById", 7, 4, 0, u16(sid(rd, "strokeWidth")))
                       + ins("LoadConstStringLongIndex", 4, u32(sid(rd, "12 12")))
                       + ins("PutNewOwnById", 7, 4, u16(sid(rd, "strokeDasharray"))))
    # progress arc: round ends
    k = next(k for k, x in enumerate(L) if x[2] == "LoadConstString" and int.from_bytes(o[x[0] + 2:x[0] + 4], "little") == sid(rd, "square"))
    delete.append((L[k][0], L[k][1])); insert[L[k][1]] = ins("LoadConstString", o[L[k][0] + 1], u16(sid(rd, "round")))
    return f, f.rewrite(delete=delete, insert=insert)


def circular_timer(rd, data):
    """CircularTimer: the track uses the neutral (secondary container) background instead of the near-black one."""
    f = Fn(rd.r, fid_by_name(rd, "CircularTimer", 179), data)
    body = bytearray(f.original)
    sec = sid(rd, "secondary").to_bytes(2, "little")
    hits = [a for a, b, n, _ in f.ins if n == "GetById" and bytes(f.original[a + 4:a + 6]) == sec]
    assert len(hits) == 1
    body[hits[0] + 4:hits[0] + 6] = sid(rd, "neutral").to_bytes(2, "little")
    return f, bytes(body)


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
    for name, (old, new) in zip(("TAB_BODY", "TAB_HEADER", "TAB_CLOSURE"), tab_bar_relocated(rd, data)):
        print(f"{name}_ORIGINAL = \"{old.hex()}\"")
        print(f"{name}_PATCHED = \"{new.hex()}\"")
    for name, gen in (("TAB_ITEM", tab_bar_item), ("LINK_SECTION", link_section), ("HEADER_LINK", header_link), ("MAIN_MENU", main_menu), ("LIST_ITEM", list_item), ("PERSONA_MENU", persona_menu),
                      ("GUARD_RING", circular_progress), ("GUARD_TIMER", circular_timer)):
        f, body = gen(rd, data)
        print(f"{name}_ORIGINAL = \"{f.original.hex()}\"")
        print(f"{name}_PATCHED = \"{body.hex()}\"")
