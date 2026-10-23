---
name: build
description: Local build and packaging entry point. Read this first for build, compile, package, produce APK, assemble, R8, keystore signing, versionCode, applicationId, gradle, AGP, SDK, aapt2, NDK, toolchain, env provisioning, rootfs, container, pm install, decompile, unpack, dex, smali, AXML, ELF, readelf, BUILD FAILED, compile errors, or command not found.
---

## Hard rule: report the diff before installing (user requirement)

**Execution gate**

- Post the summary first, then **install immediately after it** - do not stop at the summary waiting
  for confirmation. When the user has already authorised the install, the summary is a step in
  execution, not a pause.
- The summary must contain the file list of this round's diff (one line per file, what changed), the
  artifact path, size, md5 and versionCode.
- Installing without a summary = failure. The user will interrupt and demand a redo.

**Check these before writing the summary:**

1. After editing the source tree you must `sync` to the build tree before building, otherwise you ship
  the old code.
2. After installing, `HostToolsInstaller` overwrites `/data/local/aitools/*.sh` with the **APK's
  `assets/buildkit`**. If you edited a host script by hand without rebuilding, the next install
  reverts it (the symptom is `build-src.sh` referring to an old path again).

Before any `pm install` / overwrite, output this round's diff summary, then install:

1. **Changed files**: `file -> what changed` (which class/field/branch was removed or added, one line).
2. **Artifact facts**: versionCode, size, md5, APK path.
3. **One sentence**: "installing vXX now".
4. After installing report only: `Success` plus versionCode verification.

Forbidden: install-then-explain, reporting only md5 without the diff, putting the summary after the
install command. No summary before install = failure.

# Build

## Python dependencies: no pip, use pyinstall.sh

The host `hosttools/bin/python3` is 3.12.13 **stdlib only**: site-packages holds only a broken pip
skeleton, and `zipfile._path` was stripped (a stub is patched into hosttools). Fetch missing deps
from the network; do not fight pip.

```sh
# install pure python packages into the shared location
sh /data/local/aitools/pyinstall.sh requests urllib3 idna certifi charset-normalizer html2text

# use them
H=/data/user/0/com.hoerire.tools/files/hosttools
PYTHONPATH=/data/local/aitools/pylib $H/bin/netrun $H/bin/python3 script.py
```

How it works: `netrun curl` pulls `https://pypi.org/pypi/<pkg>/json`, picks the `py3-none-any.whl`,
`7zz x` extracts it into `/data/local/aitools/pylib`.

- **Installable**: pure python packages (requests / urllib3 / idna / certifi / charset-normalizer / html2text ...).
- **Not installable**: wheels carrying `.so` - hosttools is a musl static build and cannot load glibc
  extensions (numpy / lxml / pillow need a separate glibc environment).
- All network access goes through `netrun`; musl does not use the Android resolver.
- Dependencies live in `/data/local/aitools/pylib` (not `tmp/`, so switching trees does not clear them).

## 0. Hard rules

- Run `sh /data/local/aitools/verify.sh` before building. It is read-only, prints PASS/FAIL, and a
  non-zero exit means a real problem. **Never substitute your own prose for it.**
- Writable locations: only `/data/local/aitools/`; the one exception is `pm install` staging in
  `/data/local/tmp` (delete after).
- Edit only `src/<tree>`; build only `project/<same tree>`; sync only via `project/sync.sh <tree>`.
- Artifacts -> `out/`; logs -> `logs/`; intermediates -> `tmp/` (clear when the task ends).
- The execution rules block outright: hand-rolled `chroot`, `su -c`, `env/run.sh`, calling `gradlew`
  directly, `grep -P`, `ls -R`, `find` without `-maxdepth`.
- Naming: `project/<name>` must match `src/<name>`; `build-src.sh` validates this and exits 2 listing
  the available trees.
- Exception: kernel trees may be named `project/kernel-<soc>-<ver>` (e.g. `kernel-sm8750-6.6`); they do
  not go through `build-src.sh`, and the naming rule only constrains app builds.

