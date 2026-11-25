#!/usr/bin/env python3
"""
ACT5 客户端扩展宿主 —— DumpReport.dll 的无损校验。

对"重建的 DumpReport.dll"与"改动前的 DumpReport.dll"做字节级对照，
并检查硬约束。任何一项 FAIL 都不该部署。

用法:
    python verify_dumpreport.py <new.dll> <reference.dll>
默认:
    new       = ..\\release\\DumpReport.dll
    reference = ..\\..\\..\\..\\game\\client\\DumpReport.dll   (现役原件)
"""
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_NEW = os.path.join(HERE, "release", "DumpReport.dll")
DEFAULT_REF = os.path.join(HERE, "..", "..", "..", "game", "client", "DumpReport.dll")
DEFAULT_REF = os.path.abspath(DEFAULT_REF)

# 改动前的契约（原库 20480 字节；DNF.exe 只按名字导入后两个）
EXPECT_EXPORTS = {
    "ImplicitExceptionHandler": 1,
    "InitStackTraceLibrary": 2,
    "RegisterExceptionCallback": 3,
}
DNF_NEEDS = {"InitStackTraceLibrary", "RegisterExceptionCallback"}
EXPECT_IMPORTS = {"kernel32.dll", "a5.dll"}

fails = []
oks = []


def ok(m):
    oks.append(m)
    print("  PASS  " + m)


def bad(m):
    fails.append(m)
    print("  FAIL  " + m)


def parse(path):
    d = open(path, "rb").read()
    pe = struct.unpack("<I", d[0x3C:0x40])[0]
    nsec = struct.unpack("<H", d[pe + 6:pe + 8])[0]
    opt = pe + 24
    magic = struct.unpack("<H", d[opt:opt + 2])[0]
    dd = opt + (96 if magic == 0x10B else 112)
    dirs = []
    for i in range(16):
        rva, sz = struct.unpack("<II", d[dd + i * 8:dd + i * 8 + 8])
        dirs.append((rva, sz))
    so = opt + struct.unpack("<H", d[pe + 20:pe + 22])[0]
    secs = []
    for i in range(nsec):
        b = d[so + i * 40:so + i * 40 + 40]
        vsz, va, rsz, ra = struct.unpack("<IIII", b[8:24])
        secs.append((va, vsz, ra, rsz))

    def r2o(rva):
        for va, vsz, ra, rsz in secs:
            if va <= rva < va + vsz:
                return ra + (rva - va)
        return None

    return d, dirs, r2o


def exports(d, dirs, r2o):
    """-> {name: ordinal}"""
    rva, sz = dirs[0]
    if not rva:
        return {}
    off = r2o(rva)
    base = struct.unpack("<I", d[off + 16:off + 20])[0]
    nfun, nname = struct.unpack("<II", d[off + 20:off + 28])
    frva = struct.unpack("<I", d[off + 28:off + 32])[0]
    nrva = struct.unpack("<I", d[off + 32:off + 36])[0]
    orva = struct.unpack("<I", d[off + 36:off + 40])[0]
    out = {}
    if nrva and orva:
        no = r2o(nrva)
        oo = r2o(orva)
        for i in range(nname):
            x = struct.unpack("<I", d[no + i * 4:no + i * 4 + 4])[0]
            e = d.index(b"\0", r2o(x))
            nm = d[r2o(x):e].decode("latin1")
            idx = struct.unpack("<H", d[oo + i * 2:oo + i * 2 + 2])[0]
            out[nm] = base + idx
    return out


def imports(d, dirs, r2o):
    rva, sz = dirs[1]
    if not rva:
        return []
    o = r2o(rva)
    out = []
    while True:
        orig, ts, fwd, nrva, ft = struct.unpack("<IIIII", d[o:o + 20])
        if orig == 0 and nrva == 0 and ft == 0:
            break
        e = d.index(b"\0", r2o(nrva))
        out.append(d[r2o(nrva):e].decode("latin1"))
        o += 20
    return out


def main():
    new = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_NEW)
    ref = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else DEFAULT_REF)

    print("new : " + new)
    print("ref : " + ref)
    if not os.path.exists(new):
        raise SystemExit("new not found")
    if not os.path.exists(ref):
        raise SystemExit("reference not found (pass it explicitly)")

    dn, dirsn, r2on = parse(new)
    dr, dirsr, r2or = parse(ref)
    print("  sizes: new=%d  ref=%d" % (len(dn), len(dr)))

    # 1) exports + ordinals identical
    en = exports(dn, dirsn, r2on)
    er = exports(dr, dirsr, r2or)
    if en == er:
        ok("exports identical to reference (name + ordinal): %s"
           % ", ".join("%s@%d" % (k, v) for k, v in sorted(en.items())))
    else:
        bad("exports differ: new=%s ref=%s" % (en, er))

    # 1b) contract
    if en == EXPECT_EXPORTS:
        ok("exports match the documented contract (3 names, ordinals 1/2/3)")
    else:
        bad("exports != contract: %s" % en)

    miss = sorted(DNF_NEEDS - set(en))
    if not miss:
        ok("DNF.exe's 2 imported names present")
    else:
        bad("DNF.exe imports these, missing: %s" % miss)

    # 1c) decorated names
    dec = [n for n in en if n.startswith("_") or "@" in n]
    if not dec:
        ok("no decorated (@stdcall) export names")
    else:
        bad("decorated names: %s" % dec)

    # 2) imports: kernel32 + A5.dll only (this is what keeps it CRT-free)
    imn = [x.lower() for x in imports(dn, dirsn, r2on)]
    imr = [x.lower() for x in imports(dr, dirsr, r2or)]
    if set(imn) == EXPECT_IMPORTS:
        ok("imports are exactly %s" % sorted(imn))
    else:
        bad("imports != %s : %s" % (sorted(EXPECT_IMPORTS), imn))

    if "a5.dll" in imn:
        ok("still imports A5.dll (A5.dll keeps getting pulled in)")
    else:
        bad("A5.dll import LOST -- the client patch would stop loading")

    if set(imn) == set(imr):
        ok("import set identical to reference: %s" % sorted(imr))
    else:
        # not fatal by itself, but worth knowing
        bad("import set differs from reference: new=%s ref=%s" % (sorted(imn), sorted(imr)))

    # 3) arch
    pe = struct.unpack("<I", dn[0x3C:0x40])[0]
    mach = struct.unpack("<H", dn[pe + 4:pe + 6])[0]
    mag = struct.unpack("<H", dn[pe + 24:pe + 26])[0]
    if mach == 0x14C and mag == 0x10B:
        ok("arch = PE32 / i386 (matches DNF.exe)")
    else:
        bad("arch mismatch machine=%s magic=%s" % (hex(mach), hex(mag)))

    # 4) CRT leftovers
    leftover = [n for n in (b"__chkstk", b"_except_handler", b"__security_cookie",
                            b"msvcrt", b"ucrtbase") if n in dn]
    if not leftover:
        ok("no CRT leftovers (no __chkstk / _except_handler / msvcrt / ucrtbase)")
    else:
        bad("CRT leftovers: %s" % leftover)

    # 5) the extension host is really inside
    if b"dll_ext.ini" in dn and b"dll_ext.log" in dn:
        ok("extension host strings present (dll_ext.ini / dll_ext.log)")
    else:
        bad("extension host NOT found in the binary")

    print()
    print("  %d PASS / %d FAIL" % (len(oks), len(fails)))
    if fails:
        print("  RESULT: NOT DEPLOYABLE")
        return 1
    print("  RESULT: DEPLOYABLE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
