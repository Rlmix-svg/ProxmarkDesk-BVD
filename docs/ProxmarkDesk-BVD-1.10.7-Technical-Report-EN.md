# ProxmarkDesk BVD 1.10.7 — Technical Architecture and Test Report

## 1. Project purpose

ProxmarkDesk BVD is a standalone Android application for operating a Proxmark3 directly from a smartphone over USB OTG. The key architectural decision is to avoid reimplementing the Proxmark3 protocol stack in Java. Instead, the APK contains a native Android ARM64 build of the Proxmark3/Iceman client, with an Android UI, file library, operation automation, analysis tools and firmware updater built around it.

Current APK version: **1.10.7**. Android package: `org.proxmarkdesk.android`. `minSdkVersion=30`, `targetSdkVersion=36`. USB host support is required. The hardware profile actually used for real-device testing is **Proxmark3 Easy / PM3GENERIC / AT91SAM7S512**.

The embedded client was migrated from Iceman v4.21611 to **Iceman v4.23346**.

## 2. High-level architecture

```text
Android UI / MainActivity
        │
        ├── Card / ReadingPage
        ├── Operations / OperationPage
        ├── Library / DumpLibrary + LibraryMirror
        ├── Tools
        │     ├── Console
        │     ├── Sniff / traces
        │     ├── PM3 Commands
        │     ├── Emulation
        │     ├── BVD Analyzer
        │     ├── Signals
        │     ├── Lua sequences
        │     ├── Python
        │     └── Device information
        └── Firmware updater
                 │
Action / capability layer
ActionRegistry + CapabilityEngine + ActionExecutor
                 │
ClientService + CommandSessionManager
                 │
native Proxmark3/Iceman ARM64 client
                 │
/dev/ttyACM0 / USB OTG / root
                 │
Proxmark3
```

`ClientService` is the central service. It manages the native client, USB/session state, commands, console output, result logs, resources, the Python bridge, firmware updates and reconnection.

`PMDESK_PIPE=1` is used for application/native-client integration. The native client is launched with options such as `--incognito --flush --port /dev/ttyACM0`.

The application does not implement a separate Java PM3 serial protocol stack. Actual device communication is performed by the upstream Proxmark3 client through `/dev/ttyACM0`.

## 3. Primary navigation

The persistent top navigation contains four main sections:

| Section | Purpose |
|---|---|
| **Card** | HF/LF discovery, family detection and context-aware command selection |
| **Operations** | actions for the currently confirmed card |
| **Library** | dumps, import, analysis, export and file management |
| **Tools** | USB, console, sniffing, full PM3 catalogue, emulation, BVD, signals, Lua, Python and device information |

Client/session state is displayed separately and refreshed approximately every 500 ms together with command progress.

## 4. Card screen

Implemented by `ReadingPage`.

Quick actions include automatic discovery, HF search and LF search. After a tag is detected, the UI displays the tag summary/UID, provides a detailed report, opens card-specific actions and allows manual family selection if automatic classification is ambiguous.

The searchable action catalogue can be filtered by Command, Lua, Python, special variants and favorites. Each entry can expose parameters, `-h`, Lua/Python source and execution.

## 5. Operations screen

`OperationPage` is a context-sensitive UI built on `ActionRegistry` and `CardCapability`.

A cached historical tag is deliberately not treated as a current physical tag. Operations requiring a current card are only enabled after a fresh confirmed detection.

The screen presents subtype/family, UID, sectors, memory size, key availability, partial-access status and capability verification, with shortcuts to Keys and Sniffer.

Actions are grouped into categories such as:

- Reading and information;
- Keys and authentication;
- Dictionaries / keys;
- Capture and emulation.

The registry contains actions for MIFARE Classic, Ultralight/NTAG, ISO15693, DESFire, ISO14443-B, FeliCa, iCLASS/PicoPass, LEGIC, Topaz, EM410x, HID Prox, T55xx, MIFARE Plus and dictionary-based checks.

Examples include Classic key checks, nested, autopwn, dumps and block reads; MFU info/dump/PWD_AUTH/emulation; ISO15693 info/dump/emulation; DESFire/FeliCa/iCLASS/LEGIC/Topaz information; LF readers; T55xx information; and user dictionary checks for several HF/LF families.

Parameters such as UID, block, sector, card size, dump path and dictionary path can be suggested from current capability state.

## 6. Device / USB screen

The screen exposes:

- serial device, default `/dev/ttyACM0`;
- root USB access toggle;
- USB/tty discovery;
- offline native-client mode;
- stop session;
- export USB diagnostics;
- firmware update;
- device library profile;
- SAF mirror folder selection;
- library backup;
- licenses/instructions.

Automatic connection is supported on application startup and USB attach events.

On the tested system, Proxmark3 was available as a CDC ACM device through `/dev/ttyACM0`.

## 7. Iceman console