## 0.1 Tool availability (check first, never trial-and-error one by one)

Inside the container (`zafiro_env(action="shell")`) these are installed and ready - **prefer them**:
```
compile  gcc g++ clang clang++ lld ld objcopy ar as nm strip readelf objdump addr2line
build    cmake ninja make pkg-config samu
script   python3 node npm git rg jq sqlite3 tree xxd strace lsof file diff patch sed awk
archive  tar gzip zip unzip 7z aria2c lzop pzstd zstd xz bzip2
network  curl wget nc ss ip ping traceroute whois
crypto   openssl keytool jarsigner
java     java(17/21) javac jar javadoc javap jlink jimage
android  aapt2 zipalign apksigner adb fastboot lpmake lpunpack unpack_bootimg mkdtboimg avbtool simg2img img2simg
kernel   pahole bc bison flex m4 dtc kmod rsync cpio
language go(/usr/lib/go/bin/go, not on PATH) gofmt rustc cargo ruby gem perl php lua5.4
```
- Baseline: the rootfs carries roughly 725 executables and 259 apk packages. If something is missing,
  check `command -v X` -> `ls /usr/bin` -> only then `zafiro_env(action="apk", cmd="add --no-cache X")`.
- **Host side** (the `shell` tool): no gcc/clang/python3/node/git, only `/system/bin/curl`; the host
  `aapt/aapt2` is x86_64, so it fails with `not executable: 64-bit ELF`.
- Known gaps (do not waste attempts): `pip3`, `apktool`, `jadx`, `baksmali`, `meson`, `mkbootimg` are
  absent (use `lpmake/lpunpack/unpack_bootimg` for boot images).

## 1. Layout
```
<root>/  verify.sh build-src.sh bootstrap-env.sh env-doctor.sh env.sh run.sh
         hosttools-install.sh aitools-fix.sh
env/     rootfs/ android-sdk/ gradle-<ver>/ gradle-home/ toolchain/ buildkit/ debug.keystore
project/ <tree> (gradlew only runs here) + sync.sh
src/     <tree> authoritative source, edit only never build (must have no build/ .gradle/ .kotlin/)
out/     logs/     tmp/
```
- `verify.sh` at the root is the gate; run it before building. `buildkit/` holds the signing and lint
  init scripts and is overwritten from the APK on install.
- The artifact name comes from `ZAFIRO_APK_NAME`, it is not fixed; take the newest with
  `ls -t out/*.apk | head -1`, never assume `app-debug.apk`.

## 2. env
- If `env/env.sh` exists the environment is provisioned - do not rebuild it. Check with
  `zafiro_env(action="check")`.
- Missing -> `sh /data/local/aitools/bootstrap-env.sh` (idempotent provisioner: when rootfs/gradle/
  platforms x3/build-tools/cmdline-tools are missing it **downloads through netrun and installs
  automatically**, including `apk add openjdk17`, the aapt2 toolchain and keystore self-healing).
  `build-src.sh` preflight invokes it when gradle is absent; a failed build automatically runs
  env-doctor and appends the result to the log tail.
- Health check / repair: `sh /data/local/aitools/aitools-fix.sh --check`, add
  `--with-ndk --with-platforms --with-cmdline` to repair.
- Dependency cache `env/gradle-home` (roughly 1.39G when warm): gradle populates it on first build,
  there is no warm-up script and no seed file. Judge by `du -sh env/gradle-home`, not by docs.

## 3. Producing an APK
```sh
zafiro_env(action="build", args="-PdevSuffix --max-workers=1")            # when the tree holds one project
sh /data/local/aitools/build-src.sh --src=/data/local/aitools/project/<name> --max-workers=1
```
- Three success signals: the log shows `BUILD SUCCESSFUL`, `RC=0`, and `path: out/...apk`.
- The script copies the artifact and prints `path/size/md5/appId=...`; override the name with
  `ZAFIRO_APK_NAME=`, skip the copy with `ZAFIRO_NO_COPY=1`.
- Builds must be serial: `--max-workers=1` is mandatory. Warm build ~2 s, cold 15-30 s, full rebuild of
  every module 4-5 minutes.
