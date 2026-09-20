# Test status

## Verified on real hardware
Target: Proxmark3 Easy / PM3GENERIC / AT91SAM7S512 over Android USB OTG.

Confirmed:
1. Android ARM64 Iceman v4.23346 starts.
2. USB OTG detects Proxmark3 and `/dev/ttyACM0` is available.
3. The v4.23346 client communicates with the device and `hw version` works.
4. v4.23346 `fullimage.elf` was successfully flashed from the Android updater.
5. USB disconnect/re-enumeration occurred correctly and the device returned.
6. The OS then reported v4.23346 while the old bootrom remained v4.21611.
7. v4.23346 `bootrom.elf` was successfully flashed in bootrom-only mode.
8. After reconnect, both Bootrom and OS reported v4.23346.
9. The final 1.10.7 APK builds successfully after the consolidated updater changes.

Final observed state:
```text
Firmware: PM3 GENERIC
Bootrom: Iceman/master/v4.23346-suspect
OS:      Iceman/master/v4.23346-suspect
uC: AT91SAM7S512 Rev A
Embedded flash: 512K
```

Tested firmware hashes:
```text
bootrom.elf
4e6191b78894b63125138e65d5a94d17b5399587f3d08935030755dccaff0b72

fullimage.elf
d8945f44b2bb03f9a08b071cb02e85196d210e51a4e757b511f9dcf45e2f9497
```

## Still requiring a fresh end-to-end destructive test
The final combined flow:
`bootrom -> USB re-enumeration -> fullimage -> USB re-enumeration -> hw version -> verify Bootrom + OS`
was introduced after the successful individual flashing tests. Its individual stages are verified, but the combined sequence still needs a fresh complete upgrade test.

## Scope limitation
Firmware updating is intentionally restricted to PM3GENERIC / AT91SAM7S / Proxmark3 Easy until other targets are separately validated.
