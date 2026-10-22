<div align="right">

**[中文](README_CN.md)** | English

</div>

# Azur

<p align="center">
  <a href="https://github.com/Hoerire/Azur/releases/latest"><img src="https://img.shields.io/github/v/release/Hoerire/Azur?include_prereleases" alt="release"/></a>
  <a href="https://github.com/Hoerire/Azur/releases/latest"><img src="https://img.shields.io/github/downloads/Hoerire/Azur/total" alt="downloads"/></a>
  <a href="https://github.com/Hoerire/Azur/stargazers"><img src="https://img.shields.io/github/stars/Hoerire/Azur?label=stars" alt="stars"/></a>
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="license"/>
</p>

**An Android app that carries a full developer toolchain and compiles code on the device it runs on.**

Most AI apps on Android are a conversation surface plus an API key. Azur adds local execution: the APK
contains 1,579 aarch64 binaries — CPython 3.12, a 305-applet busybox, git with `clone` and `push`, ripgrep, jq,
7z, curl, sqlite3 and `aapt2` — extracted to private storage on first launch and registered as executables the
model can invoke directly.

When a task targets its own source tree, location, editing, verification and building all occur on the same
device: read the file, modify it, run the tests, invoke Gradle, produce a new APK. No host machine is involved
and no external build service is contacted — the toolchain emitting that artifact is the one running inside
the app.

| | |
|---|---|
| **Package** | `com.hoerire.tools` · `minSdk 26` · `compileSdk 37` |
| **APK** | 98 MB, 1,579 bundled binaries |
| **Runtime** | CPython 3.12.13 (musl), complete stdlib, no Chaquopy, no wheel ABI |
| **Shell** | busybox (305 applets) plus `rg` `fd` `fzf` `jq` `7zz` `curl` `sqlite3` `file` `rsync` `wcurl` `xz` `zstd` `lz4` `strace` |
| **Git** | 2.45.4 with `git-core` and `remote-https`; `clone` and `push` are functional |
| **Build tools** | `aapt2` (bionic aarch64, 3.5 MB) plus the musl `libz` it needs |
| **Libraries** | 71 shared objects, including `ld-musl-aarch64.so.1` and `libexpat.so.1` |
| **Cost of entry** | A key for any OpenAI-compatible endpoint |