- Force a rebuild: delete `app/build/outputs/apk/<variant>/app-release.apk` and rerun; faster than
  `--rerun-tasks`.

## 4. Shell compatibility (measured)
| Form | host toybox | container busybox | Use instead |
|---|---|---|---|
| alternation `\|` | x silently matches nothing | ok | `-E 'a|b'` or `-e a -e b` |
| `-P` regex | x | x | `-E` |
| `sed -n` alternation | x empty output | ok | `sed -nE` |
| `diff -x` / `--exclude` | x | x | compare md5 lists |
| `find -printf` | ok | x | `-exec` |
`awk / stat -c / xargs -r / sort -k2 / unzip -l / readlink -f` work on both sides. `sed -i` with `#` as
the delimiter fails with `bad pattern` on text containing `##` -> use `@`.

## 5. Editing source and verifying
**Repair rule (three steps, skipping one is failure)**: (1) edit `src/` (2)
`sh project/sync.sh <tree>` must answer `SYNC OK` (3) only then build.
Skipping step 2 means you compiled the old code and wasted a round.

```sh
cp -p f f.bak && sed -i '453s@.*@new content@' f && sed -n '450,456p' f    # line numbers are the most stable anchor; always read back
sh /data/local/aitools/project/sync.sh <tree>                              # the only sync entry point, does an md5 diff
```
- For bulk rewrites prefer the container's `python3` (avoids quote hell).
- Edits to verification logic fail silently most often: inspect the real artifact on disk first, then
  the script's verdict.

## 6. Running inside the container
```sh
zafiro_env(action="shell", cmd="gcc -O2 -o /data/local/aitools/tmp/a /data/local/aitools/tmp/a.c && /data/local/aitools/tmp/a")
zafiro_env(action="apk", cmd="add --no-cache jq")
```
- The container only rbind-mounts `src/project/out/logs/tmp`: scripts at the root are stale rootfs
  copies inside the container, so edit them from the host `shell`.
- The container's `/tmp` is not the host's - never use it as a workspace.

## 7. Installing and verifying
```sh
cp x.apk /data/local/tmp/x.apk && pm install -r /data/local/tmp/x.apk && rm -f /data/local/tmp/x.apk
```
- Version facts live in `output-metadata.json`; zip timestamps inside an APK are normalised to 1981, so
  judge recency by the host `ls -l` mtime.
- Side-by-side testing: change `applicationId` to `<package>.beta` in the **build tree only**, the
  source tree always keeps the original package name.

## 8. Analysing / decompiling
Available: `unzip -l/-p`, `file`, `readelf -hd`, `nm -D`, `objdump -d`, `strings -n 6`,
`java -jar build-tools/<v>/lib/apksigner.jar verify --print-certs`, `d8`.
Not available: `apktool`, `jadx`, `baksmali` (download the release zip into `tmp/` if you must, delete
afterwards). `unzip -p x.apk AndroidManifest.xml` yields binary AXML; parse it with the container's
python3, or just read `output-metadata.json`.

## 9. Traps (measured)
- `>>` onto a file without a trailing newline concatenates -> `printf '\n' >> f` first.
- Do not define shell functions on the host (`fc: history functions not available`); do not pass
  `workdir` to the `shell` tool (`Session gone`) - use absolute paths.
- After renaming a directory, first fix hardcoded paths inside scripts, otherwise a mirror script treats
  the source as empty and wipes the target (this actually deleted 1076 files once). Run destructive
  scripts as a dry run that prints the deletion count.
- Long tasks: `nohup ... > logs/x.log 2>&1 &` then `tail -n 4` at fixed intervals - never tight-loop.
- Under 300M free memory: `zafiro_env(action="shell", cmd="gradle --stop")` before building.

## 10. Reporting
Always close with: `path / size / md5 / built`, 20-30 lines of key log, and mark anything unverified as
"needs verification".

## Bundled Python: the aapt `_` directory trap (fixed in v26)
- aapt **drops assets directories starting with `_`** (files survive) -> `zipfile/_path`, `pip/_internal`,
  `pip/_vendor` went missing.
