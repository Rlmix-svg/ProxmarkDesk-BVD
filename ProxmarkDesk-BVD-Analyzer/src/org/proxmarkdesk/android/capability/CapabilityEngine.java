// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.capability;

import org.proxmarkdesk.android.DumpAnalyzer;
import org.proxmarkdesk.android.DumpLibrary;
import org.proxmarkdesk.android.TagInfo;

import java.io.File;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derives CardCapability from a TagInfo, a dump file, or a combination.
 *
 * Design rule: when a capability is uncertain, return UNKNOWN — never
 * silently downgrade to UNSUPPORTED.
 */
public final class CapabilityEngine {

    // MIFARE Classic family index in TagInfo.FAMILIES
    private static final int PROTO_CLASSIC    = 1;
    private static final int PROTO_ULTRALIGHT = 2;
    private static final int PROTO_ISO15693   = 3;
    private static final int PROTO_DESFIRE    = 4;
    private static final int PROTO_LF_EM      = 5;
    private static final int PROTO_LF_HID     = 6;
    private static final int PROTO_LF_T55XX   = 7;
    private static final int PROTO_14443B     = 8;
    private static final int PROTO_FELICA     = 9;
    private static final int PROTO_ICLASS     = 10;
    private static final int PROTO_LEGIC      = 11;
    private static final int PROTO_TOPAZ      = 12;
    private static final int PROTO_PLUS       = 14;
    private static final int PROTO_HITAG      = 15;
    private static final int PROTO_EM4X05     = 16;
    private static final int PROTO_EM4X50     = 17;

    private static final Pattern ATS_PATTERN =
        Pattern.compile("(?im)^\\s*ATS\\s*[:.]+\\s*[0-9a-f]");
    private static final Pattern NDEF_CC =
        Pattern.compile("(?i)E1\\s+10|ndef capability container", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTIAL_DUMP =
        Pattern.compile("(?i)partial dump|only.*block|incomplete");

    private CapabilityEngine() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Build capability from a live TagInfo (result of hf search / hf mfu info etc.).
     *
     * @param tag      TagInfo from the last successful search
     * @param filesDir getFilesDir() — used to check for existing key files
     */
    public static CardCapability fromTagInfo(TagInfo tag, File filesDir) {
        if (tag == null || !tag.detected) return CardCapability.UNKNOWN;

        CardCapability.Builder b = new CardCapability.Builder()
            .uid(tag.id)
            .protocol(tag.family)
            .subtype(detectSubtype(tag))
            .verified(true);

        applyMemory(b, tag);
        applyAuth(b, tag);
        applyNdef(b, tag);
        applyIso14443_4(b, tag);
        applySniff(b, tag.family);
        applyKeyFile(b, tag.id, filesDir);
        applyEmulation(b, tag.family, null, filesDir, tag.id);

        return b.build();
    }

    /**
     * Build capability from a dump file (used by Dump Viewer and Library).
     *
     * @param file     dump file (.bin / .eml / .json)
     * @param tag      optional TagInfo for cross-referencing; may be null
     * @param filesDir getFilesDir()
     */
    public static CardCapability fromDump(File file, TagInfo tag, File filesDir) {
        if (file == null || !file.isFile()) return CardCapability.UNKNOWN;

        CardCapability.Builder b = new CardCapability.Builder();

        // Try to extract UID from the file itself.
        String uid = "";
        try { uid = DumpLibrary.uid(file); } catch (Exception ignored) {}
        if (uid.isEmpty() && tag != null) uid = tag.id;
        b.uid(uid);

        // Try to detect protocol from tag or file name heuristics.
        int protocol = tag != null ? tag.family : guessProtocolFromName(file.getName());
        b.protocol(protocol);

        // Load raw bytes to determine size.
        byte[] data = null;
        try { data = DumpAnalyzer.load(file); } catch (Exception ignored) {}

        if (data != null) {
            b.memorySizeBytes(data.length);
            applyClassicSectors(b, protocol, data.length);
        }

        if (tag != null) {
            b.subtype(detectSubtype(tag));
            applyAuth(b, tag);
            applyNdef(b, tag);
            applyIso14443_4(b, tag);
            b.verified(true);
        }

        applySniff(b, protocol);
        applyKeyFile(b, uid, filesDir);
        applyEmulation(b, protocol, data, filesDir, uid);

        // Detect partial dump from file name convention.
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.contains("partial") || (data != null && tag != null
                && tag.memory != null && !tag.memory.isEmpty()
                && PARTIAL_DUMP.matcher(tag.details).find())) {
            b.partialAccess(true);
        }

        return b.build();
    }

