# Zafiro

An Android AI agent that runs on your phone — and can operate it.

Zafiro is a chat-driven agent app that ships its own userspace toolchain, executes real
commands on the device, and can even build Android APKs from inside itself.

---

## What makes it different

**The toolchain travels with the app.** A 113 MB toolchain is packed into the APK and unpacked
into private storage on first launch: CPython 3.12.13, ripgrep, jq, 7z, curl, sqlite3, `file`,
plus 305 busybox applets. No Termux, no separate install, no manual setup. Grant root and the
agent has a real shell with real tooling.

**It can rebuild itself.** Zafiro can bootstrap an Alpine chroot, install a JDK and the Android
SDK, and assemble its own APK on the device. The build environment is a tool the agent can drive.

**It has hands.** Screen reading and interaction are first-class tools — via the accessibility
service for structured UI trees, or via shell input for coordinates and key events.

---

## Core capabilities

### Agent runtime
Tool-calling loop with streaming output, retry, cancellation and room-backed conversation
persistence. Prompts are composed in stable/volatile layers so the environment block can change
without invalidating the cache.

### Built-in tools

| Tool | What it does |
|---|---|
| `shell` | Run commands through the on-device toolchain (root-capable) |
| `open_uri` | Open a URL, geo, tel or mailto URI |
| `launch_app` | Start an installed app by name or package |
| `find_installed_apps` | Resolve installed packages |
| `notify` | Post a system notification |
| `load_skill` | Pull a skill document into context on demand |
| `memory` | Persist durable facts across sessions |
| `screenshot` | Capture the current screen |
| `view_image` | Read an image file from disk |
| `zafiro_env` | Inspect toolchains and build environments; start on-device builds |
| `screen_operation_accessibility` | Read and drive the UI tree via accessibility |
| `screen_operation_shell` | Tap / swipe / keyevent by coordinates |

### Skills
Markdown skill documents ship inside the APK and load on demand, keeping the system prompt small
while making deep, task-specific knowledge available when the agent asks for it.

### Interface
Jetpack Compose with Material 3 Expressive, Liquid Glass surfaces and Capsule rounded corners.
Chat, history, usage, and a fully reorganised tool settings surface with per-tool switches.

---

## Requirements

- Android 8.0+ (`minSdk 26`), Android 14–16 recommended
- Root (KernelSU or Magisk) for the full toolchain · without it the app runs, but private-storage
  binaries cannot be executed by the app process itself
- Accessibility service permission for screen operation tools
- `INTERNET` for model APIs

The app writes only to its own private storage — no storage permission needed.

---

## Build

```sh
./gradlew :app:assembleDebug
```

Requires JDK 17 and Android SDK platform 37 / build-tools 36. The APK lands in
`app/build/outputs/apk/debug/`.

Release signing: create `keystore/release.keystore` or set `ZAFIRO_KEYSTORE` to an absolute path.
Without either, the build falls back to the standard debug keystore.

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

---

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.2.0 · Jetpack Compose · Material 3 Expressive · Liquid Glass · Capsule |
| Build | AGP 9.1.1 (built-in Kotlin) · KSP · `compileSdk 37` · `minSdk 26` · `targetSdk 34` |
| Agent | OKIA runtime · kotlinx-serialization · OkHttp |
| Storage | Room 2.7.2 |
| Terminal | libterm (core / runtime / libsu / shizuku / ssh backends) |
| Images | coil-resvg |
| On-device Python | CPython 3.12.13 (musl), bundled, stdlib + packaged wheels |

---

## License

See `LICENSE`.
