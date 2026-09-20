# Migration — Iceman v4.21611 to v4.23346

The native client, packaged resources and Android CLI compatibility layer were migrated.

## Runtime catalogue
- old baseline: 913 command rows
- v4.23346: 1024 command rows
- comparison: 120 added, 9 removed

## CLI compatibility examples
```text
hf mfu info --noauth  -> hf mfu info
hf mfu cchk -f FILE   -> hf mfu chk -f FILE
hf mfu aeschk -f FILE -> hf mfu chk -f FILE
```

Simulation workflows:
```text
MIFARE Classic: eclr -> eload -> sim
MFU:             eclr -> eload -> sim
ISO15693:                eload -> sim
```

A command-prefix issue involving `sc` and `script...` was also corrected.

## Native build
Android/aarch64, CMake/Ninja/Clang. Verified DT_NEEDED:
`libm.so`, `libc++_shared.so`, `libdl.so`, `libc.so`.

The build uses `-Wl,-z,max-page-size=16384`.

## Resources
The migrated resource preparation produced 563 packaged files, including 24 Lua libraries and 81 Lua scripts.

## Firmware package
The updater accepts `firmware.properties`, `bootrom.elf` and `fullimage.elf`, validates metadata/SHA-256 and checks ELF32 little-endian ARM structure and target load ranges.
