# Developer handoff

This repository documents the ProxmarkDesk BVD 1.10.7 Android companion work targeting Iceman v4.23346.

## What is verified

See [TESTING.md](TESTING.md) for the exact real-hardware validation record. Native Android ARM64 client operation, USB OTG communication, fullimage flashing and bootrom flashing were verified individually on PM3GENERIC / AT91SAM7S512.

## What is not yet claimed

The consolidated two-stage updater sequence was implemented after the individual flashing tests. It has not yet received a fresh destructive end-to-end validation on a device requiring upgrade.

## Review priorities

1. Native client synchronization strategy with upstream Iceman.
2. Automated CLI compatibility checks between Android actions and Iceman releases.
3. Firmware package authenticity/signing and recovery behavior.
4. USB reconnect/re-enumeration robustness on additional Android devices.
5. Validation on additional Proxmark3 hardware targets.
6. Automated UI-to-Iceman integration tests.

## Source package provenance

The audited developer package contains 224 files and totals 31,910,937 uncompressed bytes. It includes application Java sources, tests, Python integration, build scripts, runtime assets and bilingual technical documentation. No signing keystore/private signing key is part of the package.