    /**
     * Merge additional information from a command output into an existing capability.
     * Used after "hf mfu info", "hf mf info" etc. to enrich the snapshot.
     */
    public static CardCapability merge(CardCapability base, String commandOutput,
                                       File filesDir) {
        if (base == null) base = CardCapability.UNKNOWN;
        if (commandOutput == null || commandOutput.isEmpty()) return base;

        CardCapability.Builder b = new CardCapability.Builder()
            .uid(base.uid)
            .protocol(base.protocol)
            .subtype(base.subtype)
            .memorySizeBytes(base.memorySizeBytes)
            .sectors(base.sectors)
            .authType(base.authType)
            .hasKeys(base.hasKeys)
            .partialAccess(base.partialAccess)
            .ndefState(base.ndefState)
            .iso14443_4(base.iso14443_4)
            .sniffSupported(base.sniffSupported)
            .emulateStatus(base.emulateStatus)
            .emulateReason(base.emulateReason)
            .verified(base.verified);

        // Refine NDEF state.
        if (base.ndefState == CardCapability.NdefState.UNKNOWN) {
            if (NDEF_CC.matcher(commandOutput).find()) {
                b.ndefState(CardCapability.NdefState.PRESENT);
            }
        }

        // Refine ISO14443-4.
        if (!base.iso14443_4 && ATS_PATTERN.matcher(commandOutput).find()) {
            b.iso14443_4(true);
        }

        // Re-check key file (autopwn may have just created it).
        applyKeyFile(b, base.uid, filesDir);

        // Re-evaluate emulation now that key file may exist.
        applyEmulation(b, base.protocol, null, filesDir, base.uid);

        b.verified(true);
        return b.build();
    }