The console provides direct access to the embedded CLI, including arbitrary command entry, input validation, live output, command highlighting, long-operation progress, elapsed time, console export and persistent draft input.

This means native-client functionality remains accessible even when a dedicated GUI action has not yet been implemented.

## 8. PM3 Commands / runtime catalogue

The application can regenerate its catalogue directly from the embedded client through `ClientService.catalogue()`.

The v4.23346 runtime catalogue contained **1024 commands**, compared with 913 in the previous application version. Migration analysis identified 120 added and 9 removed commands.

The UI provides search, Russian descriptions, family filtering, `-h`, parameter entry and console handoff.

## 9. Sniffing and traces

UI protocol choices include ISO14443-A/Classic/NTAG, ISO14443-B, ISO15693, FeliCa, iCLASS, Topaz and LF signal.

Features include manual sniffing, timed ISO14443-A auto-sniff, protocol selection from the detected tag, trace decoding, capture saving, sniff help and PWD_AUTH analysis from a file.

The auto-sniff workflow performs capture → timer → `hw break` → trace display/analysis → trace save.

## 10. Library, dumps and files

The Library/Dumps UI supports BIN/JSON/EML/signal import, search by UID/name, analysis, emulation, export, rename, UID-based rename and deletion.

Original files are stored in the private application library. The user can additionally select a folder through Android SAF; `LibraryMirror` creates device-specific directories for dumps, traces, signals, profiles, passwords, logs, reports, scripts and backups.

A complete ZIP backup of the library is supported.

## 11. Emulation

Saved BIN/JSON/EML files can be loaded for:

- Classic Mini;
- Classic 1K;
- Classic 2K;
- Classic 4K;
- Ultralight;
- Ultralight EV1 / NTAG;
- Ultralight C;
- Ultralight AES;
- ISO15693.

The v4.23346 migration updated simulation workflows so emulator memory is prepared before `sim` where required.

## 12. Signal processing

The Signals screen provides LF acquisition, saved-signal access, `lf config`, buffer decoding/saving and PM3 `data` processing tools including raw demodulation, clock detection, autocorrelation, normalization, decimation, trimming, HPF, IIR and bitstream extraction.

## 13. Lua

The APK packages current Proxmark3 Lua resources. The migrated resource set contains **563 files**, including **24 Lua libraries** and **81 Lua scripts**.

The UI provides environment diagnostics, resource repair, non-RF Lua loading checks, library/script browsing, parameterized Lua execution, source viewing and saved `.cmd` command sequences.

Command sequences execute serially, with configurable stop/continue behavior after client warnings/errors.

## 14. Embedded Python

The audited 1.10.7 source contains **CPython 3.14.7 ARM64** embedded in the APK, without requiring Termux.

Bundled scripts are:

1. `python_selftest.py`
2. `device_report.py`
3. `dump_inventory.py`
4. `pm3_eml2mfd.py`
5. `pm3_mfd2eml.py`
6. `pm3_nfc2eml.py`
7. `findbits.py`
8. `parity.py`
9. `xorcheck.py`
10. `pm3_help2json.py`
11. `pm3_help2list.py`

The UI supports arguments/timeouts, importing a custom `.py`, source viewing, Android-dialog `input()`, report export and Python/library licenses.

A `pm3` adapter allows Python code to issue commands through the current Iceman session.

pip, arbitrary third-party packages and the complete native SWIG API are not claimed as supported.

## 15. BVD Analyzer

BVD Analyzer is a research-oriented NTAG/Ultralight profile and trace comparison module.

It can create profiles from saved dumps, import dumps, record a user-observed outcome, store optional known PWD/PACK, compare profiles, import and compare traces, save the current HF trace, generate reports, export JSON and delete profiles.

The analyzer intentionally does not treat UID/PWD/page values alone as proof of authorization or infer why an external reader accepted or rejected a tag.

## 16. History and result cache

`ResultCache` stores operation metadata and full/partial output. History can be searched by UID, command, description and result text.

Records include status, command, UID, device, start time and log. Full output can be exported and a previous command can be reopened with parameters.

## 17. Firmware updater

The firmware updater is integrated into the Device screen.

Accepted package layout:

```text
firmware.properties
bootrom.elf
fullimage.elf
```

`FirmwarePackage` validates metadata, SHA-256 and ELF structure. The current target is:

```text
format=1
platform=PM3GENERIC
chip=AT91SAM7S
version=v4.23346
```

The normal 1.10.7 workflow is designed as:

```text
bootrom
  ↓
USB re-enumeration
  ↓
fullimage
  ↓
USB re-enumeration
  ↓
hw version
  ↓
verify Bootrom + OS
```

A bootrom-only recovery mode is retained.

The user must explicitly confirm the Proxmark3 Easy / AT91SAM7S target and trusted PM3GENERIC package origin.

## 18. Migration from Iceman v4.21611 to v4.23346

