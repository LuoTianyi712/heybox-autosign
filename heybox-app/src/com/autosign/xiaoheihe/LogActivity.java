package com.autosign.xiaoheihe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogActivity extends Activity {

    private static final Pattern P_SUMMARY = Pattern.compile("任务汇总: 完成(\\d+)个(?:，未完成(\\d+)个)?");
    private static final Pattern P_ACCOUNT = Pattern.compile("账号: (.+?) \\(ID:(\\d+)\\)");
    private static final Pattern P_LEVEL = Pattern.compile("Lv\\.(\\d+)");
    private static final Pattern P_COIN = Pattern.compile("当前H币[:：]?\\s*(\\d+)");
    private static final Pattern P_BATTERY = Pattern.compile("盒电[:：]?\\s*(\\d+)");

    private LinearLayout emptyView;
    private LinearLayout runCard;
    private TextView timeView;
    private TextView resultChip;
    private TextView accountView;
    private LinearLayout detailCard;
    private LinearLayout detailContainer;
    private LinearLayout diagCard;
    private TextView diagBody;
    private String rawText = "";
    private BroadcastReceiver resultReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (resultReceiver == null) {
            resultReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    render();
                }
            };
            IntentFilter filter = new IntentFilter(SignReceiver.ACTION_RESULT_UPDATED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(resultReceiver, filter);
            }
        }
    }

    @Override
    protected void onStop() {
        if (resultReceiver != null) {
            unregisterReceiver(resultReceiver);
            resultReceiver = null;
        }
        super.onStop();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.pageRoot(this);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹", 26, Ui.ACCENT);
        back.setTypeface(null, Typeface.BOLD);
        back.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 12), Ui.dp(this, 2));
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back);
        header.addView(Ui.bold(this, "运行日志", 19, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView copyBtn = Ui.textButton(this, "复制", Ui.ACCENT);
        copyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyLog();
            }
        });
        header.addView(copyBtn);
        root.addView(header);

        emptyView = new LinearLayout(this);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, Ui.dp(this, 90), 0, 0);
        TextView emptyTitle = Ui.bold(this, "还没有运行记录", 15, Ui.SUB);
        emptyTitle.setGravity(Gravity.CENTER);
        emptyView.addView(emptyTitle);
        TextView emptyHint = Ui.text(this, "回主页点「立即运行一次」", 12, Ui.FAINT);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(0, Ui.dp(this, 8), 0, 0);
        emptyView.addView(emptyHint);
        root.addView(emptyView);

        runCard = Ui.card(this);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        timeView = Ui.bold(this, "", 18, Ui.TEXT);
        top.addView(timeView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        resultChip = Ui.chip(this, "", Ui.GREEN);
        top.addView(resultChip);
        runCard.addView(top);
        accountView = Ui.text(this, "", 12, Ui.SUB);
        LinearLayout.LayoutParams accountLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        accountLp.topMargin = Ui.dp(this, 6);
        runCard.addView(accountView, accountLp);
        root.addView(runCard);

        diagCard = Ui.card(this);
        diagCard.addView(Ui.sectionTitle(this, "账号诊断"));
        diagBody = Ui.text(this, "", 11, Ui.BODY);
        diagBody.setTypeface(Typeface.MONOSPACE);
        diagBody.setLineSpacing(Ui.dp(this, 3), 1f);
        diagBody.setTextIsSelectable(true);
        LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dbLp.topMargin = Ui.dp(this, 6);
        diagCard.addView(diagBody, dbLp);
        root.addView(diagCard);
        detailCard = Ui.card(this);        detailCard.addView(Ui.sectionTitle(this, "运行过程"));
        detailContainer = new LinearLayout(this);
        detailContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams dcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dcLp.topMargin = Ui.dp(this, 2);
        detailContainer.setLayoutParams(dcLp);
        detailCard.addView(detailContainer);
        root.addView(detailCard);

        setContentView(scroll);
    }

    private void render() {
        rawText = Store.lastResult(this);
        String diag = Store.diagText(this);
        if (diagCard != null) {
            diagCard.setVisibility(diag.trim().isEmpty() ? View.GONE : View.VISIBLE);
            diagBody.setText(diag);
        }
        if (rawText.trim().isEmpty()) {
            runCard.setVisibility(View.GONE);
            detailCard.setVisibility(View.GONE);
            emptyView.setVisibility(diag.trim().isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        emptyView.setVisibility(View.GONE);
        runCard.setVisibility(View.VISIBLE);
        detailCard.setVisibility(View.VISIBLE);

        String lastTime = "";
        String firstLine = rawText.split("\n", 2)[0].trim();
        if (firstLine.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            lastTime = firstLine.substring(5, 16);
        }
        timeView.setText(lastTime.isEmpty() ? "本次运行" : lastTime);

        int done = -1;
        int remain = 0;
        Matcher ms = P_SUMMARY.matcher(rawText);
        if (ms.find()) {
            done = Integer.parseInt(ms.group(1));
            remain = ms.group(2) == null ? 0 : Integer.parseInt(ms.group(2));
        }
        if (done < 0) {
            resultChip.setText("—");
            Ui.styleChip(this, resultChip, Ui.SUB);
        } else if (remain == 0) {
            resultChip.setText("全部完成");
            Ui.styleChip(this, resultChip, Ui.GREEN);
        } else {
            resultChip.setText("未完成 " + remain + " 项");
            Ui.styleChip(this, resultChip, Ui.WARN);
        }

        Matcher ma = P_ACCOUNT.matcher(rawText);
        String nick = "";
        String id = "";
        if (ma.find()) {
            nick = ma.group(1);
            id = ma.group(2);
        }
        Matcher ml = P_LEVEL.matcher(rawText);
        String level = ml.find() ? ml.group(1) : "";
        Matcher mc = P_COIN.matcher(rawText);
        String coin = mc.find() ? mc.group(1) : "";
        Matcher mb = P_BATTERY.matcher(rawText);
        String battery = mb.find() ? mb.group(1) : "";
        StringBuilder meta = new StringBuilder();
        if (!nick.isEmpty()) {
            meta.append(nick);
        }
        if (!id.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append("ID ").append(id);
        }
        if (!level.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append("Lv.").append(level);
        }
        if (!coin.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append("H币 ").append(coin);
        }
        if (!battery.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append("盒电 ").append(battery);
        }
        accountView.setText(meta.toString());
        accountView.setVisibility(meta.length() == 0 ? View.GONE : View.VISIBLE);

        detailContainer.removeAllViews();
        boolean hasDetails = false;
        String[] lines = rawText.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (i == 0 && line.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                continue;
            }
            if (line.startsWith("任务: ") || line.startsWith("账号: ") || line.startsWith("任务汇总: ")
                    || line.startsWith("cookie项: ") || line.startsWith("开关: ")) {
                continue;
            }
            hasDetails = true;
            if (line.startsWith("原始:")) {
                addJsonBlock(line.substring(3).trim());
            } else {
                addDetailRow(line);
            }
        }
        if (!hasDetails) {
            addDetailRow("本次运行时所有任务均已完成，无需额外操作");
            hasDetails = true;
        }
        detailCard.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
    }

    private void addDetailRow(String line) {
        int color = Ui.BODY;
        if (line.contains("失败") || line.contains("异常") || line.contains("错误")) {
            color = Ui.RED;
        } else if (line.contains("未结算") || line.contains("未完成")) {
            color = Ui.WARN;
        } else if (line.contains("完成") || line.contains("已签到")) {
            color = Ui.GREEN;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        View dot = new View(this);
        dot.setBackground(Ui.rounded(this, color, 2, 0, 0));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Ui.dp(this, 5), Ui.dp(this, 5));
        dotLp.topMargin = Ui.dp(this, 6);
        row.addView(dot, dotLp);
        TextView t = Ui.text(this, line, 12, color);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.leftMargin = Ui.dp(this, 9);
        t.setLineSpacing(Ui.dp(this, 2), 1f);
        row.addView(t, tLp);
        detailContainer.addView(row);
    }

    private void addJsonBlock(String json) {
        TextView label = Ui.text(this, "原始数据", 10, Ui.FAINT);
        label.setLetterSpacing(0.05f);
        label.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        detailContainer.addView(label);
        TextView t = new TextView(this);
        t.setText(json);
        t.setTextSize(10);
        t.setTextColor(Ui.SUB);
        t.setTypeface(Typeface.MONOSPACE);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        t.setBackground(Ui.rounded(this, Ui.BG, 10, Ui.STROKE, 1));
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        t.setTextIsSelectable(true);
        detailContainer.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String buildCopyText() {
        StringBuilder sb = new StringBuilder();
        String diag = Store.diagText(this);
        if (!diag.trim().isEmpty()) {
            sb.append(diag.trim()).append("\n\n");
        }
        if (!rawText.trim().isEmpty()) {
            sb.append("== 上次运行 ==\n").append(rawText.trim()).append('\n');
        }
        return sb.toString();
    }

    private void copyLog() {
        try {
            String text = buildCopyText();
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("log",
                        text.isEmpty() ? "还没有运行记录" : text));
                Ui.toast(this, "日志已复制");
            }
        } catch (Exception e) {
            Ui.toast(this, "复制失败: " + e.getMessage());
        }
    }
}
