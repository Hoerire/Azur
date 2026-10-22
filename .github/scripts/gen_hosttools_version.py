#!/usr/bin/env python3
"""Regenerate assets/hosttools/VERSION from the current content.

Run this after adding, removing or replacing any file under assets/hosttools.
Skipping it means installed apps will not re-unpack the toolchain on upgrade.
"""
import hashlib
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
HT = os.path.join(ROOT, "app", "src", "main", "assets", "hosttools")

rows = []
for dirpath, _dirnames, filenames in os.walk(HT):
    for name in filenames:
        full = os.path.join(dirpath, name)
        rel = os.path.relpath(full, HT).replace(os.sep, "/")
        if rel == "VERSION":
            continue
        rows.append("%s:%d" % (rel, os.path.getsize(full)))

value = hashlib.sha1("\n".join(sorted(rows)).encode()).hexdigest()
target = os.path.join(HT, "VERSION")
old = ""
if os.path.isfile(target):
    with open(target, encoding="utf-8") as fh:
        old = fh.read().strip()
with open(target, "w", encoding="utf-8") as fh:
    fh.write(value + "\n")

print("files: %d" % len(rows))
print("old:   %s" % (old or "(none)"))
print("new:   %s" % value)
