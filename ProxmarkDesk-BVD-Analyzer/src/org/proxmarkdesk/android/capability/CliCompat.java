// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.capability;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * CLI Compatibility Layer between ActionRegistry and CommandSessionManager.
 *
 * Version of the native client required by this Android build.
 * When Iceman is updated, add a new version entry and update the maps below
 * without touching ActionRegistry or the UI.
 */
public final class CliCompat {

    public static final String CURRENT_VERSION = "v4.23346";

    /**
     * Commands that changed syntax between versions.
     * Key: canonical command prefix (lower-case).
     * Value: minimum Iceman version where this syntax is valid.
     */
    private static final Map<String, String> MIN_VERSION = Collections.emptyMap();
    // Example for future use:
    // MIN_VERSION.put("hf mf autopwn", "v4.20000");

    /**
     * Commands known to be absent from this build (e.g. flashmem commands on Easy).
     * Checked by isSupported(); UI can grey out or hide these.
     */
    private static final Set<String> UNSUPPORTED_ON_EASY = new HashSet<>(Arrays.asList(
        "mem spiffs",
        "mem load",
        "mem save",
        "flashmem",
        "sc",
        "smart"
    ));

    private CliCompat() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Resolve a command template with parameters and return the final command string.
     * Delegates to ActionRegistry.buildCommand() and then validates the result.
     *
     * @param template     ActionDef.commandTemplate
     * @param params       caller-supplied parameters
     * @param clientVersion current Iceman version string (e.g. "v4.21611")
     * @return ready-to-send command string
     * @throws IllegalArgumentException if the command is not supported in clientVersion
     */
    public static String resolve(String template, Map<String, String> params,
                                 String clientVersion) {
        ActionDef def = new ActionDef.Builder()
            .commandTemplate(template)
            .build();
        String cmd = ActionRegistry.buildCommand(def, params);
        if (!supported(cmd, clientVersion)) {
            throw new IllegalArgumentException(
                "Команда не поддерживается в " + clientVersion + ": " + cmd);
        }
        return cmd;
    }

    /**
     * Check whether a command is supported in the given client version.
     * Returns true when uncertain (fail-open: let the client reject it).
     */
    public static boolean supported(String command, String clientVersion) {
        if (command == null || command.isEmpty()) return false;
        String lower = command.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (lower.isEmpty()) return false;

        // Check hard-coded unsupported list for Easy builds.
        for (String prefix : UNSUPPORTED_ON_EASY) {
            if ((lower.equals(prefix) || lower.startsWith(prefix + " "))) return false;
        }

        // Version gate (currently no entries, reserved for future).
        for (Map.Entry<String, String> entry : MIN_VERSION.entrySet()) {
            if ((lower.equals(entry.getKey()) || lower.startsWith(entry.getKey() + " "))) {
                if (versionCompare(clientVersion, entry.getValue()) < 0) return false;
            }
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Simple numeric version comparison.
     * Handles "v4.21611", "4.21611", "v4.21611-suspect" style strings.
     * Returns negative/zero/positive like Comparator.
     */
    static int versionCompare(String a, String b) {
        int[] va = parseVersion(a);
        int[] vb = parseVersion(b);
        for (int i = 0; i < Math.max(va.length, vb.length); i++) {
            long x = i < va.length ? va[i] : 0;
            long y = i < vb.length ? vb[i] : 0;
            if (x != y) return Long.compare(x, y);
        }
        return 0;
    }

    private static int[] parseVersion(String v) {
        if (v == null) return new int[]{0};
        v = v.replaceFirst("^v", "").replaceAll("[^0-9.].*", "");
        String[] parts = v.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException e) { result[i] = 0; }
        }
        return result;
    }
}
