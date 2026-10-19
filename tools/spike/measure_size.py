#!/usr/bin/env python3
"""measure_size.py <product> : sum download-manifest encoded sizes per platform/arch/locale."""
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import tact  # noqa: E402


def parse_download(d):
    assert d[:2] == b"DL"
    ver, ek_size, has_ck = d[2], d[3], d[4]
    count = struct.unpack(">I", d[5:9])[0]
    tag_count = struct.unpack(">H", d[9:11])[0]
    pos, flag_size = 11, 0
    if ver >= 2:
        flag_size = d[pos]; pos += 1
    if ver >= 3:
        pos += 4
    esz = ek_size + 5 + 1 + (4 if has_ck else 0) + flag_size
    sizes, prios = [], []
    for i in range(count):
        p = pos + i * esz
        sizes.append(int.from_bytes(d[p + ek_size:p + ek_size + 5], "big"))
        prios.append(d[p + ek_size + 5])
    pos += count * esz
    mask_len = (count + 7) // 8
    tags = []
    for _ in range(tag_count):
        end = d.index(b"\0", pos)
        name = d[pos:end].decode()
        ttype = struct.unpack(">H", d[end + 1:end + 3])[0]
        pos = end + 3
        tags.append((name, ttype, d[pos:pos + mask_len]))
        pos += mask_len
    return ver, sizes, prios, tags


def main():
    product = sys.argv[1]
    ver = next(r for r in tact.psv(tact.http(f"{tact.PATCH}/{product}/versions").decode()) if r["Region"] == "us")
    bc = tact.read_config(tact.fetch("config", ver["BuildConfig"]).decode())
    d = tact.blte_decode(tact.fetch("data", bc["download"][1]))
    v, sizes, prios, tags = parse_download(d)
    print(f"{product} {ver['VersionsName']}: download manifest v{v}, {len(sizes)} entries, "
          f"all = {sum(sizes) / 1e9:.2f} GB; tags: {' '.join(n for n, _, _ in tags)}")
    by_type = {}
    for n, t, m in tags:
        by_type.setdefault(t, []).append((n, m))
    for arch in ("x86_64", "arm64"):
        wanted = {"Windows", arch, "enUS", "US"}
        req = [[m for n, m in lst if n in wanted] for lst in by_type.values() if any(n in wanted for n, _ in lst)]
        total = minprio = 0
        n_sel = 0
        for i, s in enumerate(sizes):
            if all(any(m[i // 8] & (0x80 >> (i % 8)) for m in ms) for ms in req):
                total += s
                n_sel += 1
                if prios[i] == 0:
                    minprio += s
        print(f"  Windows+{arch}+enUS+US: {n_sel} files, {total:,} bytes ({total / 1e9:.2f} GB) encoded; "
              f"priority-0 subset {minprio / 1e9:.2f} GB")
    prio_hist = {}
    for s, p in zip(sizes, prios):
        prio_hist[p] = prio_hist.get(p, 0) + s
    print("  bytes by priority (all tags):", {k: f"{v / 1e9:.2f}GB" for k, v in sorted(prio_hist.items())})
    print(f"  build config sizes: install-size={bc.get('install-size')} download-size={bc.get('download-size')} "
          f"encoding-size={bc.get('encoding-size')}")


if __name__ == "__main__" and len(sys.argv) == 2:
    main()


def breakdown(product="wowf", arch="arm64"):
    ver = next(r for r in tact.psv(tact.http(f"{tact.PATCH}/{product}/versions").decode()) if r["Region"] == "us")
    bc = tact.read_config(tact.fetch("config", ver["BuildConfig"]).decode())
    v, sizes, prios, tags = parse_download(tact.blte_decode(tact.fetch("data", bc["download"][1])))
    tm = {n: m for n, _, m in tags}
    has = lambda n, i: tm[n][i // 8] & (0x80 >> (i % 8))
    base = [i for i in range(len(sizes)) if has("Windows", i) and has(arch, i) and has("enUS", i) and has("US", i)]
    opt = ["Alternate", "HighRes", "Talebound"]
    for combo in ([], ["HighRes"], ["Alternate"], ["Talebound"], opt):
        tot = sum(sizes[i] for i in base if not any(has(o, i) for o in combo))
        print(f"  {arch} excluding {combo or 'nothing'}: {tot / 1e9:.2f} GB")
    for o in opt:
        print(f"  {o}: {sum(sizes[i] for i in base if has(o, i)) / 1e9:.2f} GB tagged")
    for p in sorted(set(prios)):
        print(f"  prio {p} (base, no optional): {sum(sizes[i] for i in base if prios[i] == p and not any(has(o, i) for o in opt)) / 1e9:.2f} GB")


if __name__ == "__main__" and len(sys.argv) > 2 and sys.argv[2] == "breakdown":
    breakdown(sys.argv[1], sys.argv[3] if len(sys.argv) > 3 else "arm64")
