// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;
import org.proxmarkdesk.android.capability.CliCompat;

import android.app.*;
import android.content.*;
import android.os.*;

import org.json.*;
import org.proxmarkdesk.android.capability.CapabilityEngine;
import org.proxmarkdesk.android.capability.CardCapability;
import org.proxmarkdesk.android.session.ClientState;
import org.proxmarkdesk.android.session.CommandSessionManager;
import org.proxmarkdesk.android.session.CommandState;
import org.proxmarkdesk.android.session.ProgressState;
import org.proxmarkdesk.android.session.SessionState;
import org.proxmarkdesk.android.session.TagState;
import org.proxmarkdesk.android.session.UsbState;

import java.io.*;
import java.lang.Process;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.*;

/**
 * Foreground service owning the pm3 client process.
 *
 * Architecture changes (P0):
 *  - Four independent state axes: UsbState / ClientState / CommandState / TagState
 *    are published as an immutable SessionState snapshot.
 *  - Command execution is delegated to CommandSessionManager.
 *  - Stop button → stopCommand(): stops current operation, keeps client alive,
 *    does NOT clear the console or tag card.
 *  - Stop session → stopSession(): user-initiated full shutdown.
 *  - connectionLost() (process crash): sets ClientState.FAILED but preserves
 *    console text and tagInfo (marked as cached).
 *  - PMDESK_TRANSPORT_ERROR in stdout → UsbState.DETACHED without killing the command.
 */
public class ClientService extends Service {

    // -------------------------------------------------------------------------
    // Binder
    // -------------------------------------------------------------------------

    public class LocalBinder extends Binder {
        public ClientService get() { return ClientService.this; }
    }

    private final IBinder binder = new LocalBinder();

    // -------------------------------------------------------------------------
    // Workers
    // -------------------------------------------------------------------------

    public final AtomicBoolean firmwareUpdating = new AtomicBoolean();
    public volatile String firmwareStatus = "";
    public File firmwareLog() { return new File(getFilesDir(), "firmware-update.log"); }

    private final ExecutorService worker       = Executors.newSingleThreadExecutor();
    private final ExecutorService mirrorWorker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mirrorQueued   = new AtomicBoolean();
    private final AtomicBoolean mirrorDirty    = new AtomicBoolean();

    // -------------------------------------------------------------------------
    // Session state
    // -------------------------------------------------------------------------

    /** Single volatile reference read by the UI. Updated only via publishState(). */
    public volatile SessionState sessionState = SessionState.INITIAL;

    // Internal state axes (updated under lock, then published atomically).
    private final Object stateLock = new Object();
    private UsbState    usbState    = UsbState.DETACHED;
    private ClientState clientState = ClientState.STOPPED;
    private TagState    tagState    = TagState.UNKNOWN;

    // -------------------------------------------------------------------------
    // Console (append-only ring buffer)
    // -------------------------------------------------------------------------

    private final Object consoleLock = new Object();
    private final StringBuilder screen = new StringBuilder();

    // -------------------------------------------------------------------------
    // Pm3 process
    // -------------------------------------------------------------------------

    private final Object processLock = new Object();
    private Process client;
    private Writer  stdin;
    private BufferedWriter operationLog;
    private long rootPid;
    private boolean rootClient;
    private boolean offline;
    private volatile String startupFailure = "";

    // -------------------------------------------------------------------------
    // Tag / device info
    // -------------------------------------------------------------------------

    public volatile TagInfo tagInfo;
    public volatile boolean tagCached;
    public volatile String  deviceInfo    = "";
    public volatile String  emulationName = "";
    public volatile String  deviceProfile = "Proxmark3-Easy";
    public volatile CardCapability currentCapability = CardCapability.UNKNOWN;

    // -------------------------------------------------------------------------
    // Library / cache
    // -------------------------------------------------------------------------

    public File library, dumps, operations;
    public ResultCache cache;
    private ResultCache pythonCache;
    public volatile String storageStatus = "";
    public volatile String cacheError    = "";

    // -------------------------------------------------------------------------
    // Command session manager
    // -------------------------------------------------------------------------

    private final CommandSessionManager csm = new CommandSessionManager(
        // StateListener: republish SessionState on every CommandState change.
        (cmdState, progress, statusText) -> {
            publishState(statusText, progress);
            // After the command is done, update the revision so UI redraws.
        },
        // TransportErrorListener: USB physically removed during a command.
        () -> {
            synchronized (stateLock) { usbState = UsbState.DETACHED; }
            publishState("USB отключён во время команды", null);
        }
    );

    // -------------------------------------------------------------------------
    // Python
    // -------------------------------------------------------------------------

    private volatile PythonRunner  pythonRunner;
    private volatile PythonBridge  pythonBridge;
    private volatile boolean       pythonCommandActive;
    private final Object           pythonGate     = new Object();
    public  volatile String        pythonPrompt;
    public  volatile long          pythonPromptRevision;
    private volatile CompletableFuture<String> pythonInput;

    // -------------------------------------------------------------------------
    // Lua environment
    // -------------------------------------------------------------------------

    public volatile String luaEnvironment = "Проверка ресурсов ещё не завершена";

    // -------------------------------------------------------------------------
    // ANSI strip
    // -------------------------------------------------------------------------

    private static final Pattern ANSI =
        Pattern.compile("\u001b\\[[0-?]*[ -/]*[@-~]");

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        library    = new File(getFilesDir(), "library");
        dumps      = new File(library, "dumps");
        operations = new File(library, "operations");
        dumps.mkdirs();
        operations.mkdirs();

        try {
            selectProfile(getSharedPreferences("settings", MODE_PRIVATE)
                .getString("deviceProfile", "Proxmark3-Easy"));
        } catch (Exception e) {
            storageStatus = "Ошибка библиотеки: " + e.getMessage();
        }