    /**
     * Check whether a Classic key file exists for the given UID.
     * File name: hf-mf-{UID}-key.bin  (case-insensitive UID).
     */
    public static boolean keyFileExists(File filesDir, String uid) {
        if (filesDir == null || uid == null || uid.isEmpty()) return false;
        // Iceman saves to filesDir directly (not library subfolder).
        File f = new File(filesDir, "hf-mf-" + uid.toUpperCase(Locale.ROOT) + "-key.bin");
        return f.isFile() && f.length() > 0;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String detectSubtype(TagInfo tag) {
        if (tag == null) return "";
        String d = tag.details.toLowerCase(Locale.ROOT);
        // Ultralight / NTAG exact families
        if (d.contains("ultralight aes")) return "Ultralight AES";
        if (d.contains("ultralight c")) return "Ultralight C";
        for (String name : new String[]{"ntag213","ntag215","ntag216","ntag210","ntag212",
                                         "ntag203","ntag424"}) {
            if (d.contains(name)) return name.toUpperCase(Locale.ROOT);
        }
        if (d.contains("ultralight ev1") || (tag.family == PROTO_ULTRALIGHT && d.contains("ev1"))) return "Ultralight EV1";
        if (tag.family == PROTO_ULTRALIGHT && d.contains("ultralight")) return "MIFARE Ultralight";

        if (tag.family == PROTO_PLUS) return "MIFARE Plus";
        if (tag.family == PROTO_HITAG) return "Hitag";
        if (tag.family == PROTO_EM4X05) return "EM4x05";
        if (tag.family == PROTO_EM4X50) return "EM4x50";

        // DESFire generations
        if (tag.family == PROTO_DESFIRE) {
            if (d.contains("ev3")) return "DESFire EV3";
            if (d.contains("ev2")) return "DESFire EV2";
            if (d.contains("ev1")) return "DESFire EV1";
            return "MIFARE DESFire";
        }
        // Classic
        if (tag.family == PROTO_CLASSIC) {
            switch (tag.classicSize) {
                case 0: return "Classic 1K";
                case 1: return "Classic Mini";
                case 2: return "Classic 2K";
                case 3: return "Classic 4K";
            }
        }
        // T55xx
        if (d.contains("t55")) return "T55xx";
        return "";
    }

    private static void applyMemory(CardCapability.Builder b, TagInfo tag) {
        if (tag.family == PROTO_CLASSIC) {
            int[] sizes = {1024, 320, 2048, 4096};
            if (tag.classicSize >= 0 && tag.classicSize < sizes.length) {
                b.memorySizeBytes(sizes[tag.classicSize]);
                applyClassicSectors(b, PROTO_CLASSIC, sizes[tag.classicSize]);
            }
        }
    }

    private static void applyClassicSectors(CardCapability.Builder b, int protocol, int size) {
        if (protocol != PROTO_CLASSIC) return;
        switch (size) {
            case 320:  b.sectors(5);  break;
            case 1024: b.sectors(16); break;
            case 2048: b.sectors(32); break;
            case 4096: b.sectors(40); break;
        }
    }

    private static void applyAuth(CardCapability.Builder b, TagInfo tag) {
        switch (tag.family) {
            case PROTO_CLASSIC:
                b.authType(CardCapability.AuthType.KEY_A_B);
                break;
            case PROTO_ULTRALIGHT: {
                String d = tag.details.toLowerCase(Locale.ROOT);
                if (d.contains("ultralight c")) b.authType(CardCapability.AuthType.DES);
                else if (d.contains("ntag") || d.contains("ev1")) b.authType(CardCapability.AuthType.PWD);
                else if (d.contains("ultralight")) b.authType(CardCapability.AuthType.NONE);
                else b.authType(CardCapability.AuthType.UNKNOWN);
                break;
            }
            case PROTO_DESFIRE:
                b.authType(CardCapability.AuthType.AES);
                break;
            default:
                b.authType(CardCapability.AuthType.NONE);
        }
    }

    private static void applyNdef(CardCapability.Builder b, TagInfo tag) {
        String d = tag.details.toLowerCase(Locale.ROOT);
        if (NDEF_CC.matcher(d).find()
                || d.contains("ndef text")
                || d.contains("ndef uri")) {
            b.ndefState(CardCapability.NdefState.PRESENT);
        }
        // Otherwise leave UNKNOWN — do not assume ABSENT.
    }

    private static void applyIso14443_4(CardCapability.Builder b, TagInfo tag) {
        b.iso14443_4(ATS_PATTERN.matcher(tag.details).find());
    }

    private static void applySniff(CardCapability.Builder b, int protocol) {
        // LF protocols sniff differently (lf sniff) but are still supported.
        // iCLASS / LEGIC / FeliCa sniff supported by Iceman.
        b.sniffSupported(protocol >= 0); // all known protocols support sniff
    }

    private static void applyKeyFile(CardCapability.Builder b, String uid, File filesDir) {
        b.hasKeys(keyFileExists(filesDir, uid));
    }

    private static void applyEmulation(CardCapability.Builder b, int protocol,
                                        byte[] data, File filesDir, String uid) {
        switch (protocol) {
            case PROTO_CLASSIC: {
                int size = data != null ? data.length : -1;
                if (size < 0 && b.memorySizeBytes > 0) size = b.memorySizeBytes;
                boolean validSize = size == 320 || size == 1024 || size == 2048 || size == 4096;
                if (validSize) {
                    b.emulateStatus(CardCapability.EmulateStatus.SUPPORTED);
                } else if (size < 0) {
                    b.emulateStatus(CardCapability.EmulateStatus.CONDITIONAL)
                     .emulateReason("Сначала сохраните полный дамп");
                } else {
                    b.emulateStatus(CardCapability.EmulateStatus.CONDITIONAL)
                     .emulateReason("Неверный размер дампа (" + size + " байт)");
                }
                break;
            }
            case PROTO_ULTRALIGHT:
                if (data != null && data.length >= 16 && data.length % 4 == 0) {
                    b.emulateStatus(CardCapability.EmulateStatus.SUPPORTED);
                } else if (data == null) {
                    b.emulateStatus(CardCapability.EmulateStatus.CONDITIONAL)
                     .emulateReason("Сначала сохраните дамп");
                } else {
                    b.emulateStatus(CardCapability.EmulateStatus.CONDITIONAL)
                     .emulateReason("Неверный размер MFU-дампа (" +
                             (data == null ? 0 : data.length) + " байт)");
                }
                break;
            case PROTO_ISO15693:
                b.emulateStatus(data != null
                        ? CardCapability.EmulateStatus.SUPPORTED
                        : CardCapability.EmulateStatus.CONDITIONAL)
                 .emulateReason(data != null ? "" : "Сначала сохраните дамп");
                break;
            case PROTO_DESFIRE:
                b.emulateStatus(CardCapability.EmulateStatus.CONDITIONAL)
                 .emulateReason("Требуется полный дамп с ключами; DESFire эмуляция ограничена");
                break;
            case PROTO_LF_T55XX:
                b.emulateStatus(CardCapability.EmulateStatus.CONDITIONAL)
                 .emulateReason("Зависит от конкретного типа T55xx и данных");
                break;
            case PROTO_FELICA:
            case PROTO_ICLASS:
                b.emulateStatus(CardCapability.EmulateStatus.UNSUPPORTED)
                 .emulateReason("Эмуляция этого протокола не поддерживается в данной сборке");
                break;
            default:
                // LF EM / HID, 14443-B, Topaz, LEGIC — conditional by default
                b.emulateStatus(CardCapability.EmulateStatus.CONDITIONAL)
                 .emulateReason("Проверьте наличие поддержки в каталоге команд");
        }
    }

    private static int guessProtocolFromName(String name) {
        if (name == null) return -1;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("hf-mf-") || n.endsWith(".mfd")) return PROTO_CLASSIC;
        if (n.contains("hf-mfu-") || n.contains("mfu")) return PROTO_ULTRALIGHT;
        if (n.contains("hf-15") || n.contains("iso15693")) return PROTO_ISO15693;
        return -1;
    }
}
