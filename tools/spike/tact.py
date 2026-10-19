"""Minimal TACT helpers (adapted from tools/download_wow_arm64.py and WowClientDownloader.kt)."""
import os
import struct
import time
import urllib.request
import zlib
from pathlib import Path

PATCH = "http://us.patch.battle.net:1119"
HOSTS = ["level3.blizzard.com", "us.cdn.blizzard.com"]
CDN_PATH = "tpr/wow"
CACHE = Path(os.environ.get("TACT_CACHE", Path(__file__).resolve().parent.parent / "cache"))


def http(url, byte_range=None, tries=4):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "spike"})
            if byte_range:
                off, size = byte_range
                req.add_header("Range", f"bytes={off}-{off + size - 1}")
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code in (403, 404):
                return None
            err = e
        except Exception as e:  # noqa
            err = e
        time.sleep(1 + attempt)
    raise IOError(f"{url}: {err}")


def fetch(kind, key, byte_range=None, cache=True):
    """kind: config|data. Caches whole-file fetches on disk."""
    cp = CACHE / kind / key
    if byte_range is None and cache and cp.exists():
        return cp.read_bytes()
    for host in HOSTS:
        data = http(f"http://{host}/{CDN_PATH}/{kind}/{key[:2]}/{key[2:4]}/{key}", byte_range)
        if data is not None:
            if byte_range is None and cache:
                cp.parent.mkdir(parents=True, exist_ok=True)
                cp.write_bytes(data)
            return data
    return None


def psv(text):
    lines = [l for l in text.splitlines() if l.strip() and not l.startswith("#")]
    header = [h.split("!")[0] for h in lines[0].split("|")]
    return [dict(zip(header, l.split("|"))) for l in lines[1:]]


def read_config(text):
    return {k.strip(): v.strip().split() for k, v in
            (line.split("=", 1) for line in text.splitlines() if "=" in line and not line.startswith("#"))}


def blte_decode(data):
    if data[:4] != b"BLTE":
        raise ValueError("not BLTE")
    header_size = struct.unpack(">I", data[4:8])[0]
    if header_size == 0:
        return decode_chunk(data[8:])
    count = int.from_bytes(data[9:12], "big")
    pos, offset, out = 12, header_size, bytearray()
    for _ in range(count):
        comp_size, _d = struct.unpack(">II", data[pos:pos + 8])
        pos += 24
        out += decode_chunk(data[offset:offset + comp_size])
        offset += comp_size
    return bytes(out)


def decode_chunk(chunk):
    mode, body = chunk[:1], chunk[1:]
    if mode == b"N":
        return body
    if mode == b"Z":
        return zlib.decompress(body)
    if mode == b"F":
        return blte_decode(body)
    if mode == b"E":
        raise ValueError("encrypted BLTE chunk")
    raise ValueError(f"unsupported BLTE chunk mode {mode!r}")


def parse_install(data):
    """Returns (tags: list[(name,type,mask)], entries: list[(name, ckey, size, set(tags))])."""
    if data[:2] != b"IN":
        raise ValueError("bad install manifest")
    hash_size = data[3]
    tag_count, entry_count = struct.unpack(">HI", data[4:10])
    mask_len = (entry_count + 7) // 8
    pos, tags = 10, []
    for _ in range(tag_count):
        end = data.index(b"\0", pos)
        name = data[pos:end].decode()
        ttype = struct.unpack(">H", data[end + 1:end + 3])[0]
        pos = end + 3
        tags.append((name, ttype, data[pos:pos + mask_len]))
        pos += mask_len
    entries = []
    for i in range(entry_count):
        end = data.index(b"\0", pos)
        name = data[pos:end].decode()
        pos = end + 1
        ckey = data[pos:pos + hash_size].hex()
        size = struct.unpack(">I", data[pos + hash_size:pos + hash_size + 4])[0]
        pos += hash_size + 4
        et = {t for t, _, m in tags if m[i // 8] & (0x80 >> (i % 8))}
        entries.append((name, ckey, size, et))
    return tags, entries


def select_install(tags, entries, wanted):
    """Standard TACT semantics: for every tag *type* that contains a wanted tag,
    the entry must carry at least one wanted tag of that type."""
    by_type = {}
    for name, ttype, _ in tags:
        by_type.setdefault(ttype, set()).add(name)
    req = [types & wanted for types in by_type.values() if types & wanted]
    return [e for e in entries if all(e[3] & r for r in req)]


def encoding_lookup(data, wanted):
    if data[:2] != b"EN":
        raise ValueError("bad encoding file")
    ckey_size, ekey_size = data[3], data[4]
    page_kb = struct.unpack(">H", data[5:7])[0]
    page_count = struct.unpack(">I", data[9:13])[0]
    espec_size = struct.unpack(">I", data[18:22])[0]
    pages_start = 22 + espec_size + page_count * (ckey_size + 16)
    found = {}
    for p in range(page_count):
        pos = pages_start + p * page_kb * 1024
        end = pos + page_kb * 1024
        while pos < end:
            key_count = data[pos]
            if key_count == 0:
                break
            ckey = data[pos + 6:pos + 6 + ckey_size].hex()
            if wanted is None or ckey in wanted:
                size = int.from_bytes(data[pos + 1:pos + 6], "big")
                found[ckey] = (data[pos + 6 + ckey_size:pos + 6 + ckey_size + ekey_size].hex(), size)
            pos += 6 + ckey_size + key_count * ekey_size
    return found


def encoding_especs(data):
    """EKey -> encoded size table (second half of encoding file)."""
    ckey_size, ekey_size = data[3], data[4]
    page_kb = struct.unpack(">H", data[5:7])[0]
    page_count = struct.unpack(">I", data[9:13])[0]
    epage_kb = struct.unpack(">H", data[7:9])[0]
    epage_count = struct.unpack(">I", data[13:17])[0]
    espec_size = struct.unpack(">I", data[18:22])[0]
    start = 22 + espec_size + page_count * (ckey_size + 16) + page_count * page_kb * 1024
    start += epage_count * (ekey_size + 16)
    out = {}
    for p in range(epage_count):
        pos = start + p * epage_kb * 1024
        end = pos + epage_kb * 1024
        while pos + ekey_size + 9 <= end:
            ekey = data[pos:pos + ekey_size]
            if ekey == b"\0" * ekey_size:
                break
            size = int.from_bytes(data[pos + ekey_size + 4:pos + ekey_size + 9], "big")
            out[ekey.hex()] = size
            pos += ekey_size + 9
    return out


def parse_archive_index(data):
    """Returns dict ekey(hex,16)->(size, offset). Uses footer for key/size widths."""
    # footer: toc_hash(8) version(1) unk(2) blockKB(1) offBytes(1) sizeBytes(1) keySize(1) hashSize(1) count(4 LE) hash(8)
    f = data[-28:]
    block_kb, off_b, size_b, key_b, _hash_b = f[11], f[12], f[13], f[14], f[15]
    count = struct.unpack("<I", f[16:20])[0]
    esz = key_b + size_b + off_b
    block = block_kb * 1024
    out = {}
    nblocks = (len(data) - 28) // (block + key_b + 8)  # data blocks then TOC
    for b in range(nblocks):
        base = b * block
        for pos in range(base, base + block - esz + 1, esz):
            k = data[pos:pos + key_b]
            if k == b"\0" * key_b:
                break
            size = int.from_bytes(data[pos + key_b:pos + key_b + size_b], "big")
            off = int.from_bytes(data[pos + key_b + size_b:pos + esz], "big") if off_b else 0
            out[k.hex()] = (size, off)
            if len(out) >= count:
                return out
    return out
