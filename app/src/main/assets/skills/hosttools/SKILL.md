---
name: hosttools
description: Bundled host-side command line tools (rg/jq/sqlite3/file/fd/tree/7zz/zip/rsync/curl/xz/zstd/lz4/python3/git/openssl/busybox). Read this first whenever a task involves searching files, parsing JSON, querying a database, identifying a file type, unpacking an archive, or the host lacking python3 or some command - use these directly instead of detouring into the container.
---

# Bundled host tools

```
/data/user/0/com.hoerire.tools/files/hosttools/     <- app private storage
  bin/       commands, directly executable (musl dynamic tools already carry a loader wrapper)
  toolchain/ aapt2 (native Android ELF) + lib/*.so - required for aarch64 container builds;
             bootstrap copies it to env/toolchain
  applets/   busybox symlinks (awk sed grep tar md5sum xargs sort uniq ...)
  libexec/   the real dynamic binaries      lib/  shared libs + python3.12 stdlib + site-packages
  TOOLS.txt  full inventory
```
Already on the default PATH (`/data/adb/ksu/bin` links to bin) - **type the command name**, no full path needed.

## Hard rules
1. Command not found / Permission denied / build failed -> run `sh /data/local/aitools/env-doctor.sh` first
   (**a failed build already runs it; check the log tail**). Missing build components -> `sh /data/local/aitools/bootstrap-env.sh`
   (the provisioner downloads through netrun automatically and runs `apk add openjdk17`, self-heals the aapt2 toolchain).
   Do not go fetching binaries on your own.
2. Missing tool: look in `$HT/bin`, `$HT/applets`, `TOOLS.txt`; only then extend it per the last section.
3. **Network goes through `netrun`**: `netrun $HT/bin/curl -sSL -o f url`, `netrun $HT/bin/git clone ...`,
   `netrun $HT/bin/python3 -m pip install ...`
   (it does unshare -m plus a tmpfs over /etc with resolv.conf and a CA bundle, affecting only this process tree;
   measured https 200). The app-level `py_download_file` remains available as a fallback.

## Quick reference
| Need | Command | Notes |
|---|---|---|
| Search content/code | `rg -n "pat" /path` | `-g '*.kt'`, `-e` for several patterns, `-c` to count |
| Find files | `fd -e sh name /path` | faster than find, skips .git by default |
| JSON | `jq -c '.a.b' f.json` | 1.7.1 |
| SQLite | `sqlite3 db "select 1"` | 3.45.3, can create databases |
| Real file type | `file -b x.apk` | ships magic.mgc, more accurate than toybox |
| Directory tree | `tree -L 2 -d /path` | 2.1.1 |
| Compress/unpack | `zstd -19 f` `xz -9 f` `lz4 f` `7zz a o.7z d` `zip -r a.zip d` | |
| Sync | `rsync -a --delete src/ dst/` | 3.4.3 |
| HTTP | `netrun $HT/bin/curl -sSL -o f url` | bare curl has no DNS; only inside netrun |
| Text | `awk sed grep sort uniq head tail cut tr diff md5sum sha256sum tar xargs` | busybox applets |
| Scripting | `python3 x.py`; `netrun pip install pkg` | Python 3.12.13 |
| git local | `git init/add/commit/diff/log` | fully usable |
| git remote | `netrun curl -L https://codeload.github.com/u/r/tar.gz/refs/heads/main -o r.tgz && tar xzf r.tgz` | clone is not usable |
| Diagnostics | `lsof -p PID`, `strace -f -e openat cmd` | |

## When not to use it (skip the dead end)
- Full `file` / `curl` capability: `/system/bin` comes first in PATH, so call `$HT/bin/file` explicitly.
- Compiling / gradle / producing an APK: that is `/data/local/aitools` territory (environment in `env/`, not here).
- `pip install` over the network: `netrun $HT/bin/pip install pkg` (bare pip has no DNS).

## Adding a command (cheapest first)
1. **Single static binary**: drop it in `$HT/bin/<name>`, `chmod 755`, done.
2. **Alpine arm64 package** (an apk is just tar.gz, usually tens of KB):
   ```sh
   tar -xzf pkg.apk -C /data/local/aitools/tmp/x        # usr/bin/X, usr/lib/*.so*
   cp .../usr/bin/X $HT/libexec/X && chmod 755 $HT/libexec/X
   printf '#!/system/bin/sh\nexec %s/lib/ld-musl-aarch64.so.1 --library-path %s/lib %s/libexec/X "$@"\n' "$HT" "$HT" "$HT" > $HT/bin/X
   chmod 755 $HT/bin/X; cp .../usr/lib/*.so* $HT/lib/    # add missing libs one by one
   # for libX.so.A.B create the soname link: ln -sf libX.so.A.B $HT/lib/libX.so.A
   ```
3. **Building a project / compiling**: directories are fixed at `/data/local/aitools/{src,project,out,logs,tmp}`;
   edit, then `sync.sh` before building; gradle runs serially, always pass `--max-workers=1`.

## Running Python on the device: the bundled CPython is the only interpreter

- Path: `/data/user/0/com.hoerire.tools/files/hosttools/bin/python3` (3.12, stdlib only).
- **The zafiro_env Alpine chroot has no python3** and cannot see `/data/user`. To use it from inside the
  chroot you must go through `nsenter -t 1 -m -- /data/user/0/.../bin/python3 script.py`.
- Execution channel priority: the app's `shell` tool (the user sees the command and its output) first, then
  `zafiro_env shell` plus nsenter. Only fall back to the latter when `shell` is unavailable.
- For bulk scans or rewrites of Kotlin or binaries, always write a python script and run it. Do not compose
  multi-line edits with `sed -i` or heredocs: quote hell (`unterminated quoted string`,
  `MISSING_REQUIRED_FIELD`) costs far more to repair than writing the script.
