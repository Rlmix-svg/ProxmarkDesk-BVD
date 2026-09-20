// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.session;

/**
 * Lifecycle state of the currently executing (or last executed) command.
 *
 * TIMEOUT does NOT close the USB session or change ClientState.
 * CANCEL_REQUESTED sends "hw break" to the client and waits for the
 * end marker; only after confirmation transitions to CANCELLED.
 * After CANCELLED the client remains in ClientState.READY.
 */
public enum CommandState {
    /** No command pending or running. */
    IDLE,
    /** Command accepted, waiting for previous to finish (not used currently — queue depth 1). */
    QUEUED,
    /** Command sent to the client, waiting for the PMDESK_END marker. */
    RUNNING,
    /** Waiting for client synchronisation after hw break or long RF operation. */
    WAITING,
    /** Command completed with a normal result. */
    SUCCESS,
    /** Client reported an error in command output ([-] / [!!] etc.). */
    ERROR,
    /** End marker did not arrive within the timeout window. */
    TIMEOUT,
    /** User pressed Stop; hw break has been sent, awaiting confirmation. */
    CANCEL_REQUESTED,
    /** Operation stopped; client remains alive and ready. */
    CANCELLED
}
