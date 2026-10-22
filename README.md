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

**An Android application that embeds a complete developer toolchain and compiles code on the device it runs on.**

Azur derives from [niki914/zafiro](https://github.com/niki914/zafiro) 1.8.1, from which it retains the Compose
UI, settings framework, OKIA agent runtime and `libterm` terminal. The layers beneath those were replaced with
an execution environment that runs native binaries, a bootstrap path that provisions a build toolchain on the
device, and a build pipeline whose inputs ship inside the APK. Upstream attribution is retained in the About
screen.

The application installs as a single APK. Launching it does no unpacking: the 1,579 aarch64 binaries are
extracted into private storage the first time a tool actually needs them, then registered as executables
available to the model.

| | |
|---|---|
| **Package** | `com.hoerire.tools` · `minSdk 26` · `compileSdk 37` |
| **APK** | 98 MB, 1,579 bundled binaries |
| **Interpreter** | CPython 3.12.13 (musl), complete standard library, no Chaquopy, no wheel ABI layer |
| **Python packages** | `requests` 2.34.2, `urllib3` 2.8.0, `idna` 3.20, `certifi` 2026.10.19, `charset_normalizer` 3.5.2, `html2text` 2025.4.15 |
| **Shell** | busybox (305 applets) plus `rg` `fd` `fzf` `jq` `7zz` `curl` `sqlite3` `file` `rsync` `wcurl` `xz` `zstd` `lz4` `strace` |
| **Git** | 2.45.4 with `git-core` and `remote-https`; `clone` and `push` are supported |
| **Build tools** | `aapt2` (bionic aarch64, 3.5 MB) with the musl `libz` it links against |
| **Shared objects** | 71, including `ld-musl-aarch64.so.1` and `libexpat.so.1` |
| **Model access** | Any OpenAI-compatible endpoint, configured with a user-supplied key |

> [!IMPORTANT]
> Requires Android 8.0 or later. The agent runs unprivileged; root elevates `shell` to `su` and enables the
> on-device build environment. Screen control requires the accessibility service to be enabled.
>
> Install from [Releases](https://github.com/Hoerire/Azur/releases/latest), or [build from source](#build).

## Execution model

All bundled binaries are statically linked against musl and execute as ordinary Android processes. No
container, no companion process and no external runtime are involved. Three platform constraints determine the
rest of the design.

**Network egress.** Application sandboxes do not permit direct DNS or TLS access. `netrun` forwards both out of
the sandbox, and `curl`, `git` and the package installer are wrapped to route through it.

**Python package installation.** `pip install` cannot execute on this platform: the musl interpreter records an
absolute loader path that does not exist on Android, and `/` is read-only. `pyinstall.sh` resolves the wheel
from PyPI and extracts its pure-Python contents into a shared `pylib` directory. Distributions containing
compiled extensions are rejected rather than bound to a mismatched ABI.

**Asset extraction.** `aapt2` discards asset directories whose names begin with `_`, which removes
`zipfile/_path`, `pip/_internal` and similar paths from the packaged standard library. Those directories are
stored as `x__<name>` and restored during extraction.

## Environment provisioning

The bundled toolchain is a floor, not a ceiling. When a task requires something absent, the agent acquires it
on the device, without host involvement and without asking the user to prepare anything in advance.

| Channel | Mechanism |
|---|---|
| Arbitrary HTTP | `netrun` wraps `curl`; any reachable URL can be fetched |
| Source checkouts | `git clone` over `remote-https` |
| Alpine packages | `zafiro_env apk` runs `apk add` inside the chroot — JDK, headers, system libraries |
| Python distributions | `pyinstall.sh` resolves a wheel from PyPI and unpacks it into `pylib` |
| Extension packs | `zafiro_env install` extracts a `.tar.gz` into the tool root |
| Ad-hoc binaries | Statically linked musl builds execute directly once marked executable |

Two limits are enforced rather than documented as caveats. Distributions carrying compiled extensions are
rejected, because the interpreter is musl-linked and a glibc wheel would bind to a mismatched ABI. Anything
requiring a loader path the platform does not expose fails immediately instead of degrading silently.

Provisioned state persists in the private tool root, so a capability acquired in one session remains available
to later ones.

The practical consequence is worth stating plainly: a pure-Python library installs in seconds, while a library
that requires a C build is either supplied by Alpine's package index or is not available at all. Native
capability is reached through the bundled binaries instead — `curl`, `jq`, `sqlite3`, `rg` and the rest cover
the common cases that would otherwise need an extension module.

## Build loop

The toolchain is sufficient to produce the application's own artifact. A request against the source tree
proceeds entirely on-device: locate the file, edit it, run the test suite, invoke Gradle, emit an APK. No host
machine participates and no external build service is contacted.

```text
request → shell        rg -n 'shields.io' README.md
        → view_image   /data/local/aitools/out/preview.png
        → shell        sed -i 's/…/…/' README.md
        → zafiro_env   build    →  gradle :app:assembleDebug, background
        → zafiro_env   status   →  BUILD SUCCESSFUL, RC=0
        → build complete, new APK produced
```

The Alpine rootfs, JDK 17 and Android SDK platforms 35 / 36 / 37.0 are provisioned on first use. Only components
that cannot be fetched at runtime (`aapt2`, its shim, the signing and no-lint init scripts) are packaged as
assets.

## Agent runtime

A turn is represented as a typed event stream rather than parsed text. Tool invocations, context compaction,
sub-agents and context pressure are modelled as first-class state.

- Conversations persist across process termination; idle sessions release their model slot
- Retry attempts are bounded by configuration
- Provider, base URL, temperature, reasoning effort and timeouts are configured per model
- Credentials are user-supplied; no endpoint is hardcoded

## Conversation management

Long sessions are handled by summarisation rather than truncation.

- **Automatic compaction.** When context occupancy reaches a configured fraction of the model window — 70% by
  default, adjustable in runtime settings, or disabled — the engine summarises the earlier portion through a
  separate model call and retains the most recent turns verbatim. The compaction point is recorded as a divider
  in the conversation timeline, so the boundary between summarised and original history stays visible.
- **Manual compaction.** Sending `/compact` forces the same path regardless of current occupancy.
- **Repetition detection.** Seven sensitivity levels, from off to strictest, suppress degenerate output loops.
- **Incremental output.** Model responses stream as they are produced, and a tool that is still running
  reports its output as it arrives rather than waiting for the command to exit. Long builds and test runs are
  observable while they happen.
- **Persistence.** Conversations resume at the last turn after process termination.

## Tools

Twelve tools, each individually switchable.

`shell` · `zafiro_env` · `screen_operation_accessibility` · `screen_operation_shell` · `screenshot` ·
`view_image` · `launch_app` · `find_installed_apps` · `notify` · `open_uri` · `load_skill` · `memory`

`zafiro_env` mediates the build environment through seven operations: `check` (enumerates 21 toolchains),
`build`, `status`, `install`, `shell`, `apk`, `snapshot`. Screen control uses the accessibility service for
native view hierarchies, and falls back to coordinate input through `su` when a hierarchy is not exposed.

## Skills

Markdown documents loaded on demand. A skill that is never requested contributes no context.

| Skill | Purpose |
|---|---|
| `build` | The single entry point for compilation: Gradle, AGP, SDK, NDK, `aapt2`, signing, APK inspection |
| `hosttools` | Inventory of the bundled CLI surface, so the model does not assume availability |
| `zafiro-cmd` | Canonical tool-layer command syntax, so the model does not hand-roll `su` and `chroot` |
| `skill-creator` | Authoring, editing and testing skills |
| `tool-config` | Reading and modifying application settings through sandbox JSON |

## Interface

Compose throughout. Material 3 Expressive with Liquid Glass and Capsule surfaces, light / dark / system theme,
six locales selectable per application, and a floating ball for quick invocation. The terminal is `libterm`
with local, libsu, Shizuku and SSH backends.

## Architecture

| Module | Contents |
|---|---|
| `app/` | UI, settings, repositories, tool bindings, `MainActivity` |
| `agent-runtime/` | Turn engine, tool registry, compaction, streaming |
| `business/api/` | Shared models and utilities |
| `libs/libterm/` | Terminal core, runtime, backends |
| `assets/hosttools/` | The 1,579-file toolchain and its `VERSION` fingerprint |
| `assets/toolchain/` | `aapt2` and its musl `libz` |
| `assets/buildkit/` | Gradle init scripts for signing and lint suppression |
| `assets/skills/` | The five skill documents |

Four constraints govern changes to this tree:

- **Fingerprints hash content.** Extraction is keyed on the SHA-1 of the asset manifest rather than an install
  timestamp, so an application update does not re-extract 113 MB. Individually missing critical assets are
  repaired in isolation.
- **One root per package.** `/data/local/aitools` for a primary install, `aitools-btN` for a test install. Two
  installations never share state.
- **Signing resolves in order.** `-PRELEASE_STORE_FILE`, then `keystore/debug.keystore`, then
  `keystore/release.keystore`, then the AGP default. A clean clone builds without local paths.
- **CI verifies the repository.** `verify_repo.py` asserts documentation completeness, absent keystores,
  absent personal paths and no tab characters. `verify_hosttools.py` recomputes the asset fingerprint against
  `VERSION`.

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

## License

MIT. See [LICENSE](LICENSE).
