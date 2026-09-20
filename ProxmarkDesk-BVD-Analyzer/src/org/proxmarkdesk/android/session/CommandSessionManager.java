// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.session;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Centralised command execution engine.
 *
 * Responsibilities:
 *  - One command at a time (depth-1 queue: a new submit() cancels any pending command).
 *  - Owns the CompletableFuture + PMDESK_END marker handshake previously scattered in ClientService.
 *  - Transitions CommandState correctly; TIMEOUT does not kill the session.
 *  - cancel() sends "hw break" and waits up to HW_BREAK_TIMEOUT_S seconds for the marker,
 *    then transitions to CANCELLED. The client process stays alive.
 *  - PMDESK_TRANSPORT_ERROR in stdout signals UsbState.DETACHED without ending the command.
 *
 * The owner (ClientService) must:
 *  - Call onLine(line) for every stripped stdout line from the pm3 process.
 *  - Provide a Writer for sending text to the process stdin (setWriter).
 *  - React to stateChanged() callbacks to publish the new SessionState.
 */
public final class CommandSessionManager {

    /** Callback invoked on the worker thread whenever session state must be republished. */
    public interface StateListener {
        void onStateChanged(CommandState command, ProgressState progress, String statusText);
    }

    /** Callback invoked when PMDESK_TRANSPORT_ERROR is detected in stdout. */
    public interface TransportErrorListener {
        void onTransportError();
    }

    private static final int HW_BREAK_TIMEOUT_S = 12;
    private static final int MAX_OUTPUT_BYTES = 8 * 1024 * 1024;

    // Pattern that matches the echo of "rem PMDESK_END_<uuid>" in pm3 stdout.
    // Example: [usb] pm3 --> rem PMDESK_END_3f8a1b2c... --> (empty output)
    private static final Pattern END_MARKER_LINE =
        Pattern.compile("PMDESK_END_[0-9a-f\\-]+");

    private static final Pattern TRANSPORT_ERROR =
        Pattern.compile("PMDESK_TRANSPORT_ERROR");

    // -------------------------------------------------------------------------
    // State (guarded by lock)
    // -------------------------------------------------------------------------

    private final Object lock = new Object();

    private Writer stdin;                           // pm3 process stdin
    private String marker;                          // current PMDESK_END_<uuid>
    private CompletableFuture<String> pending;      // result future; may already be timed out while marker is still pending
    private CompletableFuture<Void> synchronization; // completes only when the current PMDESK_END marker is observed
    private boolean cancelRequested;
    private StringBuilder outputBuf;               // accumulates lines until marker
    private long commandStartMs;                    // for ProgressState ETA

    private volatile CommandState commandState = CommandState.IDLE;
    private volatile String currentCommand = "";
    private volatile ProgressState currentProgress = null;

    // Card context for ProgressState (set externally before submit)
    private volatile int classicTotalSectors = 16;
    private volatile int classicTotalBlocks  = 64;

    private final StateListener stateListener;
    private final TransportErrorListener transportListener;

    public CommandSessionManager(StateListener stateListener,
                                 TransportErrorListener transportListener) {
        this.stateListener = stateListener;
        this.transportListener = transportListener;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public void setWriter(Writer w) {
        synchronized (lock) { stdin = w; }
    }

    /**
     * Set card context so ProgressState parsers know total sectors/blocks.
     * Call after tag detection before submitting autopwn / dump.
     */
    public void setClassicContext(int totalSectors, int memorySizeBytes) {
        this.classicTotalSectors = totalSectors;
        this.classicTotalBlocks  = ProgressState.totalBlocksForSize(memorySizeBytes);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Submit a command for execution.
     * If a command is already running, it is cancelled first (CANCELLED state is
     * published, then the new command starts immediately without waiting for hw break).
     *
     * @param command  validated Iceman command string (single line, ≤ 250 bytes UTF-8)
     * @param timeoutS timeout in seconds (1–86400)
     * @return a CompletableFuture that resolves to the full stdout text of the command,
     *         or completes exceptionally on timeout / cancellation / transport error.
     */
    public CompletableFuture<String> submit(String command, int timeoutS) {
        if (command == null || command.isEmpty()) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("Пустая команда"));
            return f;
        }
        if (timeoutS < 1 || timeoutS > 86400) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new IllegalArgumentException("Тайм-аут: 1–86400 секунд"));
            return f;
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        synchronized (lock) {
            // The PM3 stream has one owner at a time. Never evict an active command:
            // doing so loses the end marker and lets outputs from two commands interleave.
            if (synchronization != null && !synchronization.isDone()) {
                String reason = (commandState == CommandState.RUNNING || commandState == CommandState.WAITING)
                    ? "Дождитесь завершения текущей команды"
                    : "Поток клиента ещё не синхронизирован с предыдущей командой";
                future.completeExceptionally(new IllegalStateException(reason));
                return future;
            }

            if (stdin == null) {
                future.completeExceptionally(new IOException("Клиент не запущен"));
                return future;
            }

            String uuid = UUID.randomUUID().toString().replace("-", "");
            marker = "PMDESK_END_" + uuid;
            outputBuf = new StringBuilder();
            pending = future;
            synchronization = new CompletableFuture<>();
            cancelRequested = false;
            currentCommand = command;
            commandStartMs = System.currentTimeMillis();

            try {
                stdin.write(command + "\nrem " + marker + "\n");
                stdin.flush();
            } catch (IOException e) {
                pending = null;
                marker = null;
                outputBuf = null;
                if (synchronization != null) synchronization.completeExceptionally(e);
                synchronization = null;
                future.completeExceptionally(e);
                return future;
            }
        }

