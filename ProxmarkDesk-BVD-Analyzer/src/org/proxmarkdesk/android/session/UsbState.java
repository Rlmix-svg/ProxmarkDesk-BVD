// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.session;

/**
 * Physical USB connection state.
 * Changes ONLY on Android USB_DEVICE_ATTACHED / USB_DEVICE_DETACHED system events.
 * Command errors, UART timeouts and PMDESK_TRANSPORT_ERROR do NOT change this state.
 */
public enum UsbState {
    /** USB cable connected and device recognized. */
    CONNECTED,
    /** USB cable removed or device unpowered. */
    DETACHED
}
