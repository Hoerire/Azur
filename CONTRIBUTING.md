# Contributing

Thanks for looking at Zafiro. This document covers the practical parts.

## Before you start

- **Search existing issues** and discussions first.
- For anything larger than a bug fix, open an issue describing the change before writing code.
  Architecture questions are cheaper to settle before a PR exists.

## Development setup

Requirements: **JDK 17**, **Android SDK platform 37**, **build-tools 36**.

```sh
git clone https://github.com/Hoerire/zafiro.git
cd zafiro
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/`.

### Signing

The build picks a keystore in this order:

1. `ZAFIRO_KEYSTORE` environment variable (absolute path)
2. `keystore/debug.keystore`
3. `keystore/release.keystore`
4. the default debug keystore Gradle generates under `~/.android`

Never commit a keystore.

## Code style

- Kotlin official style; 4-space indent, no tabs.
- One public type per file, file name matches the type.
- Prefer immutable data and pure functions in `agent-runtime`; side effects belong at the edges.
- No new dependencies without a short note in the PR explaining why the stdlib or an existing
  dependency is not enough.

## Tests

```sh
./gradlew :agent-runtime:testDebugUnitTest
./gradlew :app:testDebugUnitTest
```

Robolectric-based tests need an x86_64 host — they cannot run on arm64 devices without
`nativeruntime`.

## Pull requests

- One logical change per PR. Reformat-only PRs are discouraged.
- Describe **what breaks without this change**, not just what the change does.
- Include the commands you ran to verify, and their output.
- Update `AGENTS.md` or the skill docs when you change behaviour those documents describe.

## Commit messages

Imperative subject under 72 characters, then a body explaining the reasoning:

```
Add shell timeouts to ShellBuiltin

Commands could hang forever and block the tool loop. Default to 120s and
let the model override per call.
```

## Reporting bugs

Use the bug report template. Include:

- App version (`versionCode` from Settings → About)
- Android version and ROM
- Root provider and whether it was granted
- Contents of `files/hosttools.diag` if toolchain behaviour is involved
- `logcat -b crash -d` if the app crashed

## License

By contributing you agree your work is licensed under the MIT license in `LICENSE`.