- Fix: rename the asset-side directory to `x__<original>`, and `HostToolsInstaller.copyDir` restores it
  with `removePrefix("x__")`.
- `pyexpat` needs `assets/hosttools/lib/libexpat.so.1` (musl 2.8.1).
- pip can only do `--version`, **`pip install` does not work** (musl sets PT_INTERP to
  /lib/ld-musl-aarch64.so.1, which does not exist). Install pure python packages with
  `sh /data/local/aitools/pyinstall.sh <pkg>`.

## Re-deploying after changing hosttools / assets

- The re-deploy switch is the **SHA-1 of `app/src/main/assets/hosttools/VERSION`** (stamp file
  `files/hosttools.stamp`), **not** the APK lastUpdateTime. Changing a binary without touching VERSION
  leaves the old file on the device after install (aapt2 mtime unchanged, new .so never appears), which
  looks like "the change did not take effect".
- Always bump VERSION in the same round as a hosttools change (append a marker line such as
  `toolchain-clean-1`).
- **Two toolchain copies under assets**: `assets/toolchain` (goes into the APK) and
  `assets/hosttools/toolchain` (deployed to files/hosttools). Editing only one leaves the APK stale;
  find them all with `find . -type d -name toolchain`.
- To confirm a real re-deploy: mtime of `ls -l files/hosttools/toolchain/aapt2` plus `cat files/hosttools.stamp`.
- After changing large assets (assets dir > 10MB) run `rm -rf project/<tree>/app/build` before building,
  otherwise AGP reuses the old asset cache.
- Edit skills only under `assets/skills/`, never `files/skills/`: the latter is seeding output, and a
  hand edit makes `.seed` mismatch the content so the seeder decides "user edited it" and stops
  updating. Skills removed from assets do not disappear from the runtime directory - clean those up
  manually.

## Pre-flight guard: assets cleanliness

```sh
# assets must not carry build-machine absolute paths (they crash on any other device)
# verify.sh builds the pattern from split literals so it never matches itself
sh /data/local/aitools/verify.sh || exit 1
```

- Third-party prebuilt binaries (aapt2 and its libraries) often bake build-machine paths into the ELF:
  RUNPATH pointing at an absolute `usr/lib`, `.rodata` holding the build directory. Such binaries only
  run where the same environment exists; move to another device and you get
  `library "libz.so.1" not found` immediately.
- Cleaning (no recompilation needed): `patchelf --set-rpath '$ORIGIN'` for libraries /
  `'$ORIGIN/lib'` for executables; mask `.rodata` in place with an equal-length replacement (length
  preserved, offsets untouched); vendor the libraries missing from the closure (libz.so.1 /
  libexpat.so.1 / libbz2.so.1.0) into `toolchain/lib`.
- Acceptance: run `toolchain/aapt2 version` with **no** LD_LIBRARY_PATH set, then
  `aapt2 compile <some values.xml>` and confirm a zip comes out.

## Pushing source to a remote

The host git cannot do https: `libexec/git-core/git-remote-http` is a musl binary whose PT_INTERP
points at `/lib/ld-musl-aarch64.so.1`, which does not exist on the device and cannot be created
because `/` is read-only. Do not wrap it (git decides its role from `argv[0]`, so `exec ld-musl` loses
the subcommand; forwarding through `git remote-http` recurses until
`Argument list too long`).

The only working path is the Alpine git inside the chroot (`env/rootfs/usr/bin/git`, shipping a real
732KB `git-remote-http`):

```sh
# must run inside zafiro_env(shell)
export GIT_DIR=/data/local/aitools/tmp/git/<bare-repo>.git
export GIT_WORK_TREE=/data/local/aitools/src/<tree>
cd "$GIT_WORK_TREE" && git push origin main
```

If it reports `fatal: Invalid path '/data/local/aitools/<old-dir>'`, the bare repo's `core.worktree`
still points at a pre-rename path; fix it with
`git --git-dir=<repo> config core.worktree <new path>`.
