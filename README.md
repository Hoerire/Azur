<div align="right">

**[中文](README_CN.md)** | English

</div>

# Azur

<p align="center">
  <a href="https://github.com/Hoerire/azur/releases/latest"><img src="https://img.shields.io/github/v/release/Hoerire/azur?include_prereleases" alt="release"/></a>
  <a href="https://github.com/Hoerire/azur/releases/latest"><img src="https://img.shields.io/github/downloads/Hoerire/azur/total" alt="downloads"/></a>
  <a href="https://github.com/Hoerire/azur/stargazers"><img src="https://img.shields.io/github/stars/Hoerire/azur?label=stars" alt="stars"/></a>
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="license"/>
</p>

Azur is an AI coding agent that runs on your phone and needs nothing else. It ships a real toolchain inside
the APK — CPython 3.12, a 305-applet busybox, `git`, `rg`, `jq`, `7zz`, `curl`, `sqlite3` and `aapt2` — unpacks
it on first launch, and hands it to the model as executables. Ask it to fix a bug, and it edits the source,
runs the tests, and rebuilds the APK on the same device it is running on.

Bring your own key. Any OpenAI-compatible endpoint works, from a frontier model to a local runtime.

> [!IMPORTANT]
> Android 8.0+. No root needed for the agent itself — root unlocks `shell` as `su` and the on-device build
> environment. Accessibility service is required for screen control.
>
> Grab a build from [Releases](https://github.com/Hoerire/azur/releases/latest), or [build from source](#build).

## Core Capabilities

### A toolchain that travels with the app

Most "AI on Android" apps call an API and render text. Azur brings the other half.

- **1,579 binaries** compiled for aarch64 musl, unpacked into private storage on first launch
- **CPython 3.12 with a working standard library** — `ssl`, `sqlite3`, `zipfile`, `xml`, `zoneinfo`, plus `requests`, `urllib3`, `html2text`
- **`git` that can actually clone and push** — `git-core` and `remote-https` included, not a stripped-down stub
- **`aapt2` bundled** — the one piece of the Android build chain that cannot be downloaded at runtime
- **No chroot, no Termux, no companion app** — static musl builds run directly on stock Android
- **Networking that works** — `netrun` routes DNS and TLS out of the app sandbox, so `curl`, `pip` and `git` behave

### Build on the phone, for the phone

The agent can provision a full build environment and use it without help.

- **`zafiro_env`** exposes seven operations: `check`, `build`, `status`, `install`, `shell`, `apk`, `snapshot`
- **Bootstrap on demand** — pulls an Alpine rootfs, JDK 17 and Android SDK 35/36/37.0 when first needed
- **Real Gradle builds in the background** — start, poll the log, get an APK
- **The loop closes** — the APK it builds is the app you are talking to

### An agent runtime, not a chat wrapper

- **Typed event stream** — tool calls, compaction, sub-agents and context pressure are first-class state, not string parsing
- **Survives process death** — conversations resume where they were
- **Per-model settings** — provider, base URL, temperature, reasoning effort and timeouts, per model rather than globally
- **Idle sessions release their slot**, failed requests retry to a configured count

### Twelve tools

`shell` · `zafiro_env` · `screen_operation_accessibility` · `screen_operation_shell` · `screenshot` ·
`view_image` · `launch_app` · `find_installed_apps` · `notify` · `open_uri` · `load_skill` · `memory`

Each has its own switch. Screen control works through the accessibility service, or by coordinate through
`su` when the UI tree is not exposed.

### Skills, loaded on demand

Six documents that cost nothing until the model asks for one: `build` for the whole compile-and-package
pipeline, `hosttools` for the bundled CLI inventory, `zafiro-cmd` for canonical tool syntax, plus
`skill-creator`, `tool-config` and `termux`. Write your own — they are plain Markdown.

### Interface

Material 3 Expressive with Liquid Glass and Capsule surfaces, light/dark/system, six locales, floating ball
for quick capture, and a `libterm` terminal with local, libsu, Shizuku and SSH backends.

## Where it came from

Azur grew out of [niki914/zafiro](https://github.com/niki914/zafiro) 1.8.1. It kept that project's shell —
the Compose UI, the settings framework, the OKIA runtime, the `libterm` terminal — and replaced everything
underneath: the tool execution layer, the Python story, the environment bootstrap, the build pipeline.
Upstream is credited in the About screen.

## Build

```bash
git clone https://github.com/Hoerire/azur
cd azur
./gradlew :app:assembleDebug
```

Signing is optional. The build looks for `-PRELEASE_STORE_FILE`, then `keystore/debug.keystore`, then
`keystore/release.keystore`, and falls back to AGP's default debug key.

CI runs `.github/scripts/verify_repo.py` (docs complete, no keystore committed, no personal paths, no tabs)
and `verify_hosttools.py` (asset fingerprint matches `assets/hosttools/VERSION`).

## Project Structure

| Path | Contents |
|---|---|
| `app/` | UI, settings, repos, tool glue |
| `agent-runtime/` | Turn engine, tool registry, compaction, streaming |
| `business/api/` | Shared models and utilities |
| `libs/libterm/` | Terminal core, runtime, backends |
| `app/src/main/assets/hosttools/` | The bundled toolchain |
| `app/src/main/assets/skills/` | The six skill documents |

## Tech Stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.2.0, Jetpack Compose, Material 3 Expressive |
| Build | AGP 9.1.1, KSP, `compileSdk 37`, `minSdk 26` |
| Agent | OKIA runtime, kotlinx-serialization, OkHttp |
| Storage | Room |
| Terminal | `libterm` (libsu / Shizuku / SSH backends) |
| Toolchain | CPython 3.12.13 (musl), busybox, git 2.45.4, aapt2 |

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the build loop, the asset
fingerprint rule, and the checks CI runs.

## License

MIT. See [LICENSE](LICENSE).
