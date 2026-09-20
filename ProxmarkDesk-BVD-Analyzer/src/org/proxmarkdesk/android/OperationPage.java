// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.proxmarkdesk.android.capability.ActionDef;
import org.proxmarkdesk.android.capability.ActionRegistry;
import org.proxmarkdesk.android.capability.CardCapability;
import org.proxmarkdesk.android.session.ProgressState;
import org.proxmarkdesk.android.session.SessionState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Context-driven card Operations screen. */
public final class OperationPage {
    private static final int CARD = Color.rgb(14, 34, 49);
    private static final int CARD_BORDER = Color.rgb(33, 74, 91);
    private static final int MUTED = Color.rgb(150, 174, 188);
    private static final int WARNING = Color.rgb(241, 183, 61);
    private static final int SUCCESS = Color.rgb(58, 211, 151);

    private final MainActivity a;
    private TextView elapsedView;

    OperationPage(MainActivity activity) { this.a = activity; }

    void show() {
        elapsedView = null;
        LinearLayout root = a.content();
        root.addView(a.text("Операции", 23));
        root.addView(a.text("Действия формируются по текущей карте и её подтверждённым возможностям.", 13));

        if (a.service == null) {
            root.addView(infoCard("Служба запускается…", "Подождите подключения ClientService."));
            return;
        }

        SessionState session = a.service.sessionState;
        if (session != null && session.isBusy()) {
            showRunning(root, session);
            return;
        }

        TagInfo tag = a.service.tagInfo;
        CardCapability cap = a.service.currentCapability == null
            ? CardCapability.UNKNOWN : a.service.currentCapability;

        if (tag == null || !tag.detected || tag.ambiguous || a.service.tagCached || cap.protocol < 0) {
            String detail = a.service.tagCached
                ? "Сохранённая карточка не считается текущей. Повторно обнаружьте карту перед выполнением действий."
                : "Сначала обнаружьте карту. Старый UID и предыдущий capability не используются автоматически.";
            root.addView(infoCard("Нет подтверждённой текущей карты", detail));
            root.addView(a.button("Перейти к карте", () -> a.page(1)));
            return;
        }

        root.addView(cardSummary(tag, cap));
        LinearLayout shortcuts = new LinearLayout(a);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        Button keys = a.button("Ключи", () -> a.page(13));
        Button sniff = a.button("Сниффер", () -> a.page(2));
        shortcuts.addView(keys, new LinearLayout.LayoutParams(0, -2, 1));
        shortcuts.addView(sniff, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(shortcuts);

        List<ActionDef> actions = ActionRegistry.forCardMenu(cap);
        if (actions.isEmpty()) {
            root.addView(infoCard("Нет готовых действий", "Для этой карты пока нет описанных логических операций. Полный PM3-каталог остаётся в Инструментах."));
            return;
        }

        Map<String, List<ActionDef>> grouped = new LinkedHashMap<>();
        for (ActionDef action : actions) {
            String category = action.category == null || action.category.isEmpty() ? "Другие операции" : action.category;
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(action);
        }

        for (Map.Entry<String, List<ActionDef>> group : grouped.entrySet()) {
            TextView heading = a.text(group.getKey(), 16);
            heading.setTypeface(Typeface.DEFAULT_BOLD);
            heading.setPadding(0, a.dp(12), 0, a.dp(4));
            root.addView(heading);
            for (ActionDef action : group.getValue()) root.addView(actionCard(action, cap, session));
        }
    }

    private void showRunning(LinearLayout root, SessionState session) {
        LinearLayout card = card();
        TextView title = a.text("Выполняется операция", 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        String current = session.currentCommand == null || session.currentCommand.isEmpty()
            ? "Команда запущена" : session.currentCommand;
        card.addView(muted(current));
        if (session.commandStartedAtMs > 0) {
            elapsedView = muted(elapsedLabel(session.commandStartedAtMs));
            card.addView(elapsedView);
        }
        ProgressState ps = session.progress;
        if (ps != null) {
            ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(ps.total > 0 ? ps.total : 100);
            bar.setProgress(ps.current);
            card.addView(bar, new LinearLayout.LayoutParams(-1, a.dp(12)));
            card.addView(muted(ps.summary()));
        }
        card.addView(muted("Команда выполняется до завершения. Для аварийного прерывания используйте «Остановить сеанс» на экране устройства."));
        root.addView(card);
        root.addView(a.text("После завершения список действий будет сформирован заново по актуальному состоянию карты.", 13));
    }

    void tick(SessionState session) {
        TextView target = elapsedView;
        if (target == null || session == null || !session.isBusy() || session.commandStartedAtMs <= 0) return;
        target.setText(elapsedLabel(session.commandStartedAtMs));
    }

    private String elapsedLabel(long startedAtMs) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        long seconds = elapsed / 1000L;
        return String.format(Locale.getDefault(), "Выполняется: %02d:%02d", seconds / 60L, seconds % 60L);
    }

    private View cardSummary(TagInfo tag, CardCapability cap) {
        LinearLayout card = card();
        TextView title = a.text(cap.subtype.isEmpty() ? TagInfo.FAMILIES[tag.family] : cap.subtype, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(SUCCESS);
        card.addView(title);
        card.addView(a.text("UID  " + (cap.uid.isEmpty() ? tag.id : cap.uid), 15));
        List<String> parts = new ArrayList<>();
        if (cap.sectors > 0) parts.add(cap.sectors + " секторов");
        if (cap.memorySizeBytes > 0) parts.add(cap.memorySizeBytes + " байт");
        if (cap.hasKeys) parts.add("ключи доступны");
        if (cap.partialAccess) parts.add("частичный доступ");
        parts.add(cap.verified ? "проверено" : "требует проверки");
        card.addView(muted(String.join(" · ", parts)));
        return card;
    }

    private View actionCard(ActionDef action, CardCapability cap, SessionState session) {
        LinearLayout card = card();
        TextView title = a.text(action.description == null || action.description.isEmpty() ? action.id : action.description, 16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title);
        card.addView(muted(action.id));
        String reason = ActionRegistry.unavailableReason(action, cap, session);
        boolean needsDictionary = tokens(action.commandTemplate).contains("dict");
        if (needsDictionary) {
            String selected = a.dictionaryDisplayName(action.id);
            card.addView(a.button(selected.isEmpty() ? "Добавить файл ключей" : "Заменить файл ключей · " + selected,
                () -> a.pickDictionary(action.id)));
            if (a.dictionaryPath(action.id).isEmpty()) reason = "Сначала добавьте файл словаря ключей";
        }
        final String finalReason = reason;
        Button run = a.button(finalReason == null ? (needsDictionary ? "Подобрать ключи из файла" : "Запустить") : "Недоступно", () -> launch(action, cap));
        run.setEnabled(finalReason == null);
        card.addView(run);
        if (reason != null) {
            TextView why = a.text(reason, 12);
            why.setTextColor(WARNING);
            card.addView(why);
        }
        if (action.notes != null && !action.notes.isEmpty()) card.addView(muted(String.join("\n", action.notes)));
        return card;
    }

    private void launch(ActionDef action, CardCapability cap) {
        Map<String, String> defaults = suggested(action, cap);
        Set<String> tokens = tokens(action.commandTemplate);
        boolean needsInput = false;
        for (String token : tokens) if (!defaults.containsKey(token) || defaults.get(token).isEmpty()) needsInput = true;
        if (!needsInput) {
            a.actions.run(action.id, defaults, 300);
            a.page(15);
            return;
        }

        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(a.dp(16), a.dp(4), a.dp(16), 0);
        Map<String, EditText> fields = new LinkedHashMap<>();
        for (String token : tokens) {
            String value = defaults.getOrDefault(token, "");
            EditText field = a.input(form, label(token), value);
            if (token.equals("blk") || token.equals("sector") || token.equals("type")) field.setInputType(InputType.TYPE_CLASS_NUMBER);
            fields.put(token, field);
        }
        EditText timeout = a.input(form, "Тайм-аут, секунд", "300");
        timeout.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(a)
            .setTitle(action.description)
            .setView(form)
            .setPositiveButton("Запустить", (d, which) -> a.guard(() -> {
                Map<String, String> params = new LinkedHashMap<>(defaults);
                for (Map.Entry<String, EditText> e : fields.entrySet()) {
                    String value = e.getValue().getText().toString().trim();
                    if (value.isEmpty()) throw new IllegalArgumentException("Заполните: " + label(e.getKey()));
                    params.put(e.getKey(), value);
                }
                int seconds = Integer.parseInt(timeout.getText().toString().trim());
                if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("Тайм-аут: 1–86400 секунд");
                a.actions.run(action.id, params, seconds);
                a.page(15);
            }))
            .setNegativeButton("Отмена", null)
            .show();
    }

    private Map<String, String> suggested(ActionDef action, CardCapability cap) {
        Map<String, String> p = new LinkedHashMap<>();
        p.putAll(action.paramDefaults);
        for (String token : tokens(action.commandTemplate)) {
            if (token.equals("uid") && !cap.uid.isEmpty()) p.put(token, cap.uid);
            else if (token.equals("size") && !p.containsKey(token)) p.put(token, classicSize(cap));
            else if (token.equals("path") && !p.containsKey(token)) p.put(token, a.dumpName(action.id.replace('.', '-')));
            else if (token.equals("dict") && !p.containsKey(token)) { String d=a.dictionaryPath(action.id); if(!d.isEmpty()) p.put(token,d); }
            else if ((token.equals("blk") || token.equals("sector")) && !p.containsKey(token)) p.put(token, "0");
        }
        return p;
    }

    private static Set<String> tokens(String template) {
        Set<String> result = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)\\}").matcher(template == null ? "" : template);
        while (m.find()) result.add(m.group(1));
        return result;
    }

