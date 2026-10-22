# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [33] - 2026-10-23

### Added
- On-device toolchain bundled in the APK: CPython 3.12.13, ripgrep, jq, 7z, curl, sqlite3,
  `file`, and busybox applets, unpacked into private storage on first launch.
- Self-hosted build environment: the app can bootstrap an Alpine chroot with JDK and Android SDK
  and assemble its own APK.
- Twelve built-in tools covering shell, app launching, notifications, screen capture, image
  reading, skills, memory, environment inspection, and two screen-operation modes.
- Skill documents loaded on demand via `load_skill`.
- Portable signing configuration with environment-variable and in-repo keystore support.

### Changed
- Tool settings surface reorganised into a flat list with per-tool switches.
- Log tags unified under the `zafiro_` prefix.

### Fixed
- Bundled Python package directories that began with an underscore are now restored correctly
  during unpacking instead of being dropped by the packaging step.
- The unpack fingerprint is derived from content, so app updates no longer re-extract the whole
  toolchain.
- `.gitignore` patterns no longer drop bundled Python files, so a fresh clone carries a complete
  toolchain and matches the shipped fingerprint.

[Unreleased]: https://github.com/Hoerire/zafiro/compare/v33...HEAD
[33]: https://github.com/Hoerire/zafiro/releases/tag/v33
