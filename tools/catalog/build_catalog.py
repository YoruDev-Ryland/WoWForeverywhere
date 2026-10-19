#!/usr/bin/env python3
"""Builds catalog.json for the app's GitHub addon catalog.

Reads catalog/sources.json, looks up each repo's latest GitHub release and its packager
release.json, and writes the Forever/Classic files with download links. Run by the nightly
"Addon catalog" workflow; set GITHUB_TOKEN for the 5000/hour API limit.

    python3 tools/catalog/build_catalog.py catalog/sources.json out/catalog.json
"""
import datetime
import json
import os
import sys
import urllib.error
import urllib.request

API = "https://api.github.com"
FLAVORS = {"forever": "forever", "camelot": "forever", "classic": "classic", "vanilla": "classic"}


def get(url, accept="application/vnd.github+json"):
    req = urllib.request.Request(url, headers={"Accept": accept, "User-Agent": "WoWForeverywhere-catalog"})
    token = os.environ.get("GITHUB_TOKEN")
    if token and url.startswith(API):
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def files_for(release):
    """Maps 'forever'/'classic' to the release asset that serves it."""
    assets = {a["name"]: a for a in release.get("assets", [])}
    files = {}
    meta_asset = assets.get("release.json")
    if meta_asset:
        meta = get(meta_asset["browser_download_url"], accept="application/octet-stream")
        for item in meta.get("releases", []):
            asset = assets.get(item.get("filename"))
            if item.get("nolib") or not asset:
                continue
            for m in item.get("metadata", []):
                flavor = FLAVORS.get(str(m.get("flavor", "")).lower())
                if flavor and flavor not in files:
                    files[flavor] = (asset, m.get("interface"))
    else:
        for name, asset in assets.items():
            lower = name.lower()
            if not lower.endswith(".zip") or "-nolib" in lower:
                continue
            for suffix, flavor in FLAVORS.items():
                if lower.endswith(f"-{suffix}.zip") or lower.endswith(f"_{suffix}.zip"):
                    files.setdefault(flavor, (asset, None))
    return {
        flavor: {
            "fileId": str(asset["id"]),
            "version": release.get("tag_name") or release.get("name") or asset["name"],
            "url": asset["browser_download_url"],
            "interface": interface,
        }
        for flavor, (asset, interface) in files.items()
    }


def build(sources):
    out = []
    for entry in sources["addons"]:
        repo = entry["repo"]
        try:
            info = get(f"{API}/repos/{repo}")
            release = get(f"{API}/repos/{repo}/releases/latest")
            files = files_for(release)
        except (urllib.error.URLError, ValueError, KeyError) as e:
            print(f"skip {repo}: {e}", file=sys.stderr)
            continue
        if not files:
            print(f"skip {repo}: no Forever or Classic files", file=sys.stderr)
            continue
        owner = repo.split("/")[0]
        out.append({
            "repo": repo,
            "name": entry.get("name") or info.get("name") or repo.split("/")[1],
            "summary": entry.get("summary") or info.get("description") or "",
            "category": entry.get("category"),
            "author": owner,
            "icon": info.get("owner", {}).get("avatar_url") or f"https://github.com/{owner}.png",
            "website": info.get("html_url") or f"https://github.com/{repo}",
            "stars": info.get("stargazers_count", 0),
            "downloads": sum(a.get("download_count", 0) for a in release.get("assets", [])),
            "updated": release.get("published_at"),
            "requires": entry.get("requires", []),
            "files": files,
        })
        print(f"ok   {repo}: {', '.join(sorted(files))}", file=sys.stderr)
    return {
        "generated": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "addons": out,
    }


def main():
    src, dest = sys.argv[1], sys.argv[2]
    with open(src) as f:
        catalog = build(json.load(f))
    os.makedirs(os.path.dirname(dest) or ".", exist_ok=True)
    with open(dest, "w") as f:
        json.dump(catalog, f, indent=1)
        f.write("\n")
    print(f"{len(catalog['addons'])} addons -> {dest}", file=sys.stderr)


if __name__ == "__main__":
    main()
