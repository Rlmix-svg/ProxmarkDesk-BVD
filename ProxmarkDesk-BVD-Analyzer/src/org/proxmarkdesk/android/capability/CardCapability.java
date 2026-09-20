// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.capability;

/**
 * Immutable capability snapshot for a specific card or dump.
 * Built by CapabilityEngine; consumed by ActionRegistry and the UI.
 *
 * Rule: if a capability has not been checked yet, its state is UNKNOWN —
 * never automatically UNSUPPORTED.
 */
public final class CardCapability {

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    public enum AuthType {
        NONE,     // no authentication required
        PWD,      // 4-byte Ultralight/NTAG password
        KEY_A_B,  // MIFARE Classic Key A / Key B
        DES,      // DESFire 2K3DES / 3DES
        AES,      // DESFire AES / Ultralight C
        UNKNOWN   // not yet determined
    }

    public enum NdefState {
        PRESENT,  // NDEF capability container detected
        ABSENT,   // explicitly confirmed absent
        UNKNOWN   // not checked
    }

    public enum EmulateStatus {
        SUPPORTED,    // ready to emulate right now
        UNSUPPORTED,  // not possible with current firmware/protocol
        CONDITIONAL,  // possible when listed conditions are met
        UNKNOWN       // not yet evaluated
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** UID string (uppercase hex), empty if unknown. */
    public final String uid;

    /** TagInfo.FAMILIES index. -1 if unknown. */
    public final int protocol;

    /** Specific subtype, e.g. "NTAG213", "Classic 1K", "T55xx". Empty if unknown. */
    public final String subtype;

    /** Total memory in bytes. -1 if unknown. */
    public final int memorySizeBytes;

    /** Number of sectors (Classic). -1 for non-Classic or unknown. */
    public final int sectors;

    public final AuthType authType;

    /** True when a valid key file (hf-mf-{UID}-key.bin) exists in filesDir. */
    public final boolean hasKeys;

    /** True when only a subset of sectors/pages was readable. */
    public final boolean partialAccess;

    public final NdefState ndefState;

    /** True when the card supports ISO 14443-4 (ATS present). */
    public final boolean iso14443_4;

    /** True when sniff is meaningful for this protocol. */
    public final boolean sniffSupported;

    public final EmulateStatus emulateStatus;

    /** Human-readable reason when emulateStatus is CONDITIONAL or UNSUPPORTED. */
    public final String emulateReason;

    /**
     * True when the capability data came from a live device read (not just heuristics).
     * False means "unknown / not yet verified" — not "not supported".
     */
    public final boolean verified;

    // -------------------------------------------------------------------------
    // Constructor (use Builder)
    // -------------------------------------------------------------------------

    private CardCapability(Builder b) {
        this.uid           = b.uid;
        this.protocol      = b.protocol;
        this.subtype       = b.subtype;
        this.memorySizeBytes = b.memorySizeBytes;
        this.sectors       = b.sectors;
        this.authType      = b.authType;
        this.hasKeys       = b.hasKeys;
        this.partialAccess = b.partialAccess;
        this.ndefState     = b.ndefState;
        this.iso14443_4    = b.iso14443_4;
        this.sniffSupported = b.sniffSupported;
        this.emulateStatus = b.emulateStatus;
        this.emulateReason = b.emulateReason;
        this.verified      = b.verified;
    }

    // -------------------------------------------------------------------------
    // Convenience queries
    // -------------------------------------------------------------------------

    public boolean isClassic() {
        return protocol == 1; // TagInfo.FAMILIES[1] = "MIFARE Classic"
    }

    public boolean isUltralight() {
        return protocol == 2; // "Ultralight / NTAG"
    }

    public boolean isDumpReady() {
        return emulateStatus == EmulateStatus.SUPPORTED;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        String uid = "";
        int protocol = -1;
        String subtype = "";
        int memorySizeBytes = -1;
        int sectors = -1;
        AuthType authType = AuthType.UNKNOWN;
        boolean hasKeys = false;
        boolean partialAccess = false;
        NdefState ndefState = NdefState.UNKNOWN;
        boolean iso14443_4 = false;
        boolean sniffSupported = true;
        EmulateStatus emulateStatus = EmulateStatus.UNKNOWN;
        String emulateReason = "";
        boolean verified = false;

        public Builder() {}

        public Builder uid(String v)               { this.uid = v == null ? "" : v; return this; }
        public Builder protocol(int v)             { this.protocol = v; return this; }
        public Builder subtype(String v)           { this.subtype = v == null ? "" : v; return this; }
        public Builder memorySizeBytes(int v)      { this.memorySizeBytes = v; return this; }
        public Builder sectors(int v)              { this.sectors = v; return this; }
        public Builder authType(AuthType v)        { this.authType = v; return this; }
        public Builder hasKeys(boolean v)          { this.hasKeys = v; return this; }
        public Builder partialAccess(boolean v)    { this.partialAccess = v; return this; }
        public Builder ndefState(NdefState v)      { this.ndefState = v; return this; }
        public Builder iso14443_4(boolean v)       { this.iso14443_4 = v; return this; }
        public Builder sniffSupported(boolean v)   { this.sniffSupported = v; return this; }
        public Builder emulateStatus(EmulateStatus v) { this.emulateStatus = v; return this; }
        public Builder emulateReason(String v)     { this.emulateReason = v == null ? "" : v; return this; }
        public Builder verified(boolean v)         { this.verified = v; return this; }

        public CardCapability build() { return new CardCapability(this); }
    }

    /** Empty/unknown capability used as a safe default. */
    public static final CardCapability UNKNOWN = new Builder().build();
}