> [!IMPORTANT]
> Android 8.0+. The agent itself runs unprivileged; root upgrades `shell` to `su` and unlocks the on-device
> build environment. Screen control needs the accessibility service enabled.
>
> Install from [Releases](https://github.com/Hoerire/Azur/releases/latest), or [build it yourself](#build).

## The loop closes

This is the part no other Android agent does: the toolchain is complete enough to rebuild the app you are
talking to.

```text
you  →  "the download badge wraps onto two lines, fix it"
azur →  shell: rg -n 'shields.io' README.md
     →  view_image: /data/local/aitools/out/preview.png
     →  shell: sed -i 's/…/…/' README.md
     →  zafiro_env: build   →  gradle :app:assembleDebug in the background
     →  zafiro_env: status  →  BUILD SUCCESSFUL, RC=0
     →  build complete, new APK produced
```

Underneath sits a bootstrap path that provisions an Alpine rootfs, JDK 17 and Android SDK platforms
35 / 36 / 37.0 the first time they are needed. The SDK is not baked into the APK; only the pieces that cannot be
fetched later ship as assets.

## How it runs without a chroot

Every binary is a static musl build, so stock Android executes them directly. Three problems had to be solved
for that to be useful:

- **Networking.** App sandboxes block raw DNS and TLS. `netrun` bridges both, and `curl`, `git` and `pip` are
  wrapped to go through it.
- **Package installation.** `pip install` cannot run here — musl hardcodes an interpreter path Android does not
  have. `pyinstall.sh` fetches wheels from PyPI and unpacks the pure-Python files into a shared `pylib`. Pure
  Python only; anything with a compiled extension is rejected rather than silently bound to the wrong ABI.
- **Asset extraction.** `aapt2` silently drops asset directories starting with `_`, which breaks
  `zipfile/_path`, `pip/_internal` and friends. They ship as `x__<name>` and are renamed back on extraction.

## The agent

A turn is a typed event stream, not a string being parsed. Tool calls, compaction, sub-agents and context
pressure are first-class state.

- Conversations survive process death; idle sessions release their model slot
- Retries are bounded by configuration, not by luck
- Provider, base URL, temperature, reasoning effort and timeouts are per-model settings
- BYOK — any OpenAI-compatible endpoint, hosted or local

## Twelve tools

`shell` · `zafiro_env` · `screen_operation_accessibility` · `screen_operation_shell` · `screenshot` ·
`view_image` · `launch_app` · `find_installed_apps` · `notify` · `open_uri` · `load_skill` · `memory`

`zafiro_env` is the build bridge: `check` (21 toolchains), `build`, `status`, `install`, `shell`, `apk`,
`snapshot`. Screen control goes through the accessibility service for native UI, or by coordinate through `su`
when the view tree is not exposed.

## Six skills

Markdown documents, loaded only when the model asks, so an unused skill costs zero context.

| Skill | Purpose |
|---|---|
| `build` | The one entry point for compiling: Gradle, AGP, SDK, NDK, `aapt2`, signing, APK inspection |
| `hosttools` | The bundled CLI inventory, so the model does not assume what is available |
| `zafiro-cmd` | Canonical tool-layer syntax, so the model does not hand-roll `su` and `chroot` |
| `skill-creator` | Write, edit and test new skills |
| `tool-config` | Read and change app settings by editing sandbox JSON |
| `termux` | First-time SSH to Termux, and recovery when the link dies |

## Interface

Compose throughout. Material 3 Expressive with Liquid Glass and Capsule surfaces, light/dark/system, six
locales with per-app selection, floating ball for quick capture. The terminal is `libterm` with local, libsu,
Shizuku and SSH backends.

## Architecture

| Module | Contents |
|---|---|
| `app/` | UI, settings, repositories, tool glue, `MainActivity` |
| `agent-runtime/` | Turn engine, tool registry, compaction, streaming |
| `business/api/` | Shared models and utilities |
| `libs/libterm/` | Terminal core, runtime, backends |
| `assets/hosttools/` | The 1,579-file toolchain and its `VERSION` fingerprint |
| `assets/toolchain/` | `aapt2` and musl `libz` |
| `assets/buildkit/` | Gradle init scripts for signing and lint suppression |
| `assets/skills/` | The six skill documents |

Four decisions that shape how this tree is modified:

- **Fingerprint on content, not time.** Assets are stamped with the SHA-1 of the manifest, so an app update does
  not re-unpack 113 MB. Missing critical assets are repaired individually.
- **One root per package.** `/data/local/aitools` for the main build, `aitools-btN` for a test build — two
  installs never share state.
- **Signing is portable.** `-PRELEASE_STORE_FILE` → `keystore/debug.keystore` → `keystore/release.keystore` →
  AGP default. A fresh clone builds with no local paths.
- **CI checks itself.** `.github/scripts/verify_repo.py` asserts docs are complete, no keystore is committed, no
  personal paths leaked, no tabs. `verify_hosttools.py` recomputes the asset fingerprint against `VERSION`.

## Build

```bash
git clone https://github.com/Hoerire/Azur
cd Azur
./gradlew :app:assembleDebug
```

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.2.0, Jetpack Compose, Material 3 Expressive |
| Build | AGP 9.1.1 with built-in Kotlin, KSP, `compileSdk 37` |
| Agent | OKIA runtime, kotlinx-serialization, OkHttp |
| Storage | Room |
| Terminal | `libterm` (libsu / Shizuku / SSH) |
| Toolchain | CPython 3.12.13 (musl), busybox, git 2.45.4, `aapt2` |

## Origin

Azur grew out of [niki914/zafiro](https://github.com/niki914/zafiro) 1.8.1. It kept that project's shell — the
Compose UI, the settings framework, the OKIA runtime, the `libterm` terminal — and replaced everything beneath
it: the tool execution layer, the Python story, the environment bootstrap, the build pipeline. Upstream is
credited in the About screen.

## License

MIT. See [LICENSE](LICENSE).
