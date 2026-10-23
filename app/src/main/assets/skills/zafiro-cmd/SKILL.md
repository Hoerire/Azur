---
name: zafiro-cmd
description: Command invocation cheat sheet - the correct form for every zafiro_env action. Use this first whenever a task involves calling a tool, gluing commands together, a blocked command, an unreadable error, noisy output, su -c, chroot, the shell tool, build or compile commands, unzip, install, check, status, md5, or "how do I run this". Copy the form instead of guessing.
---

# Command invocation cheat sheet

> Shell quirks (`\|` silently failing, no `diff -x`, no `find -printf`, aapt being x86_64)
> are in the build skill, section 4. Read that before composing a command.

**Priority**: if a bundled host tool can do it (rg/jq/sqlite3/file/tree/7zz/zip/rsync/curl/
xz/zstd/lz4/python3/git/openssl - see the `hosttools` skill), stay on the host. Use
`zafiro_env` only for compiling, running gcc/cmake, installing packages, or producing an APK.

The tool already handles privilege escalation, chroot entry and argument quoting.
**Pass a plain command, one action does one thing.**

## 0. Which path to take
| Task | Use | Do not use |
|---|---|---|
| Read or edit files in the tree, list a directory | `shell` tool (host, root) | do not enter the container |
| Compile, run a compiler, install a package, inspect the environment | `zafiro_env` | do not hand-roll an escalation prefix |
| Produce an APK | `zafiro_env(action="build")` | do not hand-roll gradle commands |

## 1. Environment state (deploy/install are retired)

**Provisioning was removed from the app**: there is no deploy button, no DevenvInstaller, and
install-full.sh is no longer shipped in the APK. Do not try to "re-provision"; use:

```sh
sh /data/local/aitools/env-doctor.sh        # measured checks, each failure carries a fix line
```
- `build-src.sh` already runs the doctor on failure and appends it to the log tail - read the log first.
- Missing pieces: follow the fix line. Host tools live in `files/hosttools` (see the `hosttools` skill).
- Is the environment present: `ls -A /data/local/aitools/env` (`rootfs`/`android-sdk`/`gradle-*` is enough).
  Do not recurse, do not `du`.

## 2. Build
```
{"action":"build","args":"-PdevSuffix --max-workers=1 -Dorg.gradle.parallel=false -Dkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process"}
{"action":"status"}
```
- `args` carries gradle flags only - no `cd`, no `build-src.sh`.
- The reply includes `src:` (the tree being built) and `slot=N` (concurrency slot); when no slot is
  free it queues, it does not refuse.
- Success means `status` shows `BUILD SUCCESSFUL` **and** `RC=0`, then check size and md5 in `out/`.

## 3. Inside the container
```
{"action":"shell","cmd":"gcc --version"}
{"action":"shell","cmd":"gcc -O2 -o /data/local/aitools/tmp/a /data/local/aitools/tmp/a.c && /data/local/aitools/tmp/a"}
{"action":"shell","cmd":"cmake -S /data/local/aitools/tmp/p -B /data/local/aitools/tmp/p/out -G Ninja && cmake --build /data/local/aitools/tmp/p/out"}
{"action":"shell","cmd":"ls -t /data/local/aitools/out/*.apk | head -3; md5sum /data/local/aitools/out/*.apk"}
{"action":"shell","cmd":"rustc --version; node --version; go version"}     # several commands joined by ; are fine
{"action":"apk","cmd":"info -e gcc"}          # is a package installed, and under what name
{"action":"apk","cmd":"add --no-cache jq"}    # install (Tencent mirror)
```
**Scratch rule**: temporary files, build directories and drafts all go in `/data/local/aitools/tmp/` -
it is the only temp directory bound into the container and is shared with the host. Never use `/tmp`
or `/sdcard`; `/data/local/tmp` is reserved for `pm install`.
The container's `/tmp` is inside the rootfs and is a different directory - artifacts written there are lost.

## 4. Host-side operations (no container)
```
sed -n '100,130p' /data/local/aitools/project/<tree>/app/src/main/AndroidManifest.xml
ls -A /data/local/aitools/project/<tree>
tail -20 /data/local/aitools/logs/build-py.log
```
- Host `python3`: `/data/user/0/com.hoerire.tools/files/hosttools/bin/python3`

## 5. Never do this (the execution rules block it outright)
1. Hand-rolling an escalation prefix (writing `su` plus `-c` yourself and nesting quotes) - nested
   quoting reliably produces mangled arguments.
2. Entering the container by hand: `chroot`, `exec chroot`.
3. Piping unrelated commands together inside one action, stuffing in `while` / `set -e` / `export`.
4. Prefix-style builds built out of `cd` plus `&&`.
5. Whole-disk scans: recursive listings, `find` from `/` without `maxdepth`, `du` over `/data`.

## 6. When something is blocked
- `COMMAND_BLOCKED` with `matched_rule_name`: the form is wrong. Rewrite it as shown above and retry
  once - do not keep hammering with variations.
- `[timeout]`: the command exceeded 110 s. Split it, or use build + status.
- `ENV_NOT_INSTALLED` / `PACK_NOT_FOUND`: run `sh /data/local/aitools/env-doctor.sh` and follow the fix
  line (**there is no deploy anymore**).

## Directory map (read this first)
`/data/local/aitools/{src,project,out,logs,tmp}` plus `env/` (the build toolchain; missing pieces ->
env-doctor).
`src/<tree>` is the authoritative source tree - the tree name depends on the project you are working on.
Edit it directly and ignore how it splits into modules.
