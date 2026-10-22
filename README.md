# Anvil

An AI coding environment that lives on your phone.

Anvil is an Android agent runtime with a real developer toolchain bolted to it. It carries CPython, a
305-applet busybox, ripgrep, jq, 7z, curl, sqlite3, git and aarch64 `aapt2` inside the APK; on first launch
it unpacks them into private storage and hands them to the model as executables. From there the agent can
inspect a repository, run a test suite, bootstrap an Alpine chroot with a JDK and the Android SDK, and
assemble an APK on the device itself.

Package name: `com.hoerire.tools`. Measured release: `Anvil-1.0.apk`, 98,110,774 bytes.

## Origin

Anvil started from [niki914/zafiro](https://github.com/niki914/zafiro) 1.8.1 and kept its Compose UI shell,
settings framework, OKIA agent runtime and `libterm` terminal. Everything under that surface was rewritten:
the tool execution layer, the Python story, the environment bootstrap, the settings schema and the build
pipeline. Upstream is credited in the About screen.

## What is different from upstream

### 1. The toolchain ships inside the APK

Upstream expects the device to already have working tools, or delegates Python to an embedded interpreter.
Anvil ships 1,579 files of real aarch64 binaries as assets and materialises them at
`/data/user/0/com.hoerire.tools/files/hosttools` on first launch.

| Component | Detail |
|---|---|
| Interpreter | CPython 3.12.13 (musl), full standard library — `ssl`, `sqlite3`, `zipfile`, `xml`, `zoneinfo`, `email` |
| Python packages | `requests` 2.34.2, `urllib3` 2.8.0, `idna` 3.20, `certifi` 2026.10.19, `charset_normalizer` 3.5.2, `html2text` 2025.4.15, `pip` 24.0 |
| Shell utilities | busybox with 305 applets |
| Standalone CLIs | `rg`, `fd`, `fzf`, `jq`, `7zz`, `curl`, `sqlite3`, `file`, `rsync`, `wcurl`, `xz`, `zstd`, `lz4`, `strace` |
| Git | `git` 2.45.4 with `git-core` and `remote-https`, so `clone`/`push` work from the agent |
| Android build tool | `aapt2` (bionic aarch64, 3.5 MB) plus the musl `libz` it needs |
| Runtime libs | 71 shared objects, including `ld-musl-aarch64.so.1` and `libexpat.so.1` (needed by `pyexpat`) |

Those binaries are static musl builds, so they run on stock Android without a chroot. Networking goes
through `netrun`, which routes DNS and TLS out of the app sandbox; `curl`, `pip` and `git` all wrap it.

### 2. The build environment lives on the device

The `zafiro_env` tool is new. It exposes seven operations to the model: `check`, `build`, `status`,
`install`, `shell`, `apk` and `snapshot`. With it the agent can look at 21 toolchains, start a Gradle
build in the background, poll the log, and extract an extension pack — all without a host machine.

Behind it sits a bootstrap path that pulls an Alpine rootfs, a JDK 17 and Android SDK platforms 35/36/37.0
on demand. Nothing about the SDK is baked into the APK; only the pieces that cannot be fetched later
(`aapt2`, its shim, signing init script, no-lint init script) ship as assets.

That combination is what makes self-hosting possible: `build` assembles `:app:assembleDebug` from source
that the agent just edited, on the phone that is running the agent.

### 3. Python is a real interpreter, not an embedded runtime

Upstream ran Python through Chaquopy, which drags a wheel-based ABI into the APK. Anvil removed it and
deleted the whole surface that depended on it: the `execute_python` tool, `py_meta_tools`, the custom
Python tool editor, the runtime Python packages and their tests. The replacement is a plain CPython process
launched with `su`, which means unsupported wheels fail loudly instead of silently binding to a mismatched ABI.

Because `pip install` cannot run on this platform (musl hardcodes an interpreter path Android does not
have), packages are installed by `pyinstall.sh`: fetch the wheel from PyPI, unpack the pure-Python files
into a shared `pylib` directory. Pure-Python only — anything with a compiled extension is rejected.

### 4. Startup update check

On cold start the app asks GitHub for the latest release under `Hoerire/anvil`, compares the tag against the
running `versionName`, and shows a dialog when a newer one exists. Dismissing it records that version, so the
same release interrupts once. Drafts and older prereleases are skipped; the check falls back to the most
recent release when no stable one is published yet.

### 5. Removed from upstream

| Removed | Reason |
|---|---|
| Chaquopy and everything downstream | Replaced by the bundled CPython above |
| MCP client and server | No MCP dependency remains in the tree |
| Xposed modules | No hooking of external assistants; no `xposed_init` asset |
| Ssh tool | Terminal SSH backends remain, the separate tool does not |
| `phone-use` and `install-skill-from-github` skills | Superseded by `zafiro-cmd` and `hosttools` |

### 6. Engineering fixes you cannot see but will feel

- **Install fingerprint.** Upstream rebuilt the asset tree whenever `lastUpdateTime` changed, which meant
  every app update re-unpacked 113 MB and looked like a hang. Anvil stamps on the SHA-1 of the asset
  manifest instead, and repairs only missing critical assets.
- **Root isolation.** The tool root is derived per package (`/data/local/aitools`, or `aitools-btN` for a
  test build), so two builds never share state.
- **Portable signing.** `build.gradle.kts` walks `-PRELEASE_STORE_FILE` → `keystore/debug.keystore` →
  `keystore/release.keystore` → AGP's default, so a fresh clone builds without any local paths.
- **`aapt` underscore quirk.** `aapt2` silently drops asset directories whose names start with `_`, which
  broke `zipfile/_path`, `pip/_internal` and friends. Those directories ship as `x__<name>` and are renamed
  back during extraction.

## Features

### Agent runtime

A turn is a stream of typed events rather than a string of text: the engine tracks tool calls, compaction,
sub-agents and context pressure as first-class state. Conversations survive process death, idle sessions
release their model slot, and a failed request retries up to a configured attempt count. Provider selection,
base URL, temperature, reasoning effort and timeouts are per-model settings rather than global switches.

### Built-in tools

Twelve tools, each with its own switch in settings.

| Tool | What it does |
|---|---|
| `shell` | Run commands through the bundled toolchain; root-capable, persistent session |
| `open_uri` | Open a URL, geo, tel or mailto URI |
| `launch_app` | Start an installed app by name or package |
| `find_installed_apps` | Resolve installed packages |
| `notify` | Post a system notification |
| `load_skill` | Pull a skill document into context on demand |
| `memory` | Persist durable facts across sessions |
| `screenshot` | Capture the current screen |
| `view_image` | Read an image file from disk |
| `zafiro_env` | Inspect toolchains, start builds, extract packs |
| `screen_operation_accessibility` | Read and drive the UI tree through the accessibility service |
| `screen_operation_shell` | Tap, swipe and send key events by coordinate |

The tool settings page is flat rather than grouped: each tool is one switch, and the two screen-operation
tools collapse into one group entry.

### Skills

Six skills, loaded on demand so they cost nothing until used.

| Skill | Purpose |
|---|---|
| `build` | The single entry point for compiling: Gradle, AGP, SDK, NDK, `aapt2`, signing, APK inspection |
| `hosttools` | The bundled CLI inventory: `rg`, `jq`, `sqlite3`, `7zz`, `curl`, `python3`, 305 applets |
| `zafiro-cmd` | Canonical command syntax for the tool layer, so the model stops hand-crafting `su`/`chroot` |
| `skill-creator` | Author, edit and test new skills |
| `tool-config` | Read and change app settings by editing the JSON files in the sandbox |
| `termux` | First-time SSH setup to Termux and recovery when a connection breaks |

### Interface

Compose UI on all screens, Material 3 Expressive plus Liquid Glass and Capsule surfaces, light/dark/system,
per-app language selection across six locales, floating ball for quick capture, and a terminal built on
`libterm` with local, libsu, Shizuku and SSH backends.

## Requirements

- Android 8.0+ (`minSdk 26`), `compileSdk 37`.
- No root for the agent itself. Root is required for `shell` to run as `su`, for the on-device build
  environment, and for anything under `/data/local/aitools`. KernelSU and Magisk both work.
- The accessibility service must be enabled for `screen_operation_accessibility`.
- First launch unpacks roughly 113 MB; expect 10-30 seconds once.

## Build

```bash
git clone https://github.com/Hoerire/anvil
cd anvil
./gradlew :app:assembleDebug
```

Optional signing, strongest first: `-PRELEASE_STORE_FILE=/path/keystore` plus the usual
`RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`, or drop a keystore at
`keystore/debug.keystore` on a build machine. Without either, AGP's default debug keystore is used.

The repository checks itself in CI: `.github/scripts/verify_repo.py` asserts documentation is complete, no
keystore is committed, no personal paths leaked and no tab characters crept in;
`verify_hosttools.py` recomputes the asset fingerprint against `assets/hosttools/VERSION`.

## Project layout

| Path | Contents |
|---|---|
| `app/` | Application module: UI, settings, repos, tools glue, `MainActivity` |
| `agent-runtime/` | Turn engine, tool registry, compaction, streaming |
| `business/api/` | Shared models and utilities |
| `libs/libterm/` | Terminal core, runtime and backends (local, libsu, Shizuku, SSH) |
| `app/src/main/assets/hosttools/` | The 1,579-file toolchain, plus `VERSION` fingerprint |
| `app/src/main/assets/toolchain/` | `aapt2` and its musl `libz` |
| `app/src/main/assets/buildkit/` | Gradle init scripts for signing and lint suppression |
| `app/src/main/assets/skills/` | The six skill documents |
| `.agents/skills/` | Development skills for contributors working on the tree |

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.2.0, Jetpack Compose, Material 3 Expressive, Liquid Glass, Capsule |
| Build | AGP 9.1.1 with built-in Kotlin, KSP, `compileSdk 37`, `minSdk 26`, `targetSdk 34` |
| Agent | OKIA runtime, kotlinx-serialization, OkHttp |
| Storage | Room 2.7.2 |
| Terminal | `libterm` (core / runtime / libsu / shizuku / ssh) |
| Images | coil-resvg |
| Python | CPython 3.12.13 (musl), standard library plus seven packaged distributions |

## License

MIT. Original work copyright (c) 2026 niki914; modifications and additions copyright (c) 2026 Hoerire.
See [LICENSE](LICENSE).
