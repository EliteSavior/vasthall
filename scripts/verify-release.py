#!/usr/bin/env python3
"""Verify that README.md accurately describes the published Vast Hall APK.

This repository is a sideload drop: its only content is README.md, which
documents an Android APK published as a GitHub release asset. Drift between the
README's declared metadata (tag, file name, byte size, md5) and the real asset
is the primary failure mode this repo can suffer, so this script checks them
against each other and against the APK's own contents.

Uses only the Python standard library so it runs on the default image with no
dependency installation.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import sys
import tempfile
import urllib.request
import zipfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
README = os.path.join(REPO_ROOT, "README.md")


class Checks:
    def __init__(self) -> None:
        self.failed = False

    def ok(self, label: str, detail: str = "") -> None:
        print(f"  [PASS] {label}" + (f" - {detail}" if detail else ""))

    def bad(self, label: str, detail: str = "") -> None:
        self.failed = True
        print(f"  [FAIL] {label}" + (f" - {detail}" if detail else ""))

    def check(self, cond: bool, label: str, detail: str = "") -> bool:
        (self.ok if cond else self.bad)(label, detail)
        return cond


def parse_readme(text: str) -> dict:
    """Extract the declared metadata from README.md."""
    meta: dict = {}

    m = re.search(r"Tag:\s*`([^`]+)`", text)
    meta["tag"] = m.group(1) if m else None

    m = re.search(r"File:\s*`([^`]+)`\s*\((\d+)\s*bytes,\s*md5\s*`([0-9a-fA-F]+)`\)", text)
    if m:
        meta["file"] = m.group(1)
        meta["size"] = int(m.group(2))
        meta["md5"] = m.group(3).lower()
    else:
        meta["file"] = meta["size"] = meta["md5"] = None

    m = re.search(r"Direct:\s*(https?://\S+)", text)
    meta["direct_url"] = m.group(1) if m else None

    m = re.search(r"`(com\.[a-z0-9_.]+)`", text)
    meta["package"] = m.group(1) if m else None

    return meta


def download(url: str, dest: str) -> None:
    print(f"  Downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "vasthall-verify/1.0"})
    with urllib.request.urlopen(req) as resp, open(dest, "wb") as fh:  # noqa: S310
        while True:
            chunk = resp.read(65536)
            if not chunk:
                break
            fh.write(chunk)


def md5_of(path: str) -> str:
    h = hashlib.md5()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def manifest_strings(apk_path: str) -> list[str]:
    """Return printable strings from the binary AndroidManifest.xml."""
    with zipfile.ZipFile(apk_path) as zf:
        data = zf.read("AndroidManifest.xml")
    text = data.decode("utf-16-le", "ignore")
    return re.findall(r"[\x20-\x7e]{3,}", text)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--apk", help="Path to a local APK to verify instead of downloading.")
    ap.add_argument(
        "--offline",
        action="store_true",
        help="Skip download/APK checks; only validate README internal consistency.",
    )
    args = ap.parse_args()

    with open(README, encoding="utf-8") as fh:
        text = fh.read()

    meta = parse_readme(text)
    checks = Checks()

    print("README-declared metadata:")
    for key in ("package", "tag", "file", "size", "md5", "direct_url"):
        print(f"  {key}: {meta.get(key)}")
    print()

    print("README consistency:")
    checks.check(bool(meta["tag"]), "Tag present in README")
    checks.check(bool(meta["file"]), "File/size/md5 line present in README")
    checks.check(bool(meta["direct_url"]), "Direct download URL present")
    checks.check(bool(meta["package"]), "Package id present", meta.get("package") or "")
    if meta["tag"] and meta["direct_url"]:
        checks.check(
            f"/{meta['tag']}/" in meta["direct_url"],
            "Direct URL references the declared tag",
        )
    if meta["file"] and meta["direct_url"]:
        checks.check(
            meta["direct_url"].endswith(meta["file"]),
            "Direct URL references the declared file name",
        )
    print()

    if args.offline:
        print("Offline mode: skipping asset download and APK inspection.")
        return 1 if checks.failed else 0

    tmpdir = tempfile.mkdtemp(prefix="vasthall-verify-")
    apk_path = args.apk
    if not apk_path:
        if not meta["direct_url"]:
            checks.bad("Cannot download: no Direct URL in README")
            return 1
        apk_path = os.path.join(tmpdir, meta["file"] or "asset.apk")
        try:
            download(meta["direct_url"], apk_path)
        except Exception as exc:  # noqa: BLE001
            checks.bad("Download release asset", str(exc))
            return 1

    print("\nReleased asset vs README:")
    actual_size = os.path.getsize(apk_path)
    checks.check(
        actual_size == meta["size"],
        "Byte size matches README",
        f"actual={actual_size} declared={meta['size']}",
    )
    actual_md5 = md5_of(apk_path)
    checks.check(
        actual_md5 == meta["md5"],
        "md5 matches README",
        f"actual={actual_md5} declared={meta['md5']}",
    )

    print("\nAPK contents (FOSS flavor):")
    try:
        strings = manifest_strings(apk_path)
    except Exception as exc:  # noqa: BLE001
        checks.bad("Read AndroidManifest.xml", str(exc))
        return 1

    if meta["package"]:
        checks.check(
            any(meta["package"] == s for s in strings),
            "Manifest declares README package id",
            meta["package"],
        )
    checks.check(
        not any("android.permission.INTERNET" in s for s in strings),
        "No INTERNET permission (offline FOSS build)",
    )
    banned = ("firebase", "com.google.android.gms", "gms", "com.android.vending")
    hits = [s for s in strings if any(b in s.lower() for b in banned)]
    checks.check(not hits, "No Play/GMS/Firebase references in manifest", ", ".join(hits[:5]))

    print()
    if checks.failed:
        print("RESULT: FAILED - README and released APK are out of sync.")
        return 1
    print("RESULT: OK - README accurately describes the released APK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
