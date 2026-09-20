# ProxmarkDesk BVD 1.10.7 — Developer Source Package

Android application for operating Proxmark3 directly from a smartphone over USB OTG.

## Current scope
- Android package: `org.proxmarkdesk.android`
- minSdk 30 / targetSdk 36
- Android ARM64 native Proxmark3/Iceman client
- migrated client/resources: Iceman v4.23346
- validated hardware: Proxmark3 Easy / PM3GENERIC / AT91SAM7S512
- Card / Operations / Library / Tools UI
- Iceman console and runtime command catalogue
- dump library, sniff/trace, emulation and LF signal tools
- Lua resources and command sequences
- embedded CPython ARM64 integration
- BVD profile/trace analyzer
- Android firmware updater

## Layout
- `ProxmarkDesk-BVD-Analyzer/` — application sources, tests, resources and runtime assets
- `build-termux.sh` — APK build entry point
- `build-native-termux.sh` — native Proxmark3 build
- `prepare-resources.py` — resource preparation
- `docs/` — bilingual technical reports and source manifest
- `TESTING.md` — verified real-device results
- `MIGRATION-v4.23346.md` — migration notes

## Release/security note
No signing keystore or private signing key is included. The local build script can create a development keystore.

The firmware updater is currently constrained to PM3GENERIC / AT91SAM7S / Proxmark3 Easy. Other hardware targets require separate validation.
