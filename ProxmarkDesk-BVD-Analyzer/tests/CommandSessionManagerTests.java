package org.proxmarkdesk.android.session;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CommandSessionManagerTests {
    static int checks;
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
        System.out.println("PASS " + message);
    }

    static String lastMarker(String text) {
        int at = text.lastIndexOf("PMDESK_END_");
        if (at < 0) throw new AssertionError("marker missing in: " + text);
        int end = at;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (!(Character.isLetterOrDigit(c) || c == '_')) break;
            end++;
        }
        return text.substring(at, end);
    }

    static void expectRejected(CompletableFuture<String> future, String contains) throws Exception {
        boolean ok = false;
        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            ok = cause instanceof IllegalStateException
                && cause.getMessage() != null
                && cause.getMessage().contains(contains);
        }
        check(ok, "rejected while stream is not synchronized: " + contains);
    }

    static CommandSessionManager manager(List<CommandState> states, StringWriter writer) {
        CommandSessionManager csm = new CommandSessionManager(
            (state, progress, status) -> states.add(state),
            () -> {}
        );
        csm.setWriter(writer);
        return csm;
    }

    public static void main(String[] args) throws Exception {
        normalCompletionAndFinish();
        busySubmitDoesNotEvict();
        timeoutBlocksNextUntilMarker();
        cancelBlocksNextUntilMarker();
        System.out.println("TOTAL " + checks + " command session checks passed");
    }

    static void normalCompletionAndFinish() throws Exception {
        List<CommandState> states = new ArrayList<>();
        StringWriter writer = new StringWriter();
        CommandSessionManager csm = manager(states, writer);

        CompletableFuture<String> future = csm.submit("hw version", 5);
        String marker = lastMarker(writer.toString());
        csm.onLine("client output");
        csm.onLine("[usb] pm3 --> rem " + marker);

        check(future.get(1, TimeUnit.SECONDS).contains("client output"),
              "end marker completes future with output");
        check(csm.getState() != CommandState.RUNNING,
              "completed command must not remain RUNNING");
        check(!states.isEmpty() && states.get(states.size()-1) != CommandState.RUNNING,
              "completion publishes a non-busy command state");
        csm.finish(CommandState.ERROR, "Проверьте вывод");
        check(csm.getState() == CommandState.ERROR,
              "caller can refine completed output to ERROR");
    }

    static void busySubmitDoesNotEvict() throws Exception {
        List<CommandState> states = new ArrayList<>();
        StringWriter writer = new StringWriter();
        CommandSessionManager csm = manager(states, writer);

        CompletableFuture<String> first = csm.submit("hw version", 5);
        String marker = lastMarker(writer.toString());
        CompletableFuture<String> second = csm.submit("hw status", 5);
        expectRejected(second, "Дождитесь завершения");
        check(!writer.toString().contains("hw status"),
              "busy submit must not write second command");
        check(!first.isDone(), "busy submit must not cancel first command");

        csm.onLine("first result");
        csm.onLine("[usb] pm3 --> rem " + marker);
        check(first.get(1, TimeUnit.SECONDS).contains("first result"),
              "first command survives rejected second submit");
    }

    static void timeoutBlocksNextUntilMarker() throws Exception {
        List<CommandState> states = new ArrayList<>();
        StringWriter writer = new StringWriter();
        CommandSessionManager csm = manager(states, writer);

        CompletableFuture<String> first = csm.submit("hf search", 1);
        String marker = lastMarker(writer.toString());
        boolean timedOut = false;
        try {
            first.get(12, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            timedOut = e.getCause() instanceof java.util.concurrent.TimeoutException;
        }
        check(timedOut, "command result future times out");
        check(csm.getState() == CommandState.TIMEOUT, "timeout state is published");
        check(csm.getCommandStartMs() > 0, "timeout keeps command start time while stream is owned");

        CompletableFuture<String> blocked = csm.submit("hw version", 5);
        expectRejected(blocked, "синхрониз");
        check(!writer.toString().contains("hw version"),
              "timeout must block next command before old marker");

        csm.onLine("late output");
        csm.onLine("[usb] pm3 --> rem " + marker);
        check(csm.getState() == CommandState.ERROR, "late marker after timeout restores terminal non-busy state");
        check(csm.getCommandStartMs() == 0, "late marker clears command start time");
        CompletableFuture<String> afterSync = csm.submit("hw version", 5);
        check(!afterSync.isCompletedExceptionally(),
              "next command is accepted after late marker restores sync");
        String nextMarker = lastMarker(writer.toString());
        csm.onLine("ok");
        csm.onLine("[usb] pm3 --> rem " + nextMarker);
        check(afterSync.get(1, TimeUnit.SECONDS).contains("ok"),
              "command after timeout synchronization completes");
    }

    static void cancelBlocksNextUntilMarker() throws Exception {
        List<CommandState> states = new ArrayList<>();
        StringWriter writer = new StringWriter();
        CommandSessionManager csm = manager(states, writer);

        CompletableFuture<String> first = csm.submit("hf 14a sniff", 30);
        String marker = lastMarker(writer.toString());
        Thread cancel = new Thread(csm::cancel, "cancel-test");
        cancel.start();
        long deadline = System.currentTimeMillis() + 1000;
        while (csm.getState() != CommandState.CANCEL_REQUESTED && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        check(csm.getState() == CommandState.CANCEL_REQUESTED,
              "cancel request state is published");

        CompletableFuture<String> blocked = csm.submit("hw status", 5);
        expectRejected(blocked, "синхрониз");
        check(!writer.toString().contains("hw status"),
              "cancel must block next command before marker");

        csm.onLine("[usb] pm3 --> rem " + marker);
        cancel.join(1000);
        check(!cancel.isAlive(), "cancel returns after marker synchronization");
        check(csm.getState() == CommandState.CANCELLED,
              "cancel ends in CANCELLED without killing client");
        check(first.isDone(), "cancel resolves original result future");

        CompletableFuture<String> after = csm.submit("hw status", 5);
        check(!after.isCompletedExceptionally(), "command accepted after cancel synchronization");
        String nextMarker = lastMarker(writer.toString());
        csm.onLine("ready");
        csm.onLine("[usb] pm3 --> rem " + nextMarker);
        check(after.get(1, TimeUnit.SECONDS).contains("ready"),
              "command after cancel completes normally");
    }
}
