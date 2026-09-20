// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.session;

import org.proxmarkdesk.android.TagInfo;

/**
 * Immutable snapshot of the full session state, published atomically.
 * The UI reads a single volatile reference to this object instead of
 * polling multiple scattered fields.
 *
 * Build with the inner Builder:
 *   SessionState s = new SessionState.Builder(previous)
 *       .clientState(ClientState.READY)
 *       .statusText("Подключено")
 *       .build();
 */
public final class SessionState {

    public final UsbState usb;
    public final ClientState client;
    public final CommandState command;
    public final TagState tag;
    public final String currentCommand;   // "" when IDLE
    public final long commandStartedAtMs; // 0 when no active command
    public final String statusText;       // human-readable one-liner for the status bar
    public final TagInfo tagInfo;         // null if never searched
    public final boolean tagCached;       // true when tagInfo is from history, not live
    public final ProgressState progress;  // null when no long operation is running
    public final long revision;           // monotonically increasing; UI compares to skip redraws

    private SessionState(Builder b) {
        this.usb = b.usb;
        this.client = b.client;
        this.command = b.command;
        this.tag = b.tag;
        this.currentCommand = b.currentCommand;
        this.commandStartedAtMs = b.commandStartedAtMs;
        this.statusText = b.statusText;
        this.tagInfo = b.tagInfo;
        this.tagCached = b.tagCached;
        this.progress = b.progress;
        this.revision = b.revision;
    }

    /** True when the client is alive and accepting commands (READY or OFFLINE). */
    public boolean isClientAlive() {
        return client == ClientState.READY || client == ClientState.OFFLINE;
    }

    /** True when a command is actively executing (RUNNING, WAITING, CANCEL_REQUESTED). */
    public boolean isBusy() {
        return command == CommandState.RUNNING
            || command == CommandState.WAITING
            || command == CommandState.TIMEOUT
            || command == CommandState.CANCEL_REQUESTED;
    }

    /** True when the Stop button should be shown as active (not greyed). */
    public boolean isStopVisible() {
        return command == CommandState.RUNNING || command == CommandState.WAITING;
    }

    /** True when Stop was pressed and we are waiting for hw break confirmation. */
    public boolean isCancelling() {
        return command == CommandState.CANCEL_REQUESTED;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        UsbState usb = UsbState.DETACHED;
        ClientState client = ClientState.STOPPED;
        CommandState command = CommandState.IDLE;
        TagState tag = TagState.UNKNOWN;
        String currentCommand = "";
        long commandStartedAtMs = 0L;
        String statusText = "";
        TagInfo tagInfo = null;
        boolean tagCached = false;
        ProgressState progress = null;
        long revision = 0;

        /** Start with all-defaults (initial state). */
        public Builder() {}

        /** Copy-and-modify: start from an existing snapshot. */
        public Builder(SessionState previous) {
            if (previous == null) return;
            this.usb = previous.usb;
            this.client = previous.client;
            this.command = previous.command;
            this.tag = previous.tag;
            this.currentCommand = previous.currentCommand;
            this.commandStartedAtMs = previous.commandStartedAtMs;
            this.statusText = previous.statusText;
            this.tagInfo = previous.tagInfo;
            this.tagCached = previous.tagCached;
            this.progress = previous.progress;
            this.revision = previous.revision;
        }

        public Builder usbState(UsbState v)        { this.usb = v; return this; }
        public Builder clientState(ClientState v)  { this.client = v; return this; }
        public Builder commandState(CommandState v){ this.command = v; return this; }
        public Builder tagState(TagState v)        { this.tag = v; return this; }
        public Builder currentCommand(String v)    { this.currentCommand = v == null ? "" : v; return this; }
        public Builder commandStartedAtMs(long v)  { this.commandStartedAtMs = Math.max(0L, v); return this; }
        public Builder statusText(String v)        { this.statusText = v == null ? "" : v; return this; }
        public Builder tagInfo(TagInfo v)          { this.tagInfo = v; return this; }
        public Builder tagCached(boolean v)        { this.tagCached = v; return this; }
        public Builder progress(ProgressState v)   { this.progress = v; return this; }

        public SessionState build() {
            this.revision++;
            return new SessionState(this);
        }

        /** Build without incrementing revision (e.g. initial construction). */
        public SessionState buildInitial() {
            return new SessionState(this);
        }
    }

    // -------------------------------------------------------------------------
    // Initial state constant
    // -------------------------------------------------------------------------

    public static final SessionState INITIAL = new Builder()
        .statusText("Клиент не запущен")
        .buildInitial();
}
