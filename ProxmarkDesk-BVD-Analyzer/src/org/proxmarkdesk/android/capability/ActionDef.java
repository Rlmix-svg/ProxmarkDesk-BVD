// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.capability;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Declarative definition of a logical action that the UI can invoke.
 *
 * The UI calls ActionRegistry.buildCommand(action, params) — it never
 * assembles the pm3 command string directly.
 *
 * Template parameter tokens (replaced by ActionRegistry.buildCommand):
 *   {uid}    — current tag UID (uppercase hex)
 *   {size}   — Classic size flag: mini / 1k / 2k / 4k
 *   {path}   — relative file path inside library (e.g. "dumps/UID-xxx-mfu.bin")
 *   {keyA}   — 12-hex Key A
 *   {keyB}   — 12-hex Key B
 *   {blk}    — block number
 *   {sector} — sector number
 *   {type}   — MFU sim type index (2/7/13/14)
 */
public final class ActionDef {

    public final String id;                   // unique identifier, e.g. "classic.autopwn"
    public final String[] protocols;          // command group prefixes, e.g. {"hf mf"}
    public final String commandTemplate;      // "hf mf autopwn --{size}"
    public final Map<String, String> paramDefaults; // e.g. {"size":"1k"}
    public final String category;             // for UI grouping
    public final String description;          // Russian description
    public final boolean requiresTag;         // needs live tag (connected session)
    public final boolean requiresKeys;        // needs hasKeys == true
    public final boolean requiresDump;        // needs existing dump file
    public final boolean requiresConnection;  // needs ClientState.READY (not OFFLINE)
    public final List<String> notes;          // warnings / constraints shown in card

    private ActionDef(Builder b) {
        this.id                 = b.id;
        this.protocols          = b.protocols;
        this.commandTemplate    = b.commandTemplate;
        this.paramDefaults      = Collections.unmodifiableMap(b.paramDefaults);
        this.category           = b.category;
        this.description        = b.description;
        this.requiresTag        = b.requiresTag;
        this.requiresKeys       = b.requiresKeys;
        this.requiresDump       = b.requiresDump;
        this.requiresConnection = b.requiresConnection;
        this.notes              = Collections.unmodifiableList(b.notes);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        String id = "";
        String[] protocols = new String[0];
        String commandTemplate = "";
        Map<String, String> paramDefaults = new java.util.HashMap<>();
        String category = "";
        String description = "";
        boolean requiresTag = true;
        boolean requiresKeys = false;
        boolean requiresDump = false;
        boolean requiresConnection = true;
        List<String> notes = new java.util.ArrayList<>();

        public Builder id(String v)                   { this.id = v; return this; }
        public Builder protocols(String... v)         { this.protocols = v; return this; }
        public Builder commandTemplate(String v)      { this.commandTemplate = v; return this; }
        public Builder paramDefault(String k, String v) { this.paramDefaults.put(k, v); return this; }
        public Builder category(String v)             { this.category = v; return this; }
        public Builder description(String v)          { this.description = v; return this; }
        public Builder requiresTag(boolean v)         { this.requiresTag = v; return this; }
        public Builder requiresKeys(boolean v)        { this.requiresKeys = v; return this; }
        public Builder requiresDump(boolean v)        { this.requiresDump = v; return this; }
        public Builder requiresConnection(boolean v)  { this.requiresConnection = v; return this; }
        public Builder note(String v)                 { this.notes.add(v); return this; }

        public ActionDef build() { return new ActionDef(this); }
    }
}
