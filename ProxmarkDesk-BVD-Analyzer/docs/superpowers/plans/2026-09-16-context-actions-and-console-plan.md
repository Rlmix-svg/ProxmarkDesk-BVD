# Context Actions and Iceman Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build strict card-specific menus, place the manual console under Tools, and improve long-command diagnostics while preserving the PM3 client across command timeout.

**Architecture:** Card discovery remains in ReadingPage; OperationPage becomes the only live-card action surface and consumes ActionRegistry.forCardMenu(CardCapability). Tools owns full catalogue and console. CommandSessionManager publishes command start time and timeout synchronization state.

**Tech Stack:** Java 8 Android UI, embedded Iceman v4.21611, Python source regression tests, Java host-side tests.

**Spec:** `docs/superpowers/specs/2026-09-16-context-actions-and-console-design.md`

## Global Constraints
- One adaptive app mode.
- No soft Stop button.
- Do not infer unsupported from unknown capability.
- UI does not assemble PM3 command strings directly.
- Physical USB state remains independent from command failures/timeouts.

---

### Task 1: Card-specific action projection
**Files:** Modify `ActionRegistry.java`; Test `ActionRegistryTests.java`.
- [ ] Add failing tests for strict live-card projection.
- [ ] Add `forCardMenu` and subtype/capability filtering.
- [ ] Add safe info actions for currently known protocol families lacking logical actions.
- [ ] Run ActionRegistry tests.

### Task 2: Remove legacy read/dump UI
**Files:** Modify `ReadingPage.java`, `MainActivity.java`, `OperationPage.java`; Test `SourceRegressionTests.py`.
- [ ] Add regression assertions that live card UI does not call `legacyReadPage`.
- [ ] Route current-card operations to OperationPage only.
- [ ] Keep full command catalogue in Tools.
- [ ] Add Iceman Console entry under Tools.
- [ ] Run source regression tests.

### Task 3: Long-command diagnostics and timeout semantics
**Files:** Modify `SessionState.java`, `CommandSessionManager.java`, `ClientService.java`, `OperationPage.java`; tests `CommandSessionManagerTests.java`, `SourceRegressionTests.py`.
- [ ] Add command start timestamp to state.
- [ ] Render current command + elapsed time in Operations.
- [ ] Remove automatic client termination on command timeout.
- [ ] Restore a terminal non-busy state after a late end marker re-synchronizes the stream.
- [ ] Run host-side command/session tests.

### Task 4: Full verification and package
**Files:** package source archive and checksum.
- [ ] Run source regression suite.
- [ ] Run Java host-safe suites.
- [ ] Run Python tests available in container.
- [ ] Create Termux source ZIP and SHA-256.
