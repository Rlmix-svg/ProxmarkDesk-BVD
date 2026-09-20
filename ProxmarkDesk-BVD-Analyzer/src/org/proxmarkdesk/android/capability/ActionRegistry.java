// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.capability;

import org.proxmarkdesk.android.session.ClientState;
import org.proxmarkdesk.android.session.SessionState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalogue of logical actions and their availability rules.
 *
 * Usage:
 *   List<ActionDef> actions = ActionRegistry.forCapability(cap);
 *   boolean ok = ActionRegistry.isAvailable(action, cap, session);
 *   String cmd = ActionRegistry.buildCommand(action, params);
 *
 * The UI never assembles command strings directly.
 */
public final class ActionRegistry {

    private ActionRegistry() {}

    // -------------------------------------------------------------------------
    // Action catalogue (all known P0/P1 actions)
    // -------------------------------------------------------------------------

    private static final List<ActionDef> ALL = buildAll();

    private static List<ActionDef> buildAll() {
        List<ActionDef> list = new ArrayList<>();

        // ---- MIFARE Classic ----
        list.add(new ActionDef.Builder()
            .id("classic.chk")
            .protocols("hf mf")
            .commandTemplate("hf mf fchk --{size}")
            .paramDefault("size", "1k")
            .category("Ключи и аутентификация")
            .description("Быстрая проверка стандартных ключей MIFARE Classic")
            .requiresTag(true).requiresKeys(false).requiresDump(false)
            .note("Используется быстрый fchk текущего Iceman. Для своего словаря используйте отдельное действие ниже.")
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.chk.file")
            .protocols("hf mf")
            .commandTemplate("hf mf fchk --{size} -f {dict}")
            .paramDefault("size", "1k")
            .category("Словари / ключи")
            .description("Проверить ключи Classic из своего файла")
            .requiresTag(true).requiresConnection(true)
            .note("Импортируйте словарь; для Mini/1K/2K/4K размер подставляется из обнаруженной карты.")
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.nested")
            .protocols("hf mf")
            .commandTemplate("hf mf nested --{size} --blk {blk} -a -k {keyA}")
            .paramDefault("size", "1k").paramDefault("blk", "0")
            .category("Ключи и аутентификация")
            .description("Восстановить ключи через nested-атаку")
            .requiresTag(true).requiresKeys(false)
            .note("Требуется хотя бы один известный ключ сектора.")
            .note("Занимает несколько минут на реальной карте.")
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.autopwn")
            .protocols("hf mf")
            .commandTemplate("hf mf autopwn")
            .category("Ключи и аутентификация")
            .description("Автоматическое восстановление всех ключей и создание дампа")
            .requiresTag(true).requiresKeys(false)
            .note("Запускает chk + nested/hardnested по необходимости.")
            .note("Сохраняет key.bin и dump.bin автоматически.")
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.dump")
            .protocols("hf mf")
            .commandTemplate("hf mf dump --{size}")
            .paramDefault("size", "1k")
            .category("Чтение и информация")
            .description("Сохранить полный дамп MIFARE Classic")
            .requiresTag(true).requiresKeys(true)
            .note("Требуется файл ключей hf-mf-{uid}-key.bin.")
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.rdbl")
            .protocols("hf mf")
            .commandTemplate("hf mf rdbl --blk {blk} -{keytype} -k {keyA}")
            .paramDefault("blk", "0").paramDefault("keytype", "a")
            .category("Чтение и информация")
            .description("Прочитать один блок MIFARE Classic")
            .requiresTag(true).requiresKeys(false)
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.sim")
            .protocols("hf mf")
            .commandTemplate("hf mf eclr; hf mf eload --{size} -f {path}; hf mf sim --{size}")
            .paramDefault("size", "1k")
            .category("Захват и эмуляция")
            .description("Эмулировать MIFARE Classic из дампа")
            .requiresTag(false).requiresKeys(false).requiresDump(true).requiresConnection(true)
            .note("Требуется полный дамп правильного размера.")
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.info")
            .protocols("hf mf")
            .commandTemplate("hf 14a info")
            .category("Чтение и информация")
            .description("Информация о карте: ATQA, SAK, ATS")
            .requiresTag(true).requiresConnection(true)
            .build());

        // ---- MIFARE Ultralight / NTAG ----
        list.add(new ActionDef.Builder()
            .id("mfu.info")
            .protocols("hf mfu")
            .commandTemplate("hf mfu info")
            .category("Чтение и информация")
            .description("Информация о Ultralight / NTAG без попытки аутентификации")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("mfu.dump")
            .protocols("hf mfu")
            .commandTemplate("hf mfu dump -f {path}")
            .category("Чтение и информация")
            .description("Сохранить дамп Ultralight / NTAG")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("mfu.dump.key")
            .protocols("hf mfu")
            .commandTemplate("hf mfu dump -f {path} -k {keyA}")
            .category("Чтение и информация")
            .description("Сохранить дамп Ultralight / NTAG с паролем")
            .requiresTag(true)
            .note("Используйте если карта защищена паролем.")
            .build());

        list.add(new ActionDef.Builder()
            .id("mfu.sim")
            .protocols("hf mfu")
            .commandTemplate("hf mf eclr; hf mfu eload -f {path}; hf mfu sim -t {type}")
            .paramDefault("type", "2")
            .category("Захват и эмуляция")
            .description("Эмулировать Ultralight / NTAG из дампа")
            .requiresTag(false).requiresDump(true).requiresConnection(true)
            .note("Остановите эмуляцию физической кнопкой Proxmark3.")
            .build());

        list.add(new ActionDef.Builder()
            .id("mfu.pwdauth")
            .protocols("hf mfu")
            .commandTemplate("hf 14a raw -c -v 1B{keyA}")
            .category("Ключи и аутентификация")
            .description("Проверить пароль PWD_AUTH (Ultralight EV1 / NTAG)")
            .requiresTag(true)
            .note("Однократная попытка. UID сверяется до отправки пароля.")
            .build());

        // ---- ISO 15693 ----
        list.add(new ActionDef.Builder()
            .id("iso15693.info")
            .protocols("hf 15")
            .commandTemplate("hf 15 info")
            .category("Чтение и информация")
            .description("Информация об ISO 15693 карте")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("iso15693.dump")
            .protocols("hf 15")
            .commandTemplate("hf 15 dump -f {path}")
            .category("Чтение и информация")
            .description("Сохранить дамп ISO 15693")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("iso15693.sim")
            .protocols("hf 15")
            .commandTemplate("hf 15 eload -f {path}; hf 15 sim")
            .category("Захват и эмуляция")
            .description("Эмулировать ISO 15693 из дампа")
            .requiresTag(false).requiresDump(true).requiresConnection(true)
            .build());

        // ---- Safe protocol-specific identification actions ----
        list.add(new ActionDef.Builder().id("desfire.info").protocols("hf mfdes")
            .commandTemplate("hf mfdes info").category("Чтение и информация")
            .description("Информация о MIFARE DESFire").requiresTag(true).build());
        list.add(new ActionDef.Builder().id("hf14b.info").protocols("hf 14b")
            .commandTemplate("hf 14b info").category("Чтение и информация")
            .description("Информация об ISO 14443-B карте").requiresTag(true).build());
        list.add(new ActionDef.Builder().id("felica.info").protocols("hf felica")
            .commandTemplate("hf felica info").category("Чтение и информация")
            .description("Информация о FeliCa карте").requiresTag(true).build());
        list.add(new ActionDef.Builder().id("iclass.info").protocols("hf iclass")
            .commandTemplate("hf iclass info").category("Чтение и информация")
            .description("Информация об iCLASS / PicoPass карте").requiresTag(true).build());
        list.add(new ActionDef.Builder().id("legic.info").protocols("hf legic")
            .commandTemplate("hf legic info").category("Чтение и информация")
            .description("Информация о LEGIC Prime карте").requiresTag(true).build());
        list.add(new ActionDef.Builder().id("topaz.info").protocols("hf topaz")
            .commandTemplate("hf topaz info").category("Чтение и информация")
            .description("Информация о Topaz карте").requiresTag(true).build());
        list.add(new ActionDef.Builder().id("lf.em.reader").protocols("lf em 410x")
            .commandTemplate("lf em 410x reader").category("Чтение и информация")
            .description("Повторно прочитать EM410x идентификатор").requiresTag(false).build());
        list.add(new ActionDef.Builder().id("lf.hid.reader").protocols("lf hid")
            .commandTemplate("lf hid reader").category("Чтение и информация")
            .description("Повторно прочитать HID Prox идентификатор").requiresTag(false).build());
        list.add(new ActionDef.Builder().id("t55xx.info").protocols("lf t55xx")
            .commandTemplate("lf t55xx info").category("Чтение и информация")
            .description("Показать конфигурацию T55xx").requiresTag(true).build());

        // ---- Protocol-specific dictionary checks for the bundled Iceman client ----
        list.add(new ActionDef.Builder().id("mfp.chk.file").protocols("hf mfp")
            .commandTemplate("hf mfp chk -f {dict}").category("Словари / ключи")
            .description("Проверить ключи MIFARE Plus из своего файла")
            .requiresTag(true).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("mfu.cchk.file").protocols("hf mfu")
            .commandTemplate("hf mfu chk -f {dict}").category("Словари / ключи")
            .description("Проверить 3DES-ключи Ultralight C из своего файла")
            .requiresTag(true).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("mfu.aeschk.file").protocols("hf mfu")
            .commandTemplate("hf mfu chk -f {dict}").category("Словари / ключи")
            .description("Проверить AES-ключи Ultralight AES из своего файла")
            .requiresTag(true).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("desfire.chk.file").protocols("hf mfdes")
            .commandTemplate("hf mfdes chk -f {dict}").category("Словари / ключи")
            .description("Проверить ключи DESFire из своего файла")
            .requiresTag(true).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("iclass.chk.file").protocols("hf iclass")
            .commandTemplate("hf iclass chk -f {dict}").category("Словари / ключи")
            .description("Проверить iCLASS-ключи из своего файла")
            .requiresTag(true).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("t55xx.chk.file").protocols("lf t55xx")
            .commandTemplate("lf t55xx chk -f {dict}").category("Словари / ключи")
            .description("Проверить пароли T55xx из своего файла")
            .requiresTag(true).requiresConnection(true)
            .note("Внимание: Iceman предупреждает о риске для незащищённых T55xx; используйте только когда тип подтверждён.")
            .build());
        list.add(new ActionDef.Builder().id("hitag.chk.file").protocols("lf hitag")
            .commandTemplate("lf hitag chk -f {dict}").category("Словари / ключи")
            .description("Проверить Hitag-ключи/пароли из своего файла")
            .requiresTag(true).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("em4x05.chk.file").protocols("lf em 4x05")
            .commandTemplate("lf em 4x05 chk -f {dict}").category("Словари / ключи")
            .description("Проверить пароли EM4x05 из своего файла")
            .requiresTag(true).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("em4x50.chk.file").protocols("lf em 4x50")
            .commandTemplate("lf em 4x50 chk -f {dict}").category("Словари / ключи")
            .description("Проверить пароли EM4x50 из своего файла")
            .requiresTag(true).requiresConnection(true).build());

        // ---- Primary read actions used by the adaptive Reading UI ----
        list.add(new ActionDef.Builder()
            .id("tag.auto")
            .commandTemplate("auto")
            .category("Чтение и информация")
            .description("Автоматически найти HF/LF метку и определить протокол")
            .requiresTag(false).requiresConnection(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("mfu.rdbl")
            .protocols("hf mfu")
            .commandTemplate("hf mfu rdbl -b {blk}")
            .category("Чтение и информация")
            .description("Прочитать страницу Ultralight / NTAG")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("mfu.rdbl.key")
            .protocols("hf mfu")
            .commandTemplate("hf mfu rdbl -b {blk} -k {keyA}")
            .category("Чтение и информация")
            .description("Прочитать страницу Ultralight / NTAG с паролем")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("iso15693.rdbl")
            .protocols("hf 15")
            .commandTemplate("hf 15 rdbl -b {blk}")
            .category("Чтение и информация")
            .description("Прочитать блок ISO 15693")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.ndef")
            .protocols("hf mf")
            .commandTemplate("hf mf ndefread")
            .category("Чтение и информация")
            .description("Прочитать NDEF с MIFARE Classic")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.ndef.keya")
            .protocols("hf mf")
            .commandTemplate("hf mf ndefread -k {keyA}")
            .category("Чтение и информация")
            .description("Прочитать NDEF с MIFARE Classic по Key A")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("classic.ndef.keyb")
            .protocols("hf mf")
            .commandTemplate("hf mf ndefread -k {keyA} -b")
            .category("Чтение и информация")
            .description("Прочитать NDEF с MIFARE Classic по Key B")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("mfu.ndef")
            .protocols("hf mfu")
            .commandTemplate("hf mfu ndefread")
            .category("Чтение и информация")
            .description("Прочитать NDEF с Ultralight / NTAG")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("mfu.ndef.key")
            .protocols("hf mfu")
            .commandTemplate("hf mfu ndefread -k {keyA}")
            .category("Чтение и информация")
            .description("Прочитать NDEF с Ultralight / NTAG с паролем")
            .requiresTag(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("t55xx.dump")
            .protocols("lf t55xx")
            .commandTemplate("lf t55xx dump -f {path}")
            .category("Чтение и информация")
            .description("Сохранить дамп T55xx")
            .requiresTag(true)
            .build());

        // ---- Generic HF search / sniff ----
        list.add(new ActionDef.Builder()
            .id("hf.search")
            .protocols("hf")
            .commandTemplate("hf search")
            .category("Чтение и информация")
            .description("Найти HF карту и определить протокол")
            .requiresTag(false).requiresConnection(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("lf.search")
            .protocols("lf")
            .commandTemplate("lf search")
            .category("Чтение и информация")
            .description("Найти LF карту и определить протокол")
            .requiresTag(false).requiresConnection(true)
            .build());

        list.add(new ActionDef.Builder()
            .id("hf.sniff.14a")
            .protocols("hf 14a")
            .commandTemplate("hf 14a sniff")
            .category("Захват и эмуляция")
            .description("Захват ISO 14443-A трассы")
            .requiresTag(false).requiresConnection(true)
            .note("Остановите кнопкой устройства, затем trace list -t 14a.")
            .build());

        // ---- Application-owned utility actions ----
        list.add(new ActionDef.Builder().id("hw.tune").commandTemplate("hw tune")
            .category("Диагностика").description("Проверить антенны Proxmark3")
            .requiresTag(false).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("hw.version").commandTemplate("hw version")
            .category("Диагностика").description("Версия клиента и устройства")
            .requiresTag(false).requiresConnection(false).build());
        list.add(new ActionDef.Builder().id("hw.status").commandTemplate("hw status")
            .category("Диагностика").description("Состояние оборудования")
            .requiresTag(false).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("lua.list").commandTemplate("script list")
            .category("Lua").description("Список Lua-скриптов")
            .requiresTag(false).requiresConnection(false).build());
        list.add(new ActionDef.Builder().id("lua.envcheck").commandTemplate("script run pmdesk_envcheck")
            .category("Lua").description("Проверить встроенное Lua-окружение")
            .requiresTag(false).requiresConnection(false).build());
        list.add(new ActionDef.Builder().id("lf.read").commandTemplate("lf read")
            .category("Сигналы").description("Получить LF-сигнал")
            .requiresTag(false).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("lf.search.buffer").commandTemplate("lf search -1")
            .category("Сигналы").description("Декодировать LF из текущего буфера")
            .requiresTag(false).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("data.save").commandTemplate("data save -f {path}")
            .category("Сигналы").description("Сохранить текущий буфер сигнала")
            .requiresTag(false).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("classic.dump.file")
            .protocols("hf mf").commandTemplate("hf mf dump --{size} -k classic-keys.bin -f {path}")
            .category("Чтение и информация").description("Сохранить полный Classic-дамп с импортированным файлом ключей")
            .requiresTag(true).requiresKeys(false).requiresConnection(true).build());
        list.add(new ActionDef.Builder().id("trace.hf14a.save").commandTemplate("hf 14a list; trace save -f {path}")
            .category("Захват и эмуляция").description("Показать и сохранить ISO14443-A трассу")
            .requiresTag(false).requiresConnection(true).build());

        String[][] sniffDefs = {
            {"sniff.hf14b","hf 14b sniff"}, {"sniff.hf15","hf 15 sniff"},
            {"sniff.felica","hf felica sniff"}, {"sniff.iclass","hf iclass sniff"},
            {"sniff.topaz","hf topaz sniff"}, {"sniff.lf","lf sniff"},
            {"trace.hf14a","hf 14a list"}, {"trace.hf14b","hf 14b list"},
            {"trace.hf15","hf 15 list"}, {"trace.felica","hf felica list"},
            {"trace.iclass","hf iclass list"}, {"trace.topaz","hf topaz list"},
            {"trace.lf","lf search -1"}
        };
        for (String[] def : sniffDefs) {
            list.add(new ActionDef.Builder().id(def[0]).commandTemplate(def[1])
                .category("Захват и эмуляция").description(def[1])
                .requiresTag(false).requiresConnection(true).build());
        }

        return Collections.unmodifiableList(list);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Return all actions relevant to the given capability.
     * Actions without a matching protocol are excluded.
     */
    public static List<ActionDef> forCapability(CardCapability cap) {
        if (cap == null) return Collections.emptyList();
        List<ActionDef> result = new ArrayList<>();
        for (ActionDef a : ALL) {
            if (matchesProtocol(a, cap)) result.add(a);
        }
        return result;
    }

    /**
     * Actions shown on the card-specific Operations screen.
     * Sniff/trace and global search/tool actions intentionally live in their
     * dedicated flows, so this projection only exposes operations for the
     * already identified card.
     */
    public static List<ActionDef> forCardMenu(CardCapability cap) {
        if (cap == null || cap.protocol < 0) return Collections.emptyList();
        List<ActionDef> result = new ArrayList<>();
        for (ActionDef action : forCapability(cap)) {
            String id = action.id == null ? "" : action.id;
            String category = action.category == null ? "" : action.category;
            if (action.protocols == null || action.protocols.length == 0) continue;
            if (action.requiresDump) continue;
            if (id.equals("tag.auto") || id.equals("hf.search") || id.equals("lf.search")) continue;
            if (id.contains("sniff") || id.startsWith("trace.")) continue;
            if (id.equals("classic.dump.file")) continue;
            if (category.equals("Диагностика") || category.equals("Lua") || category.equals("Сигналы")) continue;
            if (!relevantToSubtype(action, cap)) continue;
            result.add(action);
        }
        return result;
    }

    /** Backwards-compatible name used by older tests/callers. */
    public static List<ActionDef> forOperationScreen(CardCapability cap) {
        return forCardMenu(cap);
    }

    private static boolean relevantToSubtype(ActionDef action, CardCapability cap) {
        String id = action.id == null ? "" : action.id;
        String subtype = cap.subtype == null ? "" : cap.subtype.toUpperCase(Locale.ROOT);

        if ((id.startsWith("classic.ndef") || id.startsWith("mfu.ndef"))
                && cap.ndefState == CardCapability.NdefState.ABSENT) return false;

        if (cap.protocol == 2) {
            boolean pwdCapable = cap.authType == CardCapability.AuthType.PWD
                || subtype.contains("NTAG") || subtype.contains("EV1");
            if (id.equals("mfu.pwdauth") || id.equals("mfu.dump.key")
                    || id.equals("mfu.rdbl.key") || id.equals("mfu.ndef.key")) {
                return pwdCapable;
            }
            if (id.equals("mfu.cchk.file")) return subtype.contains("ULTRALIGHT C");
            if (id.equals("mfu.aeschk.file")) return subtype.contains("ULTRALIGHT AES");
        }
        return true;
    }

    /** Return all registered actions (for full catalogue view). */
    public static List<ActionDef> all() {
        return ALL;
    }

    /** Find an action by its stable logical id. Returns null when unknown. */
    public static ActionDef findById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (ActionDef action : ALL) {
            if (id.equals(action.id)) return action;
        }
        return null;
    }

    /**
     * Evaluate whether an action can be started right now.
     *
     * @return null if available, or a human-readable reason string if not.
     */
    public static String unavailableReason(ActionDef action,
                                           CardCapability cap,
                                           SessionState session) {
        if (action == null) return "Действие не определено";

        // Session constraints
        if (session == null || !session.isClientAlive()) {
            return "Запустите клиент или подключите устройство";
        }
        if (session.isBusy()) {
            return "Дождитесь завершения текущей команды";
        }
        if (action.requiresConnection
                && session.client == ClientState.OFFLINE) {
            return "Требуется USB-подключение к Proxmark3";
        }

        // Capability constraints
        if (cap == null) cap = CardCapability.UNKNOWN;

        if (action.requiresKeys && !cap.hasKeys) {
            return "Нет файла ключей. Сначала выполните autopwn или chk.";
        }
        if (action.requiresDump
                && cap.emulateStatus == CardCapability.EmulateStatus.UNSUPPORTED) {
            return cap.emulateReason.isEmpty()
                    ? "Эмуляция не поддерживается для данного протокола"
                    : cap.emulateReason;
        }
        if (action.requiresDump
                && cap.emulateStatus == CardCapability.EmulateStatus.CONDITIONAL) {
            return cap.emulateReason.isEmpty()
                    ? "Не выполнены условия эмуляции"
                    : cap.emulateReason;
        }

        return null; // available
    }

    /** Convenience: true when unavailableReason returns null. */
    public static boolean isAvailable(ActionDef action,
                                      CardCapability cap,
                                      SessionState session) {
        return unavailableReason(action, cap, session) == null;
    }

    /**
     * Build the final command string from a template and a parameter map.
     * Missing keys fall back to ActionDef.paramDefaults, then to empty string.
     *
     * @param action action definition
     * @param params caller-supplied parameters (may be null)
     * @return ready-to-send command string
     */
    public static String buildCommand(ActionDef action, Map<String, String> params) {
        if (action == null) throw new IllegalArgumentException("ActionDef is null");
        String cmd = action.commandTemplate;
        Map<String, String> all = new HashMap<>(action.paramDefaults);
        if (params != null) all.putAll(params);
        for (Map.Entry<String, String> e : all.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            cmd = cmd.replace("{" + e.getKey() + "}", value);
        }
        java.util.regex.Matcher unresolved = java.util.regex.Pattern
            .compile("\\{[A-Za-z][A-Za-z0-9_]*\\}")
            .matcher(cmd);
        if (unresolved.find()) {
            throw new IllegalArgumentException(
                "Не задан параметр действия: " + unresolved.group().substring(1, unresolved.group().length() - 1));
        }
        return cmd.trim();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean matchesProtocol(ActionDef action, CardCapability cap) {
        if (action.protocols == null || action.protocols.length == 0) return true;
        String prefix = protocolPrefix(cap);
        if (prefix.isEmpty()) return false;
        for (String p : action.protocols) {
            if (prefix.equals(p) || prefix.startsWith(p + " ")) return true;
        }
        return false;
    }

    /** Map CardCapability.protocol index → command prefix used in ActionDef.protocols. */
    private static String protocolPrefix(CardCapability cap) {
        if (cap == null || cap.protocol < 0) return "";
        switch (cap.protocol) {
            case 0:  return "hf 14a";
            case 1:  return "hf mf";
            case 2:  return "hf mfu";
            case 3:  return "hf 15";
            case 4:  return "hf mfdes";
            case 5:  return "lf em 410x";
            case 6:  return "lf hid";
            case 7:  return "lf t55xx";
            case 8:  return "hf 14b";
            case 9:  return "hf felica";
            case 10: return "hf iclass";
            case 11: return "hf legic";
            case 12: return "hf topaz";
            case 14: return "hf mfp";
            case 15: return "lf hitag";
            case 16: return "lf em 4x05";
            case 17: return "lf em 4x50";
            default: return "";
        }
    }
}