        worker.execute(() -> {
            try { installData(); } catch (IOException e) { luaEnvironment = e.getMessage(); }
        });

        getSystemService(NotificationManager.class)
            .createNotificationChannel(new NotificationChannel(
                "session", "Сеанс Proxmark3", NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent open  = new Intent(this, MainActivity.class);
        PendingIntent tap = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "session")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("ProxmarkDesk")
            .setContentText("Сеанс Iceman. Откройте приложение для управления.")
            .setContentIntent(tap)
            .setOngoing(true)
            .build();
        startForeground(1, n);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        cancelPython();
        stopClientProcess();
        csm.reset();
        if (firmwareUpdating.get()) worker.shutdown(); else worker.shutdownNow();
        mirrorWorker.shutdown();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Profile / library
    // -------------------------------------------------------------------------

    public synchronized void selectProfile(String name) throws Exception {
        if (firmwareUpdating.get()) throw new IOException("Дождитесь обновления устройства");
        if (sessionState.isBusy() || sessionState.client == ClientState.READY
                || sessionState.client == ClientState.OFFLINE) {
            throw new IOException("Остановите сеанс перед сменой профиля устройства");
        }
        deviceProfile = LibraryMirror.profile(name);
        if (deviceProfile.equals("Proxmark3-Easy")) {
            library = new File(getFilesDir(), "library");
        } else {
            library = new File(getFilesDir(), "devices/" + deviceProfile);
        }
        dumps      = new File(library, "dumps");
        operations = new File(library, "operations");
        dumps.mkdirs();
        operations.mkdirs();

        cache       = new ResultCache(library);
        pythonCache = new ResultCache(library);
        cacheError  = "";
        tagInfo     = null;
        tagCached   = false;
        currentCapability = CardCapability.UNKNOWN;

        synchronized (consoleLock) { screen.setLength(0); }

        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("deviceProfile", deviceProfile).apply();

        File last   = new File(library, "last-tag.txt");
        File device = new File(library, "device-info.txt");
        if (last.isFile()) {
            tagInfo   = TagInfo.parse(ResultCache.preview(last, 200000));
            tagCached = true;
        }
        deviceInfo = device.isFile() ? ResultCache.preview(device, 100000) : "";
        requestMirror();

        publishState("Профиль: " + deviceProfile, null);
    }

    // -------------------------------------------------------------------------
    // Console
    // -------------------------------------------------------------------------

    public String console() {
        synchronized (consoleLock) { return screen.toString(); }
    }

    void emit(String line) {
        synchronized (consoleLock) {
            screen.append(line).append('\n');
            if (screen.length() > 240000) screen.delete(0, screen.length() - 180000);
        }
        synchronized (processLock) {
            if (operationLog != null) {
                try { operationLog.write(line); operationLog.newLine(); operationLog.flush(); }
                catch (IOException ex) { storageStatus = "Ошибка журнала: " + ex.getMessage(); }
            }
        }
    }

    void emitRaw(String value) {
        synchronized (consoleLock) {
            screen.append(value);
            if (screen.length() > 240000) screen.delete(0, screen.length() - 180000);
        }
        synchronized (processLock) {
            if (operationLog != null) {
                try { operationLog.write(value); operationLog.flush(); }
                catch (IOException e) { cacheError = e.getMessage(); }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connect / disconnect
    // -------------------------------------------------------------------------

    /**
     * Start the pm3 client process.
     * UsbState is NOT changed here — it is managed by the USB BroadcastReceiver.
     */
    public synchronized void connect(String port, boolean useRoot, boolean off) {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        if (sessionState.isBusy()
                || sessionState.client == ClientState.READY
                || sessionState.client == ClientState.OFFLINE) {
            publishState("Сначала остановите текущий сеанс", null);
            return;
        }
        if (!off && !port.matches("/dev/tty(?:ACM|USB)[0-9]+")) {
            publishState("Порт должен иметь вид /dev/ttyACM0", null);
            return;
        }

        startupFailure = "";
        updateClientState(ClientState.STARTING);
        publishState("Запуск клиента…", null);

        offline   = off;
        rootClient = useRoot && !off;

        worker.execute(() -> {
            try {
                installData();
                String exe = new File(getApplicationInfo().nativeLibraryDir,
                    "libpm3client.so").getAbsolutePath();
                if (!new File(exe).isFile()) {
                    throw new IOException("Нет встроенного клиента для архитектуры "
                        + Arrays.toString(Build.SUPPORTED_ABIS));
                }

                List<String> args = new ArrayList<>();
                if (rootClient) {
                    String shell = RootLaunch.command(
                        library.getAbsolutePath(),
                        getFilesDir().getAbsolutePath(), exe, port);
                    args.add("su"); args.add("-c"); args.add(shell);
                } else {
                    args.add(exe); args.add("--incognito"); args.add("--flush");
                    if (!off) { args.add("--port"); args.add(port); }
                }

                ProcessBuilder pb = new ProcessBuilder(args)
                    .directory(library)
                    .redirectErrorStream(true);
                pb.environment().put("HOME", getFilesDir().getAbsolutePath());
                pb.environment().put("PMDESK_PIPE", "1");

                Process owner = pb.start();
                Writer inputWriter = new OutputStreamWriter(
                    owner.getOutputStream(), StandardCharsets.UTF_8);

                synchronized (processLock) {
                    client = owner;
                    stdin  = inputWriter;
                    rootPid = 0;
                }
                csm.setWriter(inputWriter);

                // Start stdout pump.
                new Thread(() -> pump(owner), "pm3-output").start();

                // Probe: hw version.
                String out = syncExchange("hw version", 45);

                if (!off && out.contains("[offline")) {
                    throw new IOException("Клиент запустился без устройства");
                }
                if (!off && !Pattern.compile("(?s)\\[\\s*ARM\\s*\\]").matcher(out).find()) {
                    throw new IOException(
                        "Устройство не подтвердило hw version. Проверьте USB, порт и прошивку. "
                        + out.substring(Math.max(0, out.length() - 600)));
                }

                if (!owner.isAlive()) throw new IOException("Клиент завершился");

                deviceInfo = out;
                ResultCache.atomic(new File(library, "device-info.txt"), out);

                updateClientState(off ? ClientState.OFFLINE : ClientState.READY);
                publishState(off ? "Офлайн · " + CliVersion.CLIENT
                                 : "Подключено · " + port, null);

            } catch (Exception e) {
                stopClientProcess();
                updateClientState(ClientState.FAILED);
                String detail = !startupFailure.isEmpty()
                    ? "Прошивка устройства несовместима с клиентом. Откройте «Обновить устройство»."
                    : e.getMessage();
                publishState("Ошибка подключения: " + detail, null);
            }
        });
    }

    /**
     * Signal that USB was physically removed.
     * Called from MainActivity's BroadcastReceiver on USB_DEVICE_DETACHED.
     * Does NOT stop the client process — it may still be running and reporting errors.
     */

    public synchronized void updateFirmware(FirmwarePackage pack, String port, boolean boot) {
        if (firmwareUpdating.get()) return;
        if (sessionState.isBusy() || sessionState.client == ClientState.STARTING || pythonRunner != null)
            throw new IllegalStateException("Сначала завершите текущую операцию");
        if (!port.matches("/dev/tty(?:ACM|USB)[0-9]+")) throw new IllegalArgumentException("Неверный tty-порт");
        firmwareUpdating.set(true);
        firmwareStatus = "Подготовка обновления устройства…";
        publishState(firmwareStatus, null);
        worker.execute(() -> {
            PowerManager.WakeLock wake = null;
            try {
                PowerManager pm = getSystemService(PowerManager.class);
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ProxmarkDesk:firmware");
                wake.acquire();
                Process previous;
                synchronized (processLock) { previous = client; }
                stopClientProcess();
                if (previous != null && !previous.waitFor(10, TimeUnit.SECONDS))
                    throw new IOException("Предыдущий клиент не завершился; запись не начата");
                csm.reset();
                updateClientState(ClientState.STOPPED);
                FirmwareUpdater updater = new FirmwareUpdater(
                    new File(getApplicationInfo().nativeLibraryDir, "libpm3client.so"),
                    getFilesDir(), firmwareLog(), port, new FirmwareUpdater.Listener() {
                        public void line(String text) { emit(text); publishState(firmwareStatus,null); }
                        public void status(String text) { firmwareStatus=text; publishState(text,null); }
                    });
                updater.execute(pack, boot);
                deviceInfo = "";
                if (tagInfo != null) tagCached = true;
            } catch (Exception e) {
                firmwareStatus = "Обновление: " + e.getMessage();
                emit(firmwareStatus);
                try (FileWriter log = new FileWriter(firmwareLog(), true)) { log.write("\n" + firmwareStatus + "\n"); }
                catch (IOException ignored) {}
                updateClientState(ClientState.FAILED);
            } finally {
                if (wake != null && wake.isHeld()) wake.release();
                firmwareUpdating.set(false);
                publishState(firmwareStatus, null);
            }
        });
    }

    public void onUsbDetached() {
        if (firmwareUpdating.get()) { emit("[Обновление] USB отключён; ожидается повторное обнаружение прошивальщиком"); return; }
        synchronized (stateLock) { usbState = UsbState.DETACHED; }
        // Mark tag as cached (historical) but do NOT clear it.
        if (tagInfo != null) tagCached = true;
        publishState("USB отключён · результаты сохранены", null);
    }

    /**
     * Called when USB is re-attached (Android USB_DEVICE_ATTACHED).
     */
    public void onUsbAttached() {
        if (firmwareUpdating.get()) { emit("[Обновление] USB подключён"); return; }
        synchronized (stateLock) { usbState = UsbState.CONNECTED; }
        publishState("USB подключён", null);
    }

    /**
     * Called when the pm3 process exits unexpectedly (pump() thread ends).
     * Sets ClientState.FAILED but preserves console and tagInfo.
     */
    private void connectionLost() {
        csm.onProcessDied("Клиент завершился · история сохранена");
        synchronized (processLock) { client = null; stdin = null; rootPid = 0; }
        if (tagInfo != null) tagCached = true;
        updateClientState(ClientState.FAILED);
        publishState("Клиент завершился · история сохранена", null);
        requestMirror();
    }

    // -------------------------------------------------------------------------
    // Command execution
    // -------------------------------------------------------------------------

    /**
     * Execute a command and record the result in the journal.
     * Non-blocking: schedules on the worker thread.
     */
    public synchronized void run(String command, int seconds) {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        String validation = ClientInput.error(command);
        if (validation != null) { publishState(validation, null); return; }
        if (sessionState.isBusy()) { publishState("Команда ещё выполняется", null); return; }
        if (!sessionState.isClientAlive()) {
            publishState("Сначала подключите устройство или запустите офлайн-клиент", null);
            return;
        }

        boolean isSearch = AutoInspect.isSearch(command) && !offline;
        if (isSearch) {
            tagInfo = null; tagCached = false;
            tagState = TagState.UNKNOWN;
            currentCapability = CardCapability.UNKNOWN;
            publishState("Поиск и получение сведений…", null);
        }

        worker.execute(() -> operation(command, seconds, () -> {
            String output = syncExchange(command, seconds);

            // Lua environment diagnostic.
            if (command.trim().matches("script run pmdesk_envcheck(?:\\.lua)?")) {
                luaEnvironment = output;
                ResultCache.atomic(new File(library, "lua-runtime-diagnostics.txt"), output);
            }
            if (command.trim().equals("hw version"))  deviceInfo = output;
            if (command.trim().equals("hw break"))     emulationName = "";

            if (!isSearch) return output;

            // Auto-inspect: collect tag info + follow-up command.
            TagInfo found = AutoInspect.collect(output, infoCommand -> {
                publishState("Метка найдена. Получение информации…", null);
                emit("[Автоинформация] " + infoCommand);
                return syncExchange(infoCommand, 90);
            });

            if (sessionState.client == ClientState.STOPPED) return output;

            tagInfo   = found;
            tagCached = !sessionState.isClientAlive();
            tagState  = found.detected
                ? (found.ambiguous ? TagState.UNKNOWN : TagState.PRESENT)
                : TagState.REMOVED;

            // Build/refresh capability snapshot.
            currentCapability = CapabilityEngine.fromTagInfo(found, getFilesDir());

            ResultCache.atomic(new File(library, "last-tag.txt"),
                found.detected ? found.details : "");

            if (found.detected && !found.ambiguous) {
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putInt("family", found.family)
                    .putInt("cardSize", Math.max(0, found.classicSize))
                    .apply();
            }

            File cards = new File(library, "cards");
            cards.mkdirs();
            Files.write(new File(library, "last-card-report.txt").toPath(),
                found.report().getBytes(StandardCharsets.UTF_8));
            if (found.detected) {
                Files.write(new File(cards, "card-" + found.timestamp + ".txt").toPath(),
                    found.report().getBytes(StandardCharsets.UTF_8));
            }
            emit("[Карточка метки]\n" + found.summary());
            return output;
        }));
    }

    // -------------------------------------------------------------------------
    // Stop command (Stop button) — does NOT close the session
    // -------------------------------------------------------------------------

    /**
     * Stop button pressed by the user.
     * Cancels the current command via CommandSessionManager (sends hw break).
     * Console, tagInfo and USB state are NOT affected.
     */
    public void stopCommand() {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        worker.execute(() -> csm.cancel());
    }

    // -------------------------------------------------------------------------
    // Stop session (Остановить сеанс button) — closes the client process
    // -------------------------------------------------------------------------

    /**
     * Full session shutdown. Called only when the user explicitly taps "Остановить сеанс".
     * Clears the console. UsbState is not changed.
     */
    public void stopSession() {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        emulationName = "";
        cancelPython();
        csm.reset();

        // Clear tag state (user explicitly ended the session).
        tagInfo   = null;
        tagCached = false;
        tagState  = TagState.UNKNOWN;
        currentCapability = CardCapability.UNKNOWN;

        stopClientProcess();

        synchronized (consoleLock) { screen.setLength(0); }

        updateClientState(ClientState.STOPPED);
        publishState("Сеанс остановлен", null);
    }

    // -------------------------------------------------------------------------
    // Mirror
    // -------------------------------------------------------------------------

    public void requestMirror() {
        if (mirrorWorker.isShutdown()) return;
        mirrorDirty.set(true);
        if (!mirrorQueued.compareAndSet(false, true)) return;
        mirrorWorker.execute(() -> {
            try {
                while (mirrorDirty.getAndSet(false)) {
                    try {
                        storageStatus = LibraryMirror.sync(this, library, deviceProfile,
                            getSharedPreferences("settings", MODE_PRIVATE)
                                .getBoolean("mirrorPasswords", false));
                    } catch (Exception e) {
                        storageStatus = "Копирование отложено: " + e.getMessage();
                    }
                }
            } finally {
                mirrorQueued.set(false);
                if (mirrorDirty.get()) requestMirror();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Verify password (PWD_AUTH)
    // -------------------------------------------------------------------------

    public synchronized void verifyPassword(String expectedUid, String password) {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        String expected = RfidAuth.clean(expectedUid);
        String command  = RfidAuth.passwordCommand(password);
        if (!RfidAuth.validUid(expected)) {
            throw new IllegalArgumentException(
                "Сначала найдите метку или укажите её UID");
        }
        if (sessionState.isBusy() || !sessionState.isClientAlive() || offline) {
            throw new IllegalStateException(
                "Подключите устройство и дождитесь завершения команды");
        }
        worker.execute(() -> operation("[проверка PWD_AUTH · UID " + expected + "]", 15, () -> {
            String result;
            try {
                String selected = syncExchange(RfidAuth.SELECT, 15);
                String error    = RfidAuth.selectionError(expected, selected);
                result = error != null ? error : RfidAuth.checkReply(syncExchange(command, 15));
            } finally {
                if (sessionState.isClientAlive()) syncExchange(RfidAuth.DROP, 15);
            }
            String report = "[PWD_AUTH] " + result;
            emit(report);
            return report;
        }));
    }

    // -------------------------------------------------------------------------
    // Auto sniff
    // -------------------------------------------------------------------------

    public synchronized void autoSniff(int seconds, int delay) {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        if (seconds < 1 || seconds > 3600 || delay < 1 || delay > 10) {
            throw new IllegalArgumentException("Длительность 1–3600 с; пауза 1–10 с");
        }
        if (sessionState.isBusy() || !sessionState.isClientAlive() || offline) {
            throw new IllegalStateException(
                "Подключите Proxmark3 и дождитесь завершения команды");
        }
        if (!emulationName.isEmpty()) {
            throw new IllegalStateException("Сначала остановите эмуляцию");
        }
        final String filename = "dumps/sniff." + System.currentTimeMillis();
        worker.execute(() -> operation("[Авто sniff · " + seconds + " с]", seconds + 60, () -> {
            emit("[Авто sniff] "
                + "подробный разбор: trace list -1 -t 14a -c --frame");
            String out = AutoSniff.run(new AutoSniff.Transport() {
                public String command(String cmd, int timeout) throws Exception {
                    if (!sessionState.isClientAlive()) {
                        throw new CancellationException("Авто sniff остановлен");
                    }
                    publishState("Авто sniff: " + cmd, null);
                    String output = syncExchange(cmd, timeout);
                    if (cmd.equals("hf 14a sniff")
                            && !ClientService.classify(output).equals("Команда завершена")) {
                        throw new IOException("Захват не запущен: " + output);
                    }
                    return output;
                }
                public void waitSeconds(int s) throws Exception {
                    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(s);
                    while (System.nanoTime() < end) {
                        if (!sessionState.isClientAlive()) {
                            throw new CancellationException("Авто sniff остановлен");
                        }
                        publishState("Авто sniff · ожидание "
                            + Math.max(1, TimeUnit.NANOSECONDS.toSeconds(end - System.nanoTime()) + 1)
                            + " с", null);
                        Thread.sleep(100);
                    }
                }
            }, seconds, delay, filename);
            File trace = new File(library, filename + ".trace");
            if (!trace.isFile() || trace.length() == 0) {
                throw new IOException("Непустая трасса не сохранена — проверьте вывод");
            }
            emit("[Авто sniff] Сохранено: " + trace.getName());
            return out;
        }));
    }

    // -------------------------------------------------------------------------
    // Emulation
    // -------------------------------------------------------------------------

    public synchronized void emulate(File source, int mode) {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        if (sessionState.isBusy() || !sessionState.isClientAlive() || offline) {
            throw new IllegalStateException(
                "Дождитесь подключения USB и завершения команды");
        }
        if (!emulationName.isEmpty()) {
            throw new IllegalStateException("Сначала остановите текущую эмуляцию");
        }
        worker.execute(() -> operation("[эмуляция " + source.getName() + "]", 1800, () -> {
            File owned = DumpLibrary.child(dumps, source.getName());
            if (!owned.equals(source) || !source.isFile()) {
                throw new IOException("Дамп не найден в библиотеке");
            }
            String relative = "emulation/" + UUID.randomUUID().toString().replace("-", "")
                + DumpLibrary.extension(source);
            String[] commands = DumpLibrary.emulatorCommands(source, mode, relative);
            File copy = new File(library, relative);
            copy.getParentFile().mkdirs();
            Files.copy(source.toPath(), copy.toPath());
            StringBuilder result = new StringBuilder();
            try {
                for (int i = 0; i < commands.length; i++) {
                    emit("[Эмуляция] " + commands[i]);
                    String output = syncExchange(commands[i],
                        i == commands.length - 1 ? 1800 : 90);
                    result.append(output).append('\n');
                    if (i == commands.length - 2 && !DumpLibrary.loaded(output)) {
                        throw new IOException(
                            "Загрузка памяти не подтверждена. Эмуляция не запущена");
                    }
                    if (i < commands.length - 2
                            && !classify(output).equals("Команда завершена")) {
                        throw new IOException("Ошибка подготовки памяти эмулятора");
                    }
                }
                if (classify(result.toString()).equals("Команда завершена")) {
                    emulationName = mode < 4 ? source.getName() : "";
                }
                return result.toString();
            } finally {
                Files.deleteIfExists(copy.toPath());
            }
        }));
    }

    // -------------------------------------------------------------------------
    // View trace
    // -------------------------------------------------------------------------

    public synchronized void viewTrace(File source, String protocol) {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        if (sessionState.isBusy() || !sessionState.isClientAlive()) {
            throw new IllegalStateException("Дождитесь готовности клиента");
        }
        if (!Arrays.asList(FeaturePages.TRACE_TYPES).contains(protocol)) {
            throw new IllegalArgumentException("Неизвестный протокол");
        }
        worker.execute(() -> operation("[трасса · " + protocol + "]", 210, () -> {
            File dir  = new File(library, "emulation");
            dir.mkdirs();
            File copy = new File(dir, UUID.randomUUID() + ".trace");
            try {
                Files.copy(source.toPath(), copy.toPath());
                String load = syncExchange("trace load -f emulation/" + copy.getName(), 90);
                if (!classify(load).equals("Команда завершена")) {
                    throw new IOException("Не удалось загрузить трассу: " + load);
                }
                return load + syncExchange("trace list -1 -t " + protocol, 120);
            } finally {
                Files.deleteIfExists(copy.toPath());
            }
        }));
    }

    // -------------------------------------------------------------------------
    // Run sequence
    // -------------------------------------------------------------------------

    public synchronized void runSequence(String text, boolean stopOnWarning) {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        if (sessionState.isBusy() || !sessionState.isClientAlive()) {
            throw new IllegalStateException(
                "Сначала запустите клиент и дождитесь завершения команды");
        }
        List<String> steps = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String command = line.trim();
            if (command.isEmpty() || command.startsWith("#")) continue;
            String error = ClientInput.error(command);
            if (error != null) throw new IllegalArgumentException(error);
            steps.add(command);
        }
        if (steps.isEmpty() || steps.size() > 100) {
            throw new IllegalArgumentException(
                "Сценарий должен содержать от 1 до 100 команд");
        }
        worker.execute(() -> operation("[сценарий · " + steps.size() + " шагов]", 300 * steps.size(), () -> {
            StringBuilder output = new StringBuilder();
            for (String command : steps) {
                if (!sessionState.isClientAlive()) {
                    throw new IOException("Сценарий прерван");
                }
                String part = syncExchange(command, 300);
                output.append(part).append('\n');
                if (stopOnWarning && !classify(part).equals("Команда завершена")) {
                    throw new IOException("Сценарий остановлен: проверьте результат " + command);
                }
            }
            return output.toString();
        }));
    }

    // -------------------------------------------------------------------------
    // Catalogue
    // -------------------------------------------------------------------------

    public void catalogue() {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        if (sessionState.isBusy()) { publishState("Дождитесь текущей команды", null); return; }
        worker.execute(() -> operation("[каталог команд]", 30, () -> {
            installData();
            String exe = new File(getApplicationInfo().nativeLibraryDir,
                "libpm3client.so").getAbsolutePath();
            Process p = new ProcessBuilder(exe, "--incognito", "--text")
                .directory(library)
                .redirectErrorStream(true)
                .start();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Thread drain = new Thread(() -> {
                try {
                    byte[] b = new byte[8192]; int n;
                    while ((n = p.getInputStream().read(b)) >= 0) {
                        if (bytes.size() < 8 * 1024 * 1024) bytes.write(b, 0, n);
                    }
                } catch (IOException ignored) {}
            });
            drain.start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("Тайм-аут каталога");
            }
            drain.join(2000);
            String text = ANSI.matcher(bytes.toString("UTF-8")).replaceAll("");
            if (p.exitValue() != 0
                    || CommandCatalog.parse(
                        new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
                        .isEmpty()) {
                throw new IOException(
                    "Клиент не вернул корректный каталог; предыдущий сохранён");
            }
            Files.deleteIfExists(new File(library, "commands-client.txt").toPath());
            ResultCache.atomic(new File(library, "commands.txt"), text);
            ResultCache.atomic(new File(library, "commands-client.txt"), CliCompat.CURRENT_VERSION);
            emit(text);
            emit("Каталог сохранён");
            return "Каталог сохранён";
        }));
    }

    // -------------------------------------------------------------------------
    // Resources (Lua)
    // -------------------------------------------------------------------------

    private void installData() throws IOException {
        try {
            JSONObject manifest = new JSONObject(new String(
                MainActivity.readStream(getAssets().open("resource-manifest.json"), 2000000),
                StandardCharsets.UTF_8));
            luaEnvironment = ResourceInstaller.install(
                getAssets().open("pm3-data.zip"), manifest,
                new File(getFilesDir(), ".proxmark3"),
                new File(library, "backups/resources"));
            ResultCache.atomic(new File(library, "resource-diagnostics.txt"), luaEnvironment);
        } catch (Exception e) {
            luaEnvironment = "Ошибка ресурсов Lua: " + e.getMessage();
            throw new IOException(luaEnvironment, e);
        }
    }

    public synchronized void repairResources() {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        if (sessionState.isBusy()) return;
        worker.execute(() -> operation("[проверка ресурсов Lua]", 60, () -> {
            installData();
            emit(luaEnvironment);
            requestMirror();
            return luaEnvironment;
        }));
    }

    // -------------------------------------------------------------------------
    // Python
    // -------------------------------------------------------------------------

    public synchronized void runPython(String name, boolean custom,
                                        String arguments, int seconds) {
        if (firmwareUpdating.get()) { publishState(firmwareStatus, null); return; }
        if (sessionState.isBusy()) {
            throw new IllegalStateException("Дождитесь завершения текущей операции");
        }
        if (seconds < 1 || seconds > 86400) {
            throw new IllegalArgumentException("Время выполнения Python: 1–86400 секунд");
        }
        List<String> args = PythonRunner.arguments(arguments);
        PythonRunner runner = new PythonRunner();
        pythonRunner = runner;
        worker.execute(() -> operation("[Python] " + name + " " + arguments, seconds, () -> {
            String state = "Прервана";
            boolean recording = false;
            try {
                pythonCache.begin("python " + name + " " + arguments,
                    tagInfo != null && !tagCached ? tagInfo.id : "", deviceProfile);
                recording = true;
                publishState("Подготовка Python…", null);
                AndroidPython.install(this);
                File script = AndroidPython.script(this, library, name, custom);

                PythonBridge bridge = new PythonBridge(request -> {
                    if (pythonRunner != runner || runner.isCancelled()
                            || !sessionState.isClientAlive()) {
                        throw new CancellationException("Python остановлен");
                    }
                    String action = request.optString("action");
                    if (action.equals("input")) {
                        CompletableFuture<String> answer = new CompletableFuture<>();
                        pythonInput = answer;
                        pythonPromptRevision++;
                        pythonPrompt = request.optString("prompt");
                        try {
                            return new JSONObject().put("value",
                                answer.get(600, TimeUnit.SECONDS));
                        } finally {
                            if (pythonInput == answer) {
                                pythonInput = null; pythonPrompt = null;
                            }
                        }
                    }
                    if (!action.equals("command")) {
                        throw new IOException("Неизвестный запрос Python");
                    }
                    String cmd   = request.getString("command");
                    String error = ClientInput.error(cmd);
                    if (error != null) throw new IOException(error);
                    int timeout  = request.optInt("timeout", 300);
                    if (timeout < 1 || timeout > 86400) {
                        throw new IOException("Тайм-аут команды 1–86400 секунд");
                    }
                    if (!sessionState.isClientAlive()) {
                        throw new IOException(
                            "Нет сеанса Iceman: подключите устройство или запустите офлайн-клиент");
                    }
                    synchronized (pythonGate) {
                        if (pythonRunner != runner || runner.isCancelled()
                                || !sessionState.isClientAlive()) {
                            throw new CancellationException("Python остановлен");
                        }
                        pythonCommandActive = true;
                    }
                    try {
                        emit("[ProxmarkDesk] Python > " + cmd);
                        String output = syncExchange(cmd, timeout);
                        return new JSONObject().put("output", output)
                            .put("returncode",
                                classify(output).equals("Команда завершена") ? 0 : 1);
                    } finally {
                        synchronized (pythonGate) { pythonCommandActive = false; }
                    }
                });
                pythonBridge = bridge;
                publishState("Python: " + name, null);
                int code = runner.run(
                    AndroidPython.process(this, library, script, args, bridge),
                    seconds,
                    value -> { pythonCache.appendRaw(value); emitRaw(value); });
                if (code != 0) {
                    throw new IOException(
                        "Python завершился с кодом " + code + ". Подробности в журнале");
                }
                state = "Команда завершена";
                emit("\n[Python] Скрипт завершён");
                return "Python завершён";
            } catch (Exception e) {
                state = "Прервана: " + e.getMessage();
                if (recording) pythonCache.append("\n[Python] " + state);
                throw e;
            } finally {
                PythonBridge bridge = pythonBridge;
                cancelPython();
                pythonBridge = null;
                if (bridge != null) {
                    try { bridge.awaitClosed(); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                CompletableFuture<String> answer = pythonInput;
                if (answer != null) {
                    answer.completeExceptionally(new CancellationException("Python завершён"));
                }
                pythonInput = null; pythonPrompt = null; pythonRunner = null;
                if (recording) pythonCache.finish(state);
                requestMirror();
            }
        }));
    }

    public void answerPython(String value) {
        CompletableFuture<String> answer = pythonInput;
        if (answer != null) {
            if (value == null) answer.completeExceptionally(
                new CancellationException("Ввод отменён"));
            else answer.complete(value);
        }
    }

    public void cancelPython() {
        boolean active;
        synchronized (pythonGate) {
            PythonRunner runner = pythonRunner;
            if (runner != null) runner.cancel();
            active = pythonCommandActive;
        }
        PythonBridge bridge = pythonBridge;
        if (bridge != null) bridge.close();
        answerPython(null);
        pythonPrompt = null;
        if (active) stopClientProcess();
    }

    // -------------------------------------------------------------------------
    // Internal: operation wrapper
    // -------------------------------------------------------------------------

    interface Job { String get() throws Exception; }

    private void operation(String command, int timeoutS, Job job) {
        String id = String.format(Locale.US, "%tY%<tm%<td-%<tH%<tM%<tS-", new Date())
            + UUID.randomUUID().toString().substring(0, 8);
        JSONObject record = new JSONObject();
        String state = "Ошибка";
        Set<String> before = new HashSet<>();
        File[] original = dumps.listFiles();
        if (original != null) for (File f : original) before.add(f.getName());

        try {
            record.put("id", id).put("command", command)
                  .put("started", System.currentTimeMillis())
                  .put("status", "Выполняется")
                  .put("device", deviceProfile);
            writeRecord(id, record);

            synchronized (processLock) {
                operationLog = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(operations, id + ".log")),
                    StandardCharsets.UTF_8));
            }

            emit("\n[ProxmarkDesk] " + new Date() + " > " + command);
            publishState("Выполняется: " + command, null);

            String output = job.get();

            // Collect newly created dump files.
            JSONArray created = new JSONArray();
            File[] saved = dumps.listFiles();
            if (saved != null) {
                for (File f : saved) {
                    if (f.isFile() && !before.contains(f.getName())) {
                        try { f = DumpLibrary.withUid(f); } catch (Exception e) {
                            emit("[Файл] UID не определён: " + e.getMessage());
                        }
                        created.put(f.getName());
                    }
                }
            }
            record.put("files", created);

            state = RfidAuth.assessment(command, output);
            if (state == null) state = classify(output);

            if (AutoInspect.isSearch(command) && !offline && tagInfo != null) {
                record.put("tagId", tagInfo.id)
                      .put("family", TagInfo.FAMILIES[tagInfo.family])
                      .put("tagReport", tagInfo.report());
                state = tagInfo.detected
                    ? (tagInfo.ambiguous
                        ? "Обнаружены разные метки"
                        : "Метка найдена · сведения на вкладке Чтение")
                    : "Метка не обнаружена";
            }

        } catch (Exception ex) {
            state = ex instanceof TimeoutException   ? "Тайм-аут"
                  : ex instanceof CancellationException ? "Остановлена"
                  : "Ошибка";
            emit("[ProxmarkDesk] " + ex.getMessage());
            state += ": " + ex.getMessage();
            if (command.startsWith("[подключение")) stopClientProcess();
        } finally {
            try {
                JSONArray files = new JSONArray();
                File[] saved = dumps.listFiles();
                if (saved != null) {
                    for (File f : saved) {
                        if (f.isFile() && !before.contains(f.getName())) files.put(f.getName());
                    }
                }
                record.put("files", files);
            } catch (Exception ignored) {}

            try {
                record.put("status", state).put("finished", System.currentTimeMillis());
                writeRecord(id, record);
            } catch (Exception ex) { state = "Ошибка сохранения журнала"; }

            synchronized (processLock) {
                if (operationLog != null) {
                    try { operationLog.close(); } catch (IOException ignored) {}
                    operationLog = null;
                }
            }

            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .remove("draft").apply();

            publishState(state, null);
            requestMirror();
        }
    }

    // -------------------------------------------------------------------------
    // Internal: syncExchange — blocking command send/receive
    // -------------------------------------------------------------------------

    /**
     * Send a command synchronously, waiting up to timeoutS for the PMDESK_END marker.
     * Delegates to CommandSessionManager.submit() and blocks the calling thread.
     */
    private String syncExchange(String command, int timeoutS) throws Exception {
        if (firmwareUpdating.get()) throw new IOException("Выполняется обновление устройства");
        String validation = ClientInput.error(command);
        if (validation != null) throw new IOException(validation);
        if (cache == null) throw new IOException(
            "Хранилище результатов недоступно; команда не отправлена");

        cache.begin(command,
            tagInfo != null && !tagCached && !tagInfo.ambiguous ? tagInfo.id : "",
            deviceProfile);
        String cacheState = "Прервана";

        try {
            CompletableFuture<String> future = csm.submit(command, timeoutS);
            String text = future.get(Math.max(10, Math.min(86400, timeoutS)) + 5L,
                TimeUnit.SECONDS);
            cacheState = classify(text);
            csm.finish(cacheState.equals("Команда завершена")
                ? CommandState.SUCCESS : CommandState.ERROR, cacheState);
            return text;
        } catch (TimeoutException ex) {
            cacheState = "Тайм-аут";
            publishState("Тайм-аут · команда ещё владеет потоком; ожидается синхронизация", null);
            throw new TimeoutException(
                "Истекло время ожидания. Клиент не отключён; новые команды заблокированы "
                + "до синхронизации. Если операция не завершится, используйте «Остановить сеанс».");
        } catch (Exception e) {
            cacheState = "Прервана: " + e.getMessage();
            throw e;
        } finally {
            try {
                cache.finish(cacheError.isEmpty() ? cacheState
                    : "Ошибка сохранения: " + cacheError);
            } catch (Exception e) { cacheError = e.getMessage(); }
            requestMirror();
        }
    }

    // -------------------------------------------------------------------------
    // Internal: stdout pump
    // -------------------------------------------------------------------------

    private void pump(Process owner) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(owner.getInputStream(),
                    StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = ANSI.matcher(line).replaceAll("");
                synchronized (processLock) {
                    if (client != owner) break;
                    if (line.matches("PMDESK_PID_[0-9]+")) {
                        rootPid = Long.parseLong(line.substring(11));
                        continue;
                    }
                }
                // Delegate to CSM first (handles marker + transport error + progress).
                if (line.contains("Capabilities structure version")) startupFailure = line;
                csm.onLine(line);
                // Cache append and screen emit for all non-marker lines.
                if (!line.contains("PMDESK_END_")) {
                    try { if (cache != null) cache.append(line); }
                    catch (Exception e) { cacheError = e.getMessage(); }
                    emit(line);
                }
            }
        } catch (IOException ex) {
            synchronized (processLock) {
                if (client == owner) emit("[Вывод] " + ex.getMessage());
            }
        } finally {
            synchronized (processLock) {
                if (client == owner) connectionLost();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal: process stop
    // -------------------------------------------------------------------------

    private void stopClientProcess() {
        Process old;
        long pid;
        boolean wasRoot;
        synchronized (processLock) {
            old     = client;
            pid     = rootPid;
            wasRoot = rootClient;
            client  = null;
            stdin   = null;
            rootPid = 0;
        }
        csm.setWriter(null);
        if (old != null) {
            if (wasRoot && pid > 1 && old.isAlive()) {
                final long p = pid;
                final Process o = old;
                new Thread(() -> {
                    try {
                        Process kill = new ProcessBuilder("su", "-c", "kill -TERM " + p).start();
                        kill.waitFor(5, TimeUnit.SECONDS);
                        kill.destroy();
                    } catch (Exception ignored) {}
                    o.destroy();
                }, "pm3-stop").start();
            } else {
                old.destroy();
            }
        }
        if (!firmwareUpdating.get()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    // -------------------------------------------------------------------------
    // Internal: state helpers
    // -------------------------------------------------------------------------

    private void updateClientState(ClientState s) {
        synchronized (stateLock) { clientState = s; }
    }

    /**
     * Atomically build and publish a new SessionState snapshot.
     * Called from any thread.
     */
    public void publishState(String statusText, ProgressState progress) {
        UsbState    usb;
        ClientState cli;
        TagState    tag;
        synchronized (stateLock) {
            usb = usbState;
            cli = clientState;
            tag = tagState;
        }
        sessionState = new SessionState.Builder(sessionState)
            .usbState(usb)
            .clientState(cli)
            .commandState(csm.getState())
            .tagState(tag)
            .currentCommand(csm.getCurrentCommand())
            .commandStartedAtMs(csm.getCommandStartMs())
            .statusText(statusText != null ? statusText : "")
            .tagInfo(tagInfo)
            .tagCached(tagCached)
            .progress(progress != null ? progress : csm.getProgress())
            .build();
    }

    // -------------------------------------------------------------------------
    // Internal: record
    // -------------------------------------------------------------------------

    void writeRecord(String id, JSONObject value) throws IOException {
        File target = new File(operations, id + ".json");
        File temp   = new File(operations, id + ".tmp");
        Files.write(temp.toPath(), value.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), target.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    public static String classify(String output) {
        if (Pattern.compile("(?i)partial dump|partial.*complete")
                .matcher(output).find()) return "Частичный дамп";
        if (Pattern.compile(
                "(?im)\\[!!\\]|\\[-\\]|\\berror\\b|\\bfailed\\b"
                + "|no known|couldn't|cannot|trace is empty")
                .matcher(output).find()) return "Проверьте вывод";
        return "Команда завершена";
    }

    // -------------------------------------------------------------------------
    // Version tag (used in status bar)
    // -------------------------------------------------------------------------

    private static final class CliVersion {
        static final String CLIENT = "Iceman " + CliCompat.CURRENT_VERSION;
    }
}
