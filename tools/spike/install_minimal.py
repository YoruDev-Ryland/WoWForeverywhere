#!/usr/bin/env python3
"""install_minimal.py <product> <arch> <outdir>

Writes a launcher-less minimal WoW install from Blizzard's public TACT CDN:
  .build.info, .flavor.info (<flavor>/), Data/config/**, Data/indices/*.index,
  and the install-manifest files for Windows + <arch> + enUS.
"""
import hashlib
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import tact  # noqa: E402

FLAVORS = {"wowf": "_forever_", "wow_classic_beta": "_classic_beta_", "wow": "_retail_",
           "wow_classic": "_classic_", "wow_classic_era": "_classic_era_", "wowt": "_ptr_"}
BUILD_INFO_HEADER = ("Branch!STRING:0|Active!DEC:1|Build Key!HEX:16|CDN Key!HEX:16|Install Key!HEX:16|"
                     "IM Size!DEC:4|CDN Path!STRING:0|CDN Hosts!STRING:0|CDN Servers!STRING:0|Tags!STRING:0|"
                     "Armadillo!STRING:0|Last Activated!STRING:0|Version!STRING:0|KeyRing!HEX:16|Product!STRING:0")

total = 0


def log(name, n):
    global total
    total += n
    print(f"{n:>12,}  {name}", flush=True)


def write(path: Path, data: bytes, label=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    log(label or str(path), len(data))


_dirs = {}


def ci_path(name):
    """Install-manifest names mix case (UTILS\\ vs Utils\\); keep the first-seen casing per dir."""
    parts = name.split("\\")
    for i in range(len(parts) - 1):
        key = "/".join(parts[:i + 1]).lower()
        parts[i] = _dirs.setdefault(key, parts[i])
    return "/".join(parts)


def main():
    product, arch, out = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    region = "us"
    flavor = FLAVORS[product]
    ver = next(r for r in tact.psv(tact.http(f"{tact.PATCH}/{product}/versions").decode()) if r["Region"] == region)
    cdn = next(r for r in tact.psv(tact.http(f"{tact.PATCH}/{product}/cdns").decode()) if r["Name"] == region)
    tact.HOSTS = cdn["Hosts"].split()
    tact.CDN_PATH = cdn["Path"]
    bkey, ckey_cfg = ver["BuildConfig"], ver["CDNConfig"]
    print(f"{product} {ver['VersionsName']} build={bkey} cdn={ckey_cfg} keyring={ver.get('KeyRing')}")

    # --- configs
    bc_raw = tact.fetch("config", bkey)
    cc_raw = tact.fetch("config", ckey_cfg)
    write(out / "Data/config" / bkey[:2] / bkey[2:4] / bkey, bc_raw)
    write(out / "Data/config" / ckey_cfg[:2] / ckey_cfg[2:4] / ckey_cfg, cc_raw)
    if ver.get("KeyRing"):
        k = ver["KeyRing"]
        write(out / "Data/config" / k[:2] / k[2:4] / k, tact.fetch("config", k))
    bc, cc = tact.read_config(bc_raw.decode()), tact.read_config(cc_raw.decode())

    # --- install manifest
    im_raw = tact.fetch("data", bc["install"][1])
    tags, entries = tact.parse_install(tact.blte_decode(im_raw))
    print("install tags:", ", ".join(f"{n}({t})" for n, t, _ in tags))
    wanted = {"Windows", arch, "enUS", "US"}
    sel = tact.select_install(tags, entries, wanted)
    print(f"install manifest: {len(entries)} entries, {len(sel)} selected for {sorted(wanted)}")

    # --- encoding (needed for ckey->ekey)
    enc = tact.blte_decode(tact.fetch("data", bc["encoding"][1]))
    ekeys = tact.encoding_lookup(enc, {e[1] for e in sel})

    # --- archive indices (all of them: archives + patch archives + groups/file-index)
    # Only data-archive indices + file-index live under data/. archive-group is NOT on the CDN
    # (the launcher builds it locally); patch-archive indices live under /patch/ and are skipped.
    idx_names = list(cc.get("archives", [])) + [cc[k][0] for k in ("file-index",) if k in cc]
    idxdir = out / "Data/indices"
    idxdir.mkdir(parents=True, exist_ok=True)

    def get_idx(h):
        d = tact.fetch("data", h + ".index")
        if d is None:
            return h, None
        (idxdir / f"{h}.index").write_bytes(d)
        return h, d

    idx_bytes, archive_map = 0, {}
    archives_set = set(cc.get("archives", []))
    with ThreadPoolExecutor(32) as ex:
        for h, d in ex.map(get_idx, idx_names):
            if d is None:
                print("  missing index", h)
                continue
            idx_bytes += len(d)
            if h in archives_set:
                for ek, (sz, off) in tact.parse_archive_index(d).items():
                    archive_map[ek] = (h, sz, off)
    log(f"Data/indices/ ({len(idx_names)} .index files)", idx_bytes)

    # --- install files
    def get_file(e):
        name, ckey, size, _ = e
        ek = ekeys[ckey][0]
        enc_data = tact.fetch("data", ek, cache=False)
        if enc_data is None:
            a, sz, off = archive_map[ek]
            enc_data = tact.fetch("data", a, (off, sz))
        content = tact.blte_decode(enc_data)
        if hashlib.md5(content).hexdigest() != ckey:
            raise SystemExit(f"md5 mismatch {name}")
        return name, content

    fbytes = 0
    with ThreadPoolExecutor(16) as ex:
        for name, content in ex.map(get_file, sel):
            p = out / flavor / ci_path(name)
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(content)
            fbytes += len(content)
            print(f"   {len(content):>12,}  {name}", flush=True)
    log(f"install-manifest files ({len(sel)})", fbytes)

    # --- .build.info / .flavor.info
    tags_s = f"Windows {arch} US? acct-USA? geoip-US? enUS speech?:Windows {arch} US? acct-USA? geoip-US? enUS text?"
    row = ["us", "1", bkey, ckey_cfg, bc["install"][1], bc.get("install-size", ["0", "0"])[1], cdn["Path"], cdn["Hosts"],
           cdn["Servers"], tags_s, "", "", ver["VersionsName"], ver.get("KeyRing", ""), product]
    write(out / ".build.info", (BUILD_INFO_HEADER + "\n" + "|".join(row) + "\n").encode())
    write(out / flavor / ".flavor.info", f"Product Flavor!STRING:0\n{product}\n".encode())
    # Without portal/textLocale the client blocks on a modal "choose region/language" dialog.
    write(out / flavor / "WTF/Config.wtf", b'SET portal "US"\nSET textLocale "enUS"\nSET audioLocale "enUS"\n')
    print(f"TOTAL written: {total:,} bytes")


if __name__ == "__main__":
    main()
