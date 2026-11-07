"""Tiny Hermes v96 bytecode rewriter used to generate the native function bodies in patch_steam.py.

Needs hermes-dec. Instructions are kept as raw bytes; deletions and insertions are applied by
original offset and every relative jump is re-targeted.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from patch_steam import _Reader  # noqa: E402
from hermes_dec.parsers.hbc_bytecode_parser import parse_hbc_bytecode  # noqa: E402

OP = dict(NewObjectWithBufferLong=2, NewObject=3, GetByVal=73, LoadConstUInt8=110, PutOwnByIndex=78, LoadConstInt=111, GetGlobalObject=48, Call1=81, Call2=83, Jmp=142, Mov=8, GetEnvironment=41, LoadFromEnvironment=46, GetByIdShort=54, GetById=55,
          PutById=59, PutNewOwnByIdShort=63, PutNewOwnById=64, Call3=84, Ret=92, LoadConstString=115,
          LoadConstStringLongIndex=116, LoadConstUndefined=118, LoadConstNull=119, JmpTrue=144, JmpFalse=146)


def ins(name, *ops):
    """Encode one instruction; ops are (value, width) pairs or plain bytes (width 1)."""
    b = bytearray([OP[name]])
    for o in ops:
        v, w = o if isinstance(o, tuple) else (o, 1)
        b += (v & ((1 << (8 * w)) - 1)).to_bytes(w, "little")
    return bytes(b)


def u16(v): return (v, 2)
def u32(v): return (v, 4)


class Fn:
    def __init__(self, reader, fid, data):
        h = reader.function_headers[fid]
        self.offset, self.size = h.offset, h.bytecodeSizeInBytes
        self.original = data[self.offset:self.offset + self.size]
        self.ins = [(p.original_pos, p.next_pos, p.inst.name, [o.operand_type.name for o in p.inst.operands])
                    for p in parse_hbc_bytecode(h, reader)]

    def rewrite(self, delete=(), insert=None):
        """delete: [(start, end)] original byte ranges; insert: {original offset: bytes placed before it}."""
        insert = insert or {}
        dead = lambda a: any(s <= a < e for s, e in delete)
        out, newpos = [], {}
        pos = 0
        for a, b, name, ops in self.ins:
            newpos[a] = pos
            if a in insert:
                out.append((None, None, None, insert[a])); pos += len(insert[a])
            if dead(a):
                continue
            out.append((a, name, ops, bytearray(self.original[a:b]))); pos += b - a
        newpos[self.size] = pos
        kept = sorted(k for k in newpos)

        def target(t):
            if dead(t):
                print(f"  note: jump into deleted code at {t:#x} goes to the next kept instruction", file=sys.stderr)
            while dead(t):
                t = next(x for x in kept if x > t)
            return newpos[t]
        body = bytearray()
        for a, name, ops, raw in out:
            if a is not None and ops and ops[0] in ("Addr8", "Addr32"):
                w = 1 if ops[0] == "Addr8" else 4
                rel = int.from_bytes(raw[1:1 + w], "little", signed=True)
                nrel = target(a + rel) - (newpos[a] + len(insert.get(a, b"")))
                raw[1:1 + w] = nrel.to_bytes(w, "little", signed=True)
            body += raw
        if len(body) > self.size:
            raise ValueError(f"body grew: {len(body)} > {self.size}")
        return bytes(body) + bytes(self.size - len(body))


def load(path):
    data = open(path, "rb").read()
    rd = _Reader(data)
    return rd, data


def fid_by_name(rd, name):
    S = rd.r.strings
    l = [f for f, h in enumerate(rd.r.function_headers) if S[h.functionName] == name]
    if len(l) != 1:
        raise LookupError(name)
    return l[0]


def sid(rd, s):
    l = [i for i, x in enumerate(rd.strings) if x == s]
    if len(l) != 1:
        raise LookupError(s)
    return l[0]
