// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.session;

/**
 * State of the embedded pm3 client process.
 * Independent from UsbState: a command failure does NOT set FAILED.
 * Only an actual process crash or explicit stopSession() changes this.
 */
public enum ClientState {
    /** Client process is not running. */
    STOPPED,
    /** Client process is being launched. */
    STARTING,
    /** Client is running and ready to accept commands (USB session). */
    READY,
    /** Client is running without a device (--incognito, no --port). */
    OFFLINE,
    /** Client process crashed or exited unexpectedly. */
    FAILED
}
