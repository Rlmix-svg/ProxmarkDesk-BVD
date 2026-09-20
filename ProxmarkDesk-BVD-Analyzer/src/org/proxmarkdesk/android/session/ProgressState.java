// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android.session;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured progress for long-running operations (autopwn, chk, dump).
 * Published as part of SessionState; null when no operation is in progress.
 */
public final class ProgressState {

    public final int current;
    public final int total;
    public final String unit;      // "ключей", "блоков", "секторов"
    public final float rate;       // units per second, 0 if unknown
    public final long etaSeconds;  // -1 if unknown
    public final String detail;    // e.g. "Сектор 4 · Key A"

    public ProgressState(int current, int total, String unit,
                         float rate, long etaSeconds, String detail) {
        this.current = current;
        this.total = total;
        this.unit = unit;
        this.rate = rate;
        this.etaSeconds = etaSeconds;
        this.detail = detail;
    }

    /** 0.0–1.0 fraction, clamped. */
    public float fraction() {
        if (total <= 0) return 0f;
        return Math.min(1f, Math.max(0f, (float) current / total));
    }

    /** Human-readable summary line for the UI status bar. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(current).append(" / ").append(total).append(" ").append(unit);
        if (!detail.isEmpty()) sb.append(" · ").append(detail);
        if (rate > 0) sb.append(String.format(Locale.US, " · %.0f/с", rate));
        if (etaSeconds > 0) {
            long m = etaSeconds / 60, s = etaSeconds % 60;
            sb.append(String.format(Locale.US, " · ETA %d:%02d", m, s));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Parsers
    // -------------------------------------------------------------------------

    /**
     * Pattern: [+] Target sector 0 key type A -- found valid key [ FFFFFFFFFFFF ]
     * Called incrementally as stdout lines arrive.
     */
    private static final Pattern AUTOPWN_KEY =
        Pattern.compile("\\[\\+\\] Target sector (\\d+) key type ([AB]) -- found valid key \\[ [0-9a-fA-F]{12} \\]");

    /**
     * Build a ProgressState from partial autopwn output.
     * @param partialOutput accumulated stdout so far
     * @param totalSectors  total sectors for this card (16/20/32/40)
     * @param startTimeMs   System.currentTimeMillis() when the command started
     */
    public static ProgressState fromAutopwn(String partialOutput,
                                            int totalSectors,
                                            long startTimeMs) {
        if (partialOutput == null || partialOutput.isEmpty()) return null;
        Matcher m = AUTOPWN_KEY.matcher(partialOutput);
        int found = 0;
        int lastSector = 0;
        String lastKeyType = "";
        while (m.find()) {
            found++;
            lastSector = Integer.parseInt(m.group(1));
            lastKeyType = m.group(2);
        }
        if (found == 0) return null;
        int total = totalSectors * 2; // Key A + Key B per sector
        float rate = 0;
        long eta = -1;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (elapsed > 1000 && found > 0) {
            rate = found * 1000f / elapsed;
            int remaining = total - found;
            if (rate > 0) eta = (long)(remaining / rate);
        }
        String detail = "Сектор " + lastSector + " · Key " + lastKeyType;
        return new ProgressState(found, total, "ключей", rate, eta, detail);
    }

    /**
     * Pattern: [\]Sector... 0 block... 3 ( ok )
     * Block progress during hf mf dump.
     */
    private static final Pattern DUMP_BLOCK =
        Pattern.compile("Sector\\.\\.\\. (\\d+) block\\.\\.\\. (\\d+) \\( (ok|fail) \\)");

    /**
     * Build a ProgressState from partial hf mf dump output.
     * @param partialOutput  accumulated stdout so far
     * @param totalBlocks    total blocks for this card (64/80/128/256)
     * @param blocksPerSector 4 for sectors 0-31, 16 for sectors 32+
     * @param startTimeMs    System.currentTimeMillis() when the command started
     */
    public static ProgressState fromDump(String partialOutput,
                                         int totalBlocks,
                                         long startTimeMs) {
        if (partialOutput == null || partialOutput.isEmpty()) return null;
        Matcher m = DUMP_BLOCK.matcher(partialOutput);
        int lastSector = 0, lastBlock = 0, count = 0;
        boolean lastOk = true;
        while (m.find()) {
            count++;
            lastSector = Integer.parseInt(m.group(1));
            lastBlock = Integer.parseInt(m.group(2));
            lastOk = "ok".equals(m.group(3));
        }
        if (count == 0) return null;
        float rate = 0;
        long eta = -1;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (elapsed > 1000 && count > 0) {
            rate = count * 1000f / elapsed;
            int remaining = totalBlocks - count;
            if (rate > 0) eta = (long)(remaining / rate);
        }
        String detail = "Сектор " + lastSector + " блок " + lastBlock
                + (lastOk ? "" : " (ошибка)");
        return new ProgressState(count, totalBlocks, "блоков", rate, eta, detail);
    }

    /**
     * Extract the total block count for a Classic card by memory size.
     * 320→20(mini) 1024→64 2048→128 4096→256
     */
    public static int totalBlocksForSize(int memorySizeBytes) {
        switch (memorySizeBytes) {
            case 320:  return 20;
            case 2048: return 128;
            case 4096: return 256;
            default:   return 64; // 1K
        }
    }
}
