#!/usr/bin/env python3
"""Verify the bundled on-device toolchain.

The app decides whether to unpack the toolchain by hashing assets/hosttools/VERSION
and comparing it with the value it wrote on the previous launch. That only works if
VERSION actually tracks the content, so this script enforces the contract:

    VERSION == sha1("\\n".join(sorted(f"{relative_path}:{file_size}")))
               over every file under assets/hosttools except VERSION itself
"""
import hashlib
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
HT = os.path.join(ROOT, "app", "src", "main", "assets", "hosttools")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")

REQUIRED = [
    "VERSION",
    "applet-list.txt",
    "bin/netrun",
    "static/busybox",
    "static/rg",
    "static/jq",
    "static/fzf",
    "lib/libpython3.12.so.1.0",
    "lib/libexpat.so.1",
]

failures = []


def fail(msg):
    failures.append(msg)


def fingerprint():
    rows = []
    for dirpath, _dirnames, filenames in os.walk(HT):
        for name in filenames:
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, HT).replace(os.sep, "/")
            if rel == "VERSION":
                continue
            rows.append("%s:%d" % (rel, os.path.getsize(full)))
    return hashlib.sha1("\n".join(sorted(rows)).encode()).hexdigest(), len(rows)


if not os.path.isdir(HT):
    fail("assets/hosttools is missing")
else:
    for rel in REQUIRED:
        if not os.path.isfile(os.path.join(HT, rel)):
            fail("missing required file: assets/hosttools/%s" % rel)

    if not failures:
        expected, count = fingerprint()
        with open(os.path.join(HT, "VERSION"), encoding="utf-8") as fh:
            actual = fh.read().strip()
        if actual != expected:
            fail(
                "VERSION is stale: file says %s, content hashes to %s (%d files).\n"
                "  Regenerate it with: python3 .github/scripts/gen_hosttools_version.py"
                % (actual, expected, count)
            )
        else:
            print("VERSION ok: %s over %d files" % (actual, count))

# Placeholder directories for names that start with "_": the Android packaging step
# drops directories whose name begins with an underscore, so they are shipped as "x__".
bad = []
for dirpath, dirnames, _filenames in os.walk(ASSETS):
    for name in dirnames:
        if name.startswith("_"):
            bad.append(os.path.relpath(os.path.join(dirpath, name), ROOT))
if bad:
    fail("directories starting with '_' will be dropped by the packaging step:\n  " + "\n  ".join(bad))

if failures:
    print("FAILED", file=sys.stderr)
    for item in failures:
        print(" - " + item, file=sys.stderr)
    sys.exit(1)

print("hosttools checks passed")
