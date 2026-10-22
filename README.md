# Anvil

**English** · [简体中文](README_CN.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen.svg)](#requirements)
[![minSdk](https://img.shields.io/badge/minSdk-26-blue.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-7F52FF.svg)](https://kotlinlang.org)
[![checks](https://github.com/Hoerire/anvil/actions/workflows/checks.yml/badge.svg)](https://github.com/Hoerire/anvil/actions/workflows/checks.yml)

An AI coding environment that lives on your phone: it ships a complete userspace toolchain and a
build stack, so the agent can edit code, run it, and assemble an APK without a desktop.

---

## Origin

Anvil started from [niki914/zafiro](https://github.com/niki914/zafiro), released under the MIT
license. The original project supplied the application skeleton: the Compose UI shell, the
settings plumbing, the OKIA agent runtime and the libterm terminal backends.

Everything below the UI was rebuilt. The execution model, the tool system, the Python story and the
build story are not the same as the project it started from.

The original copyright notice is kept verbatim in [LICENSE](LICENSE).

## What changed

**Execution layer, rebuilt**

| Area | Before | Now |
|---|---|---|
| Python | Interpretation ran inside the app through an embedded runtime | A real CPython 3.12.13 (musl) ships in the APK and runs from private storage |
| Tool protocol | MCP client and server bundled | Removed entirely — no MCP code or dependency remains |
| Assistant takeover | Xposed modules hooked external voice assistants | Removed; the tree contains no Xposed modules |
| Tool surface | Python-defined custom tools registered at runtime | Twelve first-class built-in tools behind a typed registry, with per-tool switches |

**Toolchain, shipped instead of assumed**

- ~113 MB of tooling is packed into the APK: CPython, ripgrep, jq, 7z, curl, sqlite3, `file`,
  fd, fzf, strace-log-merge, and 305 busybox applets, unpacked into private storage on first launch.
- Unpacking is content-addressed: a fingerprint of the shipped tree is compared with the value
  recorded on the previous launch, so upgrades only re-extract when the content actually changed.
- Directories whose names begin with an underscore are shipped under an `x__` prefix, because the
  packaging step silently drops them, and restored on extraction.

**Build environment, on the device**

- The app can bootstrap an Alpine chroot with a JDK, the Android SDK and Gradle, then drive
  `gradlew` inside a container to produce an APK — the build environment is a tool the agent calls.
- Build assets travel with the app: a bionic `aapt2` with its musl `libz`, and the Gradle init
  scripts that wire signing, so a rebuilt environment does not need them fetched again.

**Operational details**

- Portable signing: `ZAFIRO_KEYSTORE` or `-PRELEASE_STORE_FILE`, then an in-repo keystore, then the
  standard debug keystore.
- Separate build roots for the main package and for side-by-side test builds, so experiments never
  touch the primary environment.
- Unified `zafiro_` log prefix across 63 source files for filterable logs.

---

## Features

### Agent runtime

Tool-calling loop with streaming output, retry with classification, cancellation, and conversation
persistence in Room. Prompts are composed in stable and volatile layers, so the environment block can
change between turns without invalidating the rest.

### Built-in tools

| Tool | What it does |
|---|---|
| `shell` | Run commands through the bundled toolchain (root-capable) |
| `open_uri` | Open a URL, geo, tel or mailto URI |
| `launch_app` | Start an installed app by name or package |
| `find_installed_apps` | Resolve installed packages |
| `notify` | Post a system notification |
| `load_skill` | Pull a skill document into context on demand |
| `memory` | Persist durable facts across sessions |
| `screenshot` | Capture the current screen |
| `view_image` | Read an image file from disk |
| `zafiro_env` | Inspect toolchains and build environments; start on-device builds |
| `screen_operation_accessibility` | Read and drive the UI tree through the accessibility service |
| `screen_operation_shell` | Tap, swipe and send key events by coordinate |

### Skills

Markdown skill documents ship inside the APK and load on demand, keeping the system prompt small
while making deep, task-specific knowledge available when the agent asks for it.

### Interface

Jetpack Compose with Material 3 Expressive, Liquid Glass surfaces and Capsule rounded corners.
Chat, history, usage, and a tool settings surface with per-tool switches and group toggles.

---

## Requirements

- Android 8.0+ (`minSdk 26`), Android 14–16 recommended
- Root (KernelSU or Magisk) for the full toolchain — without it the app runs, but the app process
  cannot execute binaries from private storage
- Accessibility service permission for the screen-operation tools
- `INTERNET` for model APIs

The app writes only to its own private storage; no storage permission is needed.

---

## Build

```sh
./gradlew :app:assembleDebug
```

Requires JDK 17 and Android SDK platform 37 / build-tools 36. Output lands in
`app/build/outputs/apk/debug/`.

Signing picks the first available of: `ZAFIRO_KEYSTORE` (absolute path), the
`-PRELEASE_STORE_FILE` Gradle property, `keystore/debug.keystore`, `keystore/release.keystore`, or
the default debug keystore Gradle generates.

Repository checks run in CI: documents present, no committed keystores or build output, no personal
paths in sources, tab-free Kotlin, and the bundled toolchain fingerprint matching its manifest.
Regenerate the fingerprint after touching the toolchain with
`python3 .github/scripts/gen_hosttools_version.py`.

---

## Project layout

```
app/              application, Compose UI, settings, tool manager
agent-runtime/    agent loop, prompt composer, built-in tools, tool executor
business/         api · agent · files · notification · application · permission
libs/             logging · okia(runtime) · termux(libterm backends)
ui-kit/           shared Compose components
store/            key-value and domain settings store
xsettings/        runtime settings bridge
remote-view/      remote view primitives
common-utils/     shared utilities
```

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.2.0 · Jetpack Compose · Material 3 Expressive · Liquid Glass · Capsule |
| Build | AGP 9.1.1 (built-in Kotlin) · KSP · `compileSdk 37` · `minSdk 26` · `targetSdk 34` |
| Agent | OKIA runtime · kotlinx-serialization · OkHttp |
| Storage | Room 2.7.2 |
| Terminal | libterm (core / runtime / libsu / shizuku / ssh backends) |
| Images | coil-resvg |
| On-device Python | CPython 3.12.13 (musl), bundled, stdlib plus packaged wheels |

---

## License

MIT. See [LICENSE](LICENSE), which carries both the original and the current copyright notice.