The native client, resources and CLI compatibility layer were migrated.

Examples:

```text
hf mfu info --noauth  →  hf mfu info
hf mfu cchk -f ...    →  hf mfu chk -f ...
hf mfu aeschk -f ...  →  hf mfu chk -f ...
```

Classic, MFU and ISO15693 simulation workflows were updated, and a command-prefix issue involving `sc` versus `script...` was fixed.

## 19. Native build

The v4.23346 client is built for Android/aarch64.

Verified dynamic dependencies:

```text
libm.so
libc++_shared.so
libdl.so
libc.so
```

OpenJPEG is not a dynamic dependency.

The native build uses CMake/Ninja/Clang and:

```text
-Wl,-z,max-page-size=16384
```

for Android 16 KiB page compatibility.

## 20. APK build pipeline

```text
Proxmark3 v4.23346 source
        ↓
CMake / Ninja / Clang
        ↓
Android ARM64 native client
        ↓
prepare-resources.py
        ↓
Java sources
        ↓
OpenJDK / Android API 36
        ↓
D8
        ↓
AAPT2
        ↓
APK packaging
        ↓
zipalign / apksigner
```

Java source compatibility is `-source 8 -target 8`.

## 21. Positive real-device tests completed

On a real Proxmark3 Easy / AT91SAM7S512, the following were confirmed:

1. Android ARM64 Iceman v4.23346 starts successfully.
2. Android USB OTG detects the Proxmark3.
3. `/dev/ttyACM0` is available.
4. The new client communicates with the device.
5. `hw version` works.
6. v4.23346 `fullimage.elf` was successfully flashed from the Android updater.
7. USB disconnect/re-enumeration occurred correctly during flashing.
8. The device reconnected after fullimage flashing.
9. The OS reported v4.23346.
10. v4.23346 `bootrom.elf` was successfully flashed in the recovery/bootrom-only mode.
11. The bootrom subsequently reported v4.23346.
12. Final device state was:

```text
Firmware: PM3 GENERIC
Bootrom: Iceman/master/v4.23346-suspect
OS:      Iceman/master/v4.23346-suspect
uC: AT91SAM7S512 Rev A
Embedded flash: 512K
```

13. USB re-enumeration works in the native flashing workflow.
14. The final 1.10.7 APK builds successfully after integration of the consolidated updater workflow.

## 22. Validation still outstanding

The newly consolidated automatic sequence **bootrom → reconnect → fullimage → reconnect → verify** was added after the successful individual bootrom/fullimage tests. It still requires a separate full destructive/end-to-end test on a device that actually needs upgrading.

The firmware updater is intentionally restricted to PM3GENERIC / AT91SAM7S / Proxmark3 Easy. Other hardware targets should only be enabled after target-specific validation.

The presence of a command in the Iceman catalogue does not imply that every Proxmark3 Easy hardware configuration physically supports it.

## 23. Main source components

| Component | Role |
|---|---|
| `MainActivity.java` | main UI/navigation, USB events, console, files, sniffing, BVD |
| `ClientService.java` | native client lifecycle, session, commands, USB, firmware, Python |
| `ReadingPage.java` | card screen and searchable command/script catalogue |
| `OperationPage.java` | capability-aware card operations |
| `ActionRegistry.java` | declarative actions and CLI templates |
| `CapabilityEngine.java` | card capability derivation |
| `CommandSessionManager.java` | command/session state |
| `TagInfo.java` | detected-tag model |
| `DumpLibrary.java` | dump management |
| `LibraryMirror.java` | SAF export/mirroring |
| `ResultCache.java` | history, metadata and logs |
| `AutoSniff.java` | automated sniff workflow |
| `FirmwarePackage.java` | firmware ZIP/SHA/ELF validation |
| `FirmwareUpdater.java` | flashing/reconnect/version verification |
| `PythonPage.java` / `AndroidPython.java` | embedded CPython UI/runtime |
| `PythonBridge.java` | Python-to-current-PM3-session bridge |
| `BvdProfiles.java` + `bvd/*` | profiles, comparisons, trace analysis/export |
| `prepare-resources.py` | Proxmark3 resource preparation |
| `build-termux.sh` | APK build |
| `build-native-termux.sh` | native Proxmark3 build |

## 24. Suggested future development

Potential upstream-oriented work includes:

- keeping the Android client synchronized with Proxmark3 releases;
- automated CLI compatibility audits;
- capability-based UI rather than release-specific behavior;
- additional validated hardware profiles;
- cryptographically signed firmware packages;
- stricter hardware-target checks before flashing;
- improved USB recovery/reconnect;
- automated UI-to-CLI integration tests;
- expanded dump/trace analysis;
- expanded Python/Lua workflows;
- a reproducible upstream-friendly Android build pipeline.

The current source tree, this report and real-device test logs can be supplied to the Proxmark3 developers for review.
