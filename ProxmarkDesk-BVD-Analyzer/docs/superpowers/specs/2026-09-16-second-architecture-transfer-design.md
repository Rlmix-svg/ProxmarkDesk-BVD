# ProxmarkDesk BVD — Second Architecture Transfer Design

## Goal

Finish the P0 migration started in 1.8 without deleting working functionality: command execution must be serialized and recoverable after Stop/timeout, logical UI actions must use stable Action Registry IDs, and the primary navigation must match the approved five-area design.

## In scope

1. CommandSessionManager owns command serialization and synchronization. A second command may not evict an active command. After timeout or Stop, no new command starts until the end marker confirms synchronization.
2. USB/Client/Command/Tag remain independent. Command timeout/error must not imply physical USB detach.
3. Primary quick actions, sniff/diagnostic helpers, Lua environment commands, and Classic dump route through ActionExecutor/ActionRegistry where they are predefined application actions. The manual Console remains the intentional escape hatch for arbitrary expert commands.
4. The app exposes five primary areas: Карта, Ключи, Операция, Библиотека, Инструменты. Existing feature pages remain reachable from those areas during migration.
5. Search invalidates stale live tag context before a new result is published; cached historical data may remain visibly marked as historical but may not silently supply UID/key context to a new RF action.
6. Stop cancels only the current operation. Console and tag card are preserved; client/USB are not restarted.

## Out of scope

- Full SavedCard database/storage rewrite.
- Universal Dump Viewer protocol-adapter implementation.
- Full LF protocol adapter expansion.
- Firmware changes.
- Removing the manual PM3 Console.

## Verification

Host-safe regression tests must prove:
- busy submit is rejected and does not evict the first command;
- timeout blocks a following command until the old marker is seen;
- Stop blocks a following command until synchronization finishes;
- normal completion permits the next command;
- registered UI action IDs exist;
- primary navigation exposes exactly the five approved areas;
- no UTF-8 BOM/mojibake regressions.
