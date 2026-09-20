# Second Architecture Transfer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete ProxmarkDesk's second P0 architecture migration while preserving existing working RF features.

**Architecture:** Keep ClientService as the process owner, but make CommandSessionManager the single synchronization authority. Predefined UI features execute stable Action Registry IDs through ActionExecutor. The existing legacy screens remain internal routes while five primary navigation areas become the user-facing shell.

**Tech Stack:** Java/Android SDK 30–36, embedded Iceman v4.21611, Python host regression tests, standalone javac tests.

**Spec:** `docs/superpowers/specs/2026-09-16-second-architecture-transfer-design.md`

## Global Constraints

- Do not reconnect Proxmark3 after ordinary command errors, card absence, auth failure, Stop, or timeout.
- Physical USB detach remains a separate Android USB event/state.
- Only one command may own the PM3 stream at a time.
- Manual Console remains available for arbitrary commands; predefined application buttons use logical actions.
- Preserve existing data files and compatibility with Iceman v4.21611.

---

### Task 1: Command synchronization invariant

**Files:**
- Modify: `tests/CommandSessionManagerTests.java`
- Modify: `src/org/proxmarkdesk/android/session/CommandSessionManager.java`

**Interfaces:**
- Consumes: `submit`, `cancel`, `onLine`
- Produces: strict one-command ownership and marker-based resynchronization

- [ ] Add failing tests for busy submit, timeout lockout, cancel lockout, and recovery after marker.
- [ ] Run the command session test and confirm the new checks fail on 1.8.
- [ ] Add a per-command synchronization future; never evict an active command from `submit`.
- [ ] Preserve marker ownership after timeout until the end marker arrives.
- [ ] Make `cancel` wait for synchronization rather than the already-completed result future.
- [ ] Re-run tests and confirm all command session checks pass.

### Task 2: Finish predefined logical action migration

**Files:**
- Modify: `tests/ActionRegistryTests.java`
- Modify: `src/org/proxmarkdesk/android/capability/ActionRegistry.java`
- Modify: `src/org/proxmarkdesk/android/MainActivity.java`
- Modify: `src/org/proxmarkdesk/android/ReadingPage.java`
- Modify: `src/org/proxmarkdesk/android/FeaturePages.java`

**Interfaces:**
- Consumes: `ActionExecutor.run(actionId, params, timeout)`
- Produces: stable IDs for tune/version/status/sniff/trace/Lua-env/Classic dump/save helpers

- [ ] Add registry tests for the new stable IDs and exact generated commands.
- [ ] Add only the application-owned action definitions required by migrated buttons.
- [ ] Replace direct command construction in those predefined buttons with ActionExecutor calls.
- [ ] Keep the manual Console/catalogue parameter UI as the explicit arbitrary-command path.
- [ ] Run registry and source-regression tests.

### Task 3: Five-area primary shell and stale-context guard

**Files:**
- Modify: `tests/SourceRegressionTests.py`
- Modify: `src/org/proxmarkdesk/android/MainActivity.java`
- Modify: `src/org/proxmarkdesk/android/ReadingPage.java`

**Interfaces:**
- Produces primary areas: `Карта`, `Ключи`, `Операция`, `Библиотека`, `Инструменты`

- [ ] Add failing source tests asserting the five labels and absence of the old 12-item primary nav array.
- [ ] Replace primary navigation with the five areas while retaining legacy internal page routes.
- [ ] Add a Tools hub linking Device, Sniff, Commands, Emulation, BVD, Signals, Lua scenarios, Python, About.
- [ ] Add a Keys hub that uses current capability/tag state and links to reading/key workflows without copying stale UID.
- [ ] Ensure a new search clears live action context before command dispatch.
- [ ] Run source regressions.

### Task 4: Documentation and verification

**Files:**
- Create: `ARCHITECTURE-1.9.ru.md`
- Create: `CHANGES-1.9.ru.md`
- Modify: `tests/run-1.8-checks.sh` -> version-neutral 1.9 output text

- [ ] Document the second transfer and known intentionally-unmigrated areas.
- [ ] Run all host-safe tests.
- [ ] Run encoding scan.
- [ ] Package the complete Termux source tree as a new 1.9 source ZIP.
- [ ] Generate SHA-256 for the ZIP.
