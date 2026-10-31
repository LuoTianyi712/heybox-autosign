package com.autosign.xiaoheihe;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TaskList {

    private static final Pattern P_TASK = Pattern.compile("\\[([^\\]]+)\\]\\s*([^\\[]*?)(?=\\s{2,}\\[|$)");

    private TaskList() {
    }

    static boolean render(Context c, LinearLayout container, String rawText) {
        container.removeAllViews();
        boolean any = false;
        String[] lines = rawText.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("任务: ")) {
                if (renderDump(c, container, line.substring(4))) {
                    any = true;
                }
            }
        }
        return any;
    }

    private static boolean renderDump(Context c, LinearLayout container, String dump) {
        Matcher m = P_TASK.matcher(dump);
        boolean any = false;
        while (m.find()) {
            String path = m.group(1);
            String title = m.group(2).trim();
            if (title.isEmpty()) {
                continue;
            }
            any = true;
            String state = path;
            int cut = state.lastIndexOf('/');
            if (cut >= 0) {
                state = state.substring(cut + 1);
            }
            int color = Ui.FAINT;
            String stateText = state;
            if ("finish".equals(state)) {
                color = Ui.GREEN;
                stateText = "已完成";
            } else if ("waiting".equals(state)) {
                if ("open_window".equals(path) || (path.startsWith("/open_window") && !path.contains("/daily"))) {
                    stateText = "活动";
                } else {
                    color = Ui.WARN;
                    stateText = "待完成";
                }
            }
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
            View dot = new View(c);
            dot.setBackground(Ui.rounded(c, color, 4, 0, 0));
            row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(c, 8), Ui.dp(c, 8)));
            TextView t = Ui.text(c, title, 13, Ui.TEXT);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tLp.leftMargin = Ui.dp(c, 10);
            row.addView(t, tLp);
            row.addView(Ui.chip(c, stateText, color));
            container.addView(row);
        }
        return any;
    }
}
