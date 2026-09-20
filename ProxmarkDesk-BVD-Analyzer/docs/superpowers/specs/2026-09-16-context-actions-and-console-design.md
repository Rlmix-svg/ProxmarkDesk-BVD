# Context Actions and Iceman Console Design

## Goal
Replace the legacy generic read/dump form with a capability-driven card menu, expose manual Iceman command input only under Tools, and make long-running command state diagnostically visible without reintroducing a soft Stop button.

## Architecture
The live-card UI follows `TagInfo -> CapabilityEngine -> CardCapability -> ActionRegistry -> OperationPage`. The card screen only discovers and summarizes the current tag; all executable card operations are projected by ActionRegistry for the detected protocol/subtype/capability. Full PM3 catalogue, Lua/Python and a manual Iceman console remain under Tools.

## Rules
- No Normal/Expert mode.
- No soft Stop button. Emergency interruption remains Stop Session on the device screen.
- No generic legacy read/dump form on the card screen.
- Unknown capability is not treated as unsupported.
- Dump/emulation actions that require a selected saved dump do not appear in the live-card action menu.
- Password-specific Ultralight/NTAG actions appear only for PWD-capable subtypes or confirmed PWD auth.
- NDEF actions are hidden only when NDEF is explicitly ABSENT; UNKNOWN remains discoverable.
- Protocols without a mature write/dump adapter expose safe identification/info actions only.
- Timeout does not automatically kill the PM3 client; the stream remains blocked until synchronization returns or the user explicitly stops the session.
- Operations show current command and elapsed runtime while a command is active.
