# Security Policy

## Supported versions

Only the latest release receives security fixes.

## Reporting a vulnerability

**Do not open a public issue for security problems.**

Use GitHub's private vulnerability reporting on the Security tab, or email the maintainer address
on the GitHub profile. Include:

- Affected version (`versionCode`)
- Steps to reproduce
- Impact: what an attacker gains
- Any proof-of-concept, kept as small as possible

You can expect an initial response within 7 days. Please allow time for a fix before public
disclosure.

## Threat model — read this before reporting

Anvil is an agent that runs commands on your device, and it is designed to do so:

| Behaviour | Expected? |
|---|---|
| Executing arbitrary shell commands that the model requests | Yes — that is the product |
| Running the bundled toolchain as root when `su` is granted | Yes, by design |
| Reading the screen through the accessibility service | Yes, when that tool is enabled |
| Writing files under its own private storage | Yes |
| Sending conversation content to the model provider you configured | Yes — you choose the endpoint |

Vulnerabilities we care about:

- Escaping the app's sandbox without user-granted root
- Executing code from a remote source without the user prompting it
- Leaking API keys or conversation content to a third party you did not configure
- Bypassing the per-tool switches in settings
- Path traversal or injection in file handling
- Supply-chain issues in bundled binaries

## Bundled binaries

`app/src/main/assets/hosttools` contains compiled binaries (CPython, ripgrep, jq, 7z, curl,
sqlite3, busybox applets). Their provenance is documented in `assets/hosttools/VERSION`.
Report mismatches between the fingerprint and the shipped files.