    private static String classicSize(CardCapability cap) {
        if (cap.sectors == 5) return "mini";
        if (cap.sectors == 32) return "2k";
        if (cap.sectors >= 40) return "4k";
        String s = cap.subtype == null ? "" : cap.subtype.toLowerCase(Locale.ROOT);
        if (s.contains("mini")) return "mini";
        if (s.contains("2k")) return "2k";
        if (s.contains("4k")) return "4k";
        return "1k";
    }

    private static String label(String token) {
        switch (token) {
            case "uid": return "UID";
            case "size": return "Размер Classic";
            case "path": return "Файл результата";
            case "keyA": return "Key A / ключ";
            case "keyB": return "Key B";
            case "blk": return "Блок";
            case "sector": return "Сектор";
            case "type": return "Тип";
            case "dict": return "Файл словаря";
            default: return token;
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, a.dp(5), 0, a.dp(5));
        card.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(a.dp(12));
        bg.setStroke(a.dp(1), CARD_BORDER);
        card.setBackground(bg);
        return card;
    }

    private View infoCard(String title, String detail) {
        LinearLayout card = card();
        TextView heading = a.text(title, 17);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(heading);
        card.addView(muted(detail));
        return card;
    }

    private TextView muted(String value) {
        TextView text = a.text(value == null ? "" : value, 13);
        text.setTextColor(MUTED);
        return text;
    }
}
