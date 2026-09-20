# Contributing

ProxmarkDesk BVD is currently in developer-handoff stage.

## Scope

The Android layer intentionally delegates Proxmark3 protocol and command behavior to the embedded native Iceman client instead of reimplementing the protocol in Java.

Changes that affect Iceman CLI templates should be checked against the target upstream client version and the runtime command catalogue.

## Before submitting changes

- keep Android UI/action behavior capability-based where possible;
- preserve the firmware updater hardware guardrails;
- do not broaden firmware targets without real-device validation;
- run the available source regression and Java test suites;
- document changes that alter CLI compatibility or firmware behavior;
- never commit signing keys, personal dumps, device secrets, or private test data.

## Upstream interaction

For changes intended for RfidResearchGroup/proxmark3 itself, follow the upstream project's CONTRIBUTING.md and coding conventions. This repository is a companion Android project and is not presented as an upstream Proxmark3 replacement.