        publish(CommandState.RUNNING, null,
                "Выполняется: " + command);

        // Timeout watchdog on a daemon thread.
        final int t = timeoutS;
        Thread watchdog = new Thread(() -> {
            try {
                future.get(Math.max(10, t), TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                boolean wasOurs;
                synchronized (lock) {
                    wasOurs = (pending == future && synchronization != null && !synchronization.isDone());
                }
                if (wasOurs) {
                    // Complete the caller result, but keep marker/synchronization ownership.
                    // A following command is blocked until the old marker is actually seen.
                    publish(CommandState.TIMEOUT, null,
                            "Тайм-аут · ожидается синхронизация: " + currentCommand);
                    future.completeExceptionally(new TimeoutException(
                        "Истекло время ожидания. Поток клиента ожидает синхронизации."));
                }
            } catch (Exception ignored) {
                // Future resolved via normal path.
            }
        }, "csm-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        return future;
    }

    /**
     * Stop button handler.
     * Transitions to CANCEL_REQUESTED, sends "hw break" to the client,
     * and waits up to HW_BREAK_TIMEOUT_S for the current marker.
     * Resolves to CANCELLED afterwards. The client process stays alive.
     *
     * Must be called from a background thread (it blocks briefly).
     */
    public void cancel() {
        CompletableFuture<String> target;
        CompletableFuture<Void> sync;
        synchronized (lock) {
            sync = synchronization;
            target = pending;
            if (sync == null || sync.isDone()) return; // nothing owns the stream
            cancelRequested = true;
        }

        publish(CommandState.CANCEL_REQUESTED, null, "Остановка…");

        // Send hw break — best effort. The stream remains locked until the old marker arrives.
        synchronized (lock) {
            if (stdin != null) {
                try {
                    stdin.write("hw break\n");
                    stdin.flush();
                } catch (IOException ignored) {}
            }
        }

        boolean synchronizedAgain = false;
        try {
            sync.get(HW_BREAK_TIMEOUT_S, TimeUnit.SECONDS);
            synchronizedAgain = true;
        } catch (Exception ignored) {}

        if (target != null && !target.isDone()) {
            target.completeExceptionally(new CancellationException("Остановлено пользователем"));
        }

        if (synchronizedAgain) {
            publish(CommandState.CANCELLED, null, "Остановлено · клиент готов");
        } else {
            // Do not clear marker/synchronization here. A late marker can still restore the stream.
            publish(CommandState.CANCEL_REQUESTED, null,
                    "Остановка отправлена · ожидается синхронизация клиента");
        }
    }

    /**
     * Called by ClientService.pump() for every stripped, ANSI-clean stdout line.
     * Must be safe to call from any thread.
     */
    public void onLine(String line) {
        if (line == null || line.isEmpty()) return;

        // Transport error: signal UsbState change but do not complete the future.
        if (TRANSPORT_ERROR.matcher(line).find()) {
            if (transportListener != null) transportListener.onTransportError();
            return;
        }

        CompletableFuture<String> complete = null;
        CompletableFuture<Void> syncComplete = null;
        String text = null;
        CommandState stateBeforeMarker = commandState;
        boolean wasCancelRequested = false;

        synchronized (lock) {
            if (marker == null || synchronization == null) return;

            if (END_MARKER_LINE.matcher(line).find() && line.contains(marker)) {
                // End marker arrived — transport stream ownership is released here and only here.
                complete = pending;
                syncComplete = synchronization;
                text = outputBuf != null ? outputBuf.toString() : "";
                wasCancelRequested = cancelRequested;
                pending = null;
                synchronization = null;
                cancelRequested = false;
                marker = null;
                outputBuf = null;
            } else {
                // Accumulate output.
                if (outputBuf != null) {
                    if (outputBuf.length() < MAX_OUTPUT_BYTES) {
                        outputBuf.append(line).append('\n');
                    }
                    // Update live progress from partial output.
                    updateProgress(line);
                }
            }
        }

        if (syncComplete != null) {
            syncComplete.complete(null);
            final String result = text == null ? "" : text;
            if (wasCancelRequested || stateBeforeMarker == CommandState.CANCEL_REQUESTED) {
                publish(CommandState.CANCELLED, null, "Остановлено · клиент готов");
            } else if (stateBeforeMarker == CommandState.TIMEOUT) {
                publish(CommandState.ERROR, null, "Тайм-аут · синхронизация восстановлена; клиент готов");
            } else {
                // The end marker means the transport-level command is finished.
                publish(CommandState.SUCCESS, null, "Команда завершена");
            }
            if (complete != null && !complete.isDone()) complete.complete(result);
        }
    }

    /**
     * Called by ClientService when the pm3 process exits unexpectedly.
     * Fails any pending future so callers don't wait forever.
     */
    public void onProcessDied(String reason) {
        CompletableFuture<String> failed;
        synchronized (lock) {
            failed = pending;
            pending = null;
            marker = null;
            outputBuf = null;
            if (synchronization != null && !synchronization.isDone()) {
                synchronization.completeExceptionally(new IOException(
                    reason != null ? reason : "Клиент завершился"));
            }
            synchronization = null;
            cancelRequested = false;
        }
        if (failed != null) {
            failed.completeExceptionally(new IOException(
                reason != null ? reason : "Клиент завершился"));
        }
        publish(CommandState.IDLE, null, "Клиент завершился");
    }

    /**
     * Reset to IDLE without killing any process.
     * Called after stopSession() clears the client.
     */
    public void reset() {
        synchronized (lock) {
            if (pending != null) {
                pending.completeExceptionally(new CancellationException("Сеанс остановлен"));
                pending = null;
            }
            marker = null;
            outputBuf = null;
            if (synchronization != null && !synchronization.isDone()) {
                synchronization.completeExceptionally(new CancellationException("Сеанс остановлен"));
            }
            synchronization = null;
            cancelRequested = false;
            currentCommand = "";
            currentProgress = null;
        }
        commandState = CommandState.IDLE;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public CommandState getState()        { return commandState; }
    public String getCurrentCommand()     { return currentCommand; }
    public long getCommandStartMs()       { return commandStartMs; }
    public ProgressState getProgress()    { return currentProgress; }

    /**
     * Refine the transport-level SUCCESS after the caller has classified
     * the completed command output. Only terminal SUCCESS/ERROR states are
     * accepted here; timeout/cancellation are owned by this manager.
     */
    public void finish(CommandState state, String status) {
        if (state != CommandState.SUCCESS && state != CommandState.ERROR) {
            throw new IllegalArgumentException("finish accepts only SUCCESS or ERROR");
        }
        publish(state, null, status == null ? "" : status);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void publish(CommandState state, ProgressState progress, String status) {
        commandState = state;
        currentProgress = progress;
        if (state == CommandState.SUCCESS || state == CommandState.ERROR
                || state == CommandState.CANCELLED || state == CommandState.IDLE) {
            currentCommand = "";
            commandStartMs = 0L;
        }
        if (stateListener != null) {
            stateListener.onStateChanged(state, progress, status);
        }
    }

    /**
     * Incrementally update ProgressState from a single new stdout line.
     * Called inside the lock — must not block.
     */
    private void updateProgress(String line) {
        if (outputBuf == null) return;
        String partial = outputBuf.toString();

        ProgressState p = null;

        // autopwn progress: "[+] Target sector N key type A -- found valid key [ HEX ]"
        if (line.contains("Target sector") && line.contains("found valid key")) {
            p = ProgressState.fromAutopwn(partial, classicTotalSectors, commandStartMs);
        }
        // dump progress: "Sector... N block... M ( ok )"
        else if (line.contains("Sector...") && line.contains("block...")) {
            p = ProgressState.fromDump(partial, classicTotalBlocks, commandStartMs);
        }

        if (p != null) {
            currentProgress = p;
            if (stateListener != null) {
                stateListener.onStateChanged(CommandState.RUNNING, p,
                        "Выполняется: " + currentCommand);
            }
        }
    }
}
