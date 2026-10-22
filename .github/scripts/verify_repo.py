#!/usr/bin/env python3
"""Repository hygiene checks that do not need the Android SDK."""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
failures = []


def fail(msg):
    failures.append(msg)


REQUIRED_DOCS = [
    "README.md",
    "README_CN.md",
    "LICENSE",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "SECURITY.md",
    "CHANGELOG.md",
    "AGENTS.md",
    "CLAUDE.md",
    ".editorconfig",
    ".gitattributes",
    ".github/PULL_REQUEST_TEMPLATE.md",
    ".github/ISSUE_TEMPLATE/bug_report.yml",
    ".github/ISSUE_TEMPLATE/feature_request.yml",
]
for rel in REQUIRED_DOCS:
    if not os.path.isfile(os.path.join(ROOT, rel)):
        fail("missing document: %s" % rel)

# The build requires the wrapper to be executable.
gradlew = os.path.join(ROOT, "gradlew")
if os.path.isfile(gradlew) and not os.access(gradlew, os.X_OK):
    fail("gradlew is not executable (run: chmod +x gradlew)")

# Secrets and machine-local configuration must never be committed.
FORBIDDEN_SUFFIX = (".keystore", ".jks", ".p12", ".pem", ".key", ".apk", ".aab")
SKIP_DIRS = {"hosttools", "toolchain", "build", ".git", ".gradle"}
leaks = []
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
    for name in filenames:
        if name.endswith(FORBIDDEN_SUFFIX):
            leaks.append(os.path.relpath(os.path.join(dirpath, name), ROOT))
if leaks:
    fail("committed secrets or build output:\n  " + "\n  ".join(leaks))

# Personal machine paths (not the product path /data/local/aitools, which is intentional).
PATH_PATTERNS = [re.compile(r"/Users/[A-Za-z0-9_.-]+/"), re.compile(r"/home/[A-Za-z0-9_.-]+/")]
SCAN_SUFFIX = (".kt", ".kts", ".xml", ".pro", ".gradle", ".properties")
hits = []
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
    for name in filenames:
        if not name.endswith(SCAN_SUFFIX):
            continue
        full = os.path.join(dirpath, name)
        try:
            with open(full, encoding="utf-8") as fh:
                for lineno, line in enumerate(fh, 1):
                    for pat in PATH_PATTERNS:
                        if pat.search(line):
                            hits.append("%s:%d: %s" % (os.path.relpath(full, ROOT), lineno, line.strip()[:90]))
        except (UnicodeDecodeError, OSError):
            continue
if hits:
    fail("machine-local paths in sources:\n  " + "\n  ".join(hits[:20]))

# Kotlin sources use spaces, never tabs.
tabbed = []
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
    for name in filenames:
        if not name.endswith((".kt", ".kts")):
            continue
        full = os.path.join(dirpath, name)
        try:
            with open(full, encoding="utf-8") as fh:
                for lineno, line in enumerate(fh, 1):
                    if line.lstrip("\n").startswith("\t") or re.match(r"^\s*\t", line):
                        tabbed.append("%s:%d" % (os.path.relpath(full, ROOT), lineno))
                        break
        except (UnicodeDecodeError, OSError):
            continue
if tabbed:
    fail("tab indentation in Kotlin sources:\n  " + "\n  ".join(tabbed[:20]))

if failures:
    print("FAILED", file=sys.stderr)
    for item in failures:
        print(" - " + item, file=sys.stderr)
    sys.exit(1)

print("repository checks passed")
