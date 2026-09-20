// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;

import org.proxmarkdesk.android.capability.ActionDef;
import org.proxmarkdesk.android.capability.ActionRegistry;
import org.proxmarkdesk.android.capability.CardCapability;
import org.proxmarkdesk.android.capability.CliCompat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin UI bridge for logical actions.
 *
 * UI code supplies a stable action id and parameters. This class owns the
 * ActionRegistry/Capability/CliCompat checks and is the only place in the
 * migrated flow that turns an action into an Iceman command string.
 */
public final class ActionExecutor {
    private final MainActivity activity;

    ActionExecutor(MainActivity activity) {
        this.activity = activity;
    }

    public void run(String actionId) {
        run(actionId, Collections.emptyMap(), 300);
    }

    public void run(String actionId, int timeoutSeconds) {
        run(actionId, Collections.emptyMap(), timeoutSeconds);
    }

    public void run(String actionId, Map<String, String> params, int timeoutSeconds) {
        ActionDef action = ActionRegistry.findById(actionId);
        if (action == null) throw new IllegalArgumentException("Неизвестное действие: " + actionId);
        if (activity.service == null) throw new IllegalStateException("Служба ещё не запущена");

        CardCapability capability = activity.service.currentCapability;
        String reason = ActionRegistry.unavailableReason(action, capability, activity.service.sessionState);
        if (reason != null) throw new IllegalStateException(reason);

        Map<String, String> safeParams = params == null
            ? Collections.emptyMap()
            : new HashMap<>(params);
        String command = ActionRegistry.buildCommand(action, safeParams);
        if (!CliCompat.supported(command, CliCompat.CURRENT_VERSION)) {
            throw new IllegalArgumentException(
                "Команда не поддерживается в " + CliCompat.CURRENT_VERSION + ": " + command);
        }
        activity.run(command, timeoutSeconds);
    }
}
