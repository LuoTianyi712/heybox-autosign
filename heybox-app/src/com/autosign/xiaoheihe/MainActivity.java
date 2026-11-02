package com.autosign.xiaoheihe;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private TextView heroCount;
    private TextView heroCoin;
    private TextView heroBattery;
    private TextView heroLevel;
    private TextView heroExp;
    private TextView heroLast;
    private TextView heroNext;
    private LinearLayout progressHost;
    private TextView runBtn;

    private LinearLayout accountContainer;
    private LinearLayout taskContainer;
    private TextView timeValue;
    private Switch sharesBox;
    private Switch allowExternalBox;
    private Switch batterySwitch;
    private Switch alarmSwitch;
    private boolean permSyncing = false;

    private boolean running = false;
    private boolean skipNextResume = true;
    private boolean leftForChild = false;
    private BroadcastReceiver resultReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
        requestNotifPermissionIfNeeded();
        ensureScheduled();
        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (skipNextResume) {
            skipNextResume = false;
            return;
        }
        if (!leftForChild && !running) {
            ensureScheduled();
            refreshAll();
        }
        leftForChild = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (resultReceiver == null) {
            resultReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!running) {
                        refreshAll();
                    }
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
        View dot = new View(this);
        GradientDrawable dotBg = Ui.rounded(this, Ui.ACCENT, 4, 0, 0);
        dot.setBackground(dotBg);
        header.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8)));
        TextView title = Ui.bold(this, "小黑盒自动签到", 19, Ui.TEXT);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = Ui.dp(this, 8);
        header.addView(title, titleLp);
        header.addView(Ui.text(this, "v" + versionName(), 11, Ui.FAINT));
        TextView refresh = Ui.text(this, "⟳", 19, Ui.ACCENT);
        refresh.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!running) {
                    refreshAll();
                    toast("已刷新");
                }
            }
        });
        header.addView(refresh);
        TextView gear = Ui.text(this, "⚙", 20, Ui.SUB);
        gear.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4));
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                leftForChild = true;
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        header.addView(gear);
        root.addView(header);
        root.addView(Ui.text(this, "锁屏也能跑 · 每天自动完成签到与分享任务", 12, Ui.SUB));

        LinearLayout statusCard = Ui.card(this);
        LinearLayout statusTop = new LinearLayout(this);
        statusTop.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.text(this, "今日任务", 12, Ui.SUB));
        heroCount = Ui.bold(this, "未运行", 26, Ui.TEXT);
        left.addView(heroCount);

        progressHost = new LinearLayout(this);
        progressHost.setOrientation(LinearLayout.HORIZONTAL);
        progressHost.setBackground(Ui.rounded(this, 0xFF262B36, 3, 0, 0));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        progressLp.topMargin = Ui.dp(this, 10);
        progressHost.setLayoutParams(progressLp);
        left.addView(progressHost);

        statusTop.addView(left, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.END);

        LinearLayout coinCol = new LinearLayout(this);
        coinCol.setOrientation(LinearLayout.VERTICAL);
        coinCol.setGravity(Gravity.END);
        coinCol.addView(Ui.text(this, "H币", 12, Ui.SUB));
        heroCoin = Ui.bold(this, "—", 20, Ui.ACCENT);
        coinCol.addView(heroCoin);

        LinearLayout batteryCol = new LinearLayout(this);
        batteryCol.setOrientation(LinearLayout.VERTICAL);
        batteryCol.setGravity(Gravity.END);
        batteryCol.addView(Ui.text(this, "盒电", 12, Ui.SUB));
        heroBattery = Ui.bold(this, "—", 20, Ui.TEXT);
        batteryCol.addView(heroBattery);

        right.addView(coinCol);
        LinearLayout.LayoutParams batteryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        batteryLp.leftMargin = Ui.dp(this, 16);
        right.addView(batteryCol, batteryLp);
        statusTop.addView(right);
        statusCard.addView(statusTop);

        statusCard.addView(Ui.divider(this));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        heroLevel = Ui.chip(this, "Lv.—", Ui.ACCENT);
        heroLevel.setVisibility(View.GONE);
        meta.addView(heroLevel);
        heroExp = Ui.text(this, "经验 —", 12, Ui.SUB);
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        expLp.leftMargin = Ui.dp(this, 8);
        meta.addView(heroExp, expLp);
        meta.addView(new View(this), new LinearLayout.LayoutParams(
                0, 1, 1f));
        heroLast = Ui.text(this, "上次: 未运行", 12, Ui.SUB);
        meta.addView(heroLast);
        statusCard.addView(meta);

        LinearLayout nextRow = new LinearLayout(this);
        nextRow.setOrientation(LinearLayout.HORIZONTAL);
        nextRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nextLp.topMargin = Ui.dp(this, 6);
        nextRow.setLayoutParams(nextLp);
        heroNext = Ui.text(this, "下次: —", 11, Ui.FAINT);
        nextRow.addView(heroNext);
        statusCard.addView(nextRow);
        root.addView(statusCard);

        runBtn = Ui.pillButton(this, "立即运行一次", 15, Ui.ACCENT_DARK, Ui.ACCENT, 0xFF5F8FE6);
        LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        runLp.topMargin = Ui.dp(this, 14);
        runBtn.setLayoutParams(runLp);
        runBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (running) {
                    return;
                }
                if (!hasCookie()) {
                    toast("请先登录或粘贴 Cookie");
                    leftForChild = true;
                    startActivity(new Intent(MainActivity.this, AccountActivity.class));
                    return;
                }
                runTest();
            }
        });
        root.addView(runBtn);

        LinearLayout accountCard = Ui.card(this);
        LinearLayout accountHead = new LinearLayout(this);
        accountHead.setOrientation(LinearLayout.HORIZONTAL);
        accountHead.setGravity(Gravity.CENTER_VERTICAL);
        accountHead.addView(Ui.sectionTitle(this, "账号"), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView manage = Ui.textButton(this, "管理 ›", Ui.ACCENT);
        manage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                leftForChild = true;
                startActivity(new Intent(MainActivity.this, AccountActivity.class));
            }
        });
        accountHead.addView(manage);
        accountCard.addView(accountHead);
        accountContainer = new LinearLayout(this);
        accountContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acLp.topMargin = Ui.dp(this, 2);
        accountContainer.setLayoutParams(acLp);
        accountCard.addView(accountContainer);
        root.addView(accountCard);

        LinearLayout taskCard = Ui.card(this);
        taskCard.addView(Ui.sectionTitle(this, "任务清单"));
        taskContainer = new LinearLayout(this);
        taskContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tcLp.topMargin = Ui.dp(this, 4);
        taskContainer.setLayoutParams(tcLp);
        taskCard.addView(taskContainer);
        root.addView(taskCard);

        LinearLayout runCard = Ui.card(this);
        runCard.addView(Ui.sectionTitle(this, "执行"));
        runCard.addView(Ui.divider(this));

        timeValue = Ui.text(this, "", 13, Ui.ACCENT);
        LinearLayout timeRow = Ui.settingRow(this, "每天执行时间", timeValue);
        timeRow.setClickable(true);
        timeRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTimePicker();
            }
        });
        runCard.addView(timeRow);

        runCard.addView(Ui.divider(this));
        sharesBox = new Switch(this);
        Ui.tintSwitch(sharesBox);
        sharesBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                Store.setSharesEnabled(MainActivity.this, isChecked);
            }
        });
        runCard.addView(Ui.settingRow(this, "执行分享任务", sharesBox));

        runCard.addView(Ui.divider(this));
        allowExternalBox = new Switch(this);
        Ui.tintSwitch(allowExternalBox);
        allowExternalBox.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                Store.setAllowExternal(MainActivity.this, isChecked);
            }
        });
        runCard.addView(Ui.settingRow(this, "社区签名服务器（签到需要）", allowExternalBox));
        root.addView(runCard);

        LinearLayout permCard = Ui.card(this);
        permCard.addView(Ui.sectionTitle(this, "权限"));
        permCard.addView(Ui.divider(this));

        batterySwitch = new Switch(this);
        Ui.tintSwitch(batterySwitch);
        batterySwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (permSyncing) {
                    return;
                }
                if (isChecked) {
                    requestBatteryExemption();
                } else {
                    openBatteryOptimizationSettings();
                }
            }
        });
        LinearLayout batteryRow = Ui.settingRow(this, "电池优化豁免", batterySwitch);
        batteryRow.setClickable(true);
        batteryRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (batterySwitch.isChecked()) {
                    openBatteryOptimizationSettings();
                } else {
                    requestBatteryExemption();
                }
            }
        });
        permCard.addView(batteryRow);

        permCard.addView(Ui.divider(this));
        alarmSwitch = new Switch(this);
        Ui.tintSwitch(alarmSwitch);
        alarmSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (permSyncing) {
                    return;
                }
                if (isChecked) {
                    requestExactAlarm();
                } else {
                    openExactAlarmSettings();
                }
            }
        });
        LinearLayout alarmRow = Ui.settingRow(this, "精确闹钟权限", alarmSwitch);
        alarmRow.setClickable(true);
        alarmRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (alarmSwitch.isChecked()) {
                    openExactAlarmSettings();
                } else {
                    requestExactAlarm();
                }
            }
        });
        permCard.addView(alarmRow);
        root.addView(permCard);

        TextView footer = Ui.text(this, "遇到问题：右上角设置 → 运行日志，可一键复制", 11, Ui.FAINT);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = Ui.dp(this, 18);
        footer.setLayoutParams(footerLp);
        root.addView(footer);

        setContentView(scroll);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private boolean hasCookie() {
        return !Store.cookieText(this).trim().isEmpty();
    }

    private void setRunning(boolean value) {
        running = value;
        runBtn.setEnabled(!value);
        runBtn.setAlpha(value ? 0.5f : 1f);
        runBtn.setText(value ? "正在运行…" : "立即运行一次");
    }

    private void runTest() {
        setRunning(true);
        final boolean sharesEnabled = Store.sharesEnabled(this);
        final boolean allowExternal = Store.allowExternal(this);
        final List<String> cookies = splitLines(Store.cookieText(this));
        new Thread(new Runnable() {
            public void run() {
                final SignWorker.RunResult r = SignWorker.runAll(cookies, allowExternal, sharesEnabled,
                        Store.deviceId(MainActivity.this));
                final String text = nowStr() + " 手动\n" + Diag.runtimeInfo(MainActivity.this)
                        + SignWorker.join(r.lines, "\n");
                runOnUiThread(new Runnable() {
                    public void run() {
                        Store.saveLastResult(MainActivity.this, text);
                        Store.saveResultFile(MainActivity.this, text);
                        Alarms.rescheduleAfterResult(MainActivity.this, r.allOk);
                        setRunning(false);
                        refreshAll();
                        Toast.makeText(MainActivity.this,
                                r.allOk ? "全部完成，详情见运行日志" : "有未完成项，详情见运行日志",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "xhh-test").start();
    }

    private List<String> splitLines(String text) {
        List<String> out = new java.util.ArrayList<String>();
        for (String line : text.split("\n")) {
            String item = line.trim();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    private static final Pattern P_SUMMARY = Pattern.compile("任务汇总: 完成(\\d+)个(?:，未完成(\\d+)个)?");
    private static final Pattern P_COIN = Pattern.compile("当前H币[:：]?\\s*(\\d+)");
    private static final Pattern P_BATTERY = Pattern.compile("盒电[:：]?\\s*(\\d+)");
    private static final Pattern P_LEVEL = Pattern.compile("Lv\\.(\\d+)");
    private static final Pattern P_EXP = Pattern.compile("经验\\s*(\\d+)/(\\d+)");

    private void refreshHero() {
        String last = Store.lastResult(this);
        int done = -1;
        int remain = 0;
        String coin = "";
        String battery = "";
        String level = "";
        String exp = "";
        String maxExp = "";
        String lastTime = "";
        if (!last.isEmpty()) {
            String firstLine = last.split("\n", 2)[0].trim();
            if (firstLine.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                lastTime = firstLine.substring(5, 16);
            }
            Matcher ms = P_SUMMARY.matcher(last);
            if (ms.find()) {
                done = Integer.parseInt(ms.group(1));
                remain = ms.group(2) == null ? 0 : Integer.parseInt(ms.group(2));
            }
            Matcher mc = P_COIN.matcher(last);
            if (mc.find()) {
                coin = mc.group(1);
            }
            Matcher mb = P_BATTERY.matcher(last);
            if (mb.find()) {
                battery = mb.group(1);
            }
            Matcher ml = P_LEVEL.matcher(last);
            if (ml.find()) {
                level = ml.group(1);
            }
            Matcher me = P_EXP.matcher(last);
            if (me.find()) {
                exp = me.group(1);
                maxExp = me.group(2);
            }
        }
        if (done < 0) {
            heroCount.setText("—");
            heroCount.setTextColor(Ui.TEXT);
        } else {
            int total = done + remain;
            heroCount.setText(done + "/" + total + " 完成");
            heroCount.setTextColor(done == total ? Ui.GREEN : Ui.TEXT);
        }
        heroCoin.setText(coin.isEmpty() ? "—" : coin);
        heroBattery.setText(battery.isEmpty() ? "—" : battery);
        heroLast.setText("上次: " + (lastTime.isEmpty() ? "未运行" : lastTime));
        if (level.isEmpty()) {
            heroLevel.setVisibility(View.GONE);
        } else {
            heroLevel.setVisibility(View.VISIBLE);
            heroLevel.setText("Lv." + level);
            Ui.styleChip(this, heroLevel, Ui.ACCENT);
        }
        heroExp.setText(exp.isEmpty() ? "经验 —" : "经验 " + exp + "/" + maxExp);

        progressHost.removeAllViews();
        if (done >= 0) {
            int total = Math.max(done + remain, 1);
            if (done > 0) {
                View fill = new View(this);
                fill.setBackground(Ui.rounded(this, done == total ? Ui.GREEN : Ui.ACCENT, 3, 0, 0));
                progressHost.addView(fill, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, done));
            }
            if (remain > 0) {
                progressHost.addView(new View(this), new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, remain));
            }
        }
    }

    private void refreshStatus() {
        long next = Store.nextRun(this);
        StringBuilder sb = new StringBuilder();
        if (next > 0) {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.CHINA);
            sb.append("下次自动执行: ").append(f.format(new Date(next)));
            int attempts = Store.attempts(this);
            if (attempts > 0) {
                sb.append("（第").append(attempts).append("次重试）");
            }
        } else {
            sb.append("下次自动执行: 未排定");
        }
        heroNext.setText(sb.toString());
    }

    private void refreshAccounts() {
        accountContainer.removeAllViews();
        List<Accounts.Row> rows = Accounts.read(this);
        if (rows.isEmpty()) {
            TextView empty = Ui.text(this, "未登录，点「管理」登录或粘贴 Cookie", 12, Ui.FAINT);
            empty.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
            accountContainer.addView(empty);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            final Accounts.Row r = rows.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            TextView name = Ui.bold(this, r.hasNick() ? r.nick : "账号 " + r.id, 13, Ui.TEXT);
            row.addView(name, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.text(this, "ID " + r.id, 11, Ui.FAINT));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    leftForChild = true;
                    startActivity(new Intent(MainActivity.this, AccountActivity.class));
                }
            });
            accountContainer.addView(row);
        }
    }

    private void refreshTasks() {
        String last = Store.lastResult(this);
        if (!TaskList.render(this, taskContainer, last)) {
            TextView empty = Ui.text(this, "运行一次后显示今日任务", 12, Ui.FAINT);
            empty.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
            taskContainer.addView(empty);
        }
    }

    private void refreshSettings() {
        timeValue.setText(String.format(Locale.CHINA, "%02d:%02d ›",
                Store.runHour(this), Store.runMin(this)));
        sharesBox.setChecked(Store.sharesEnabled(this));
        allowExternalBox.setChecked(Store.allowExternal(this));
    }

    private void refreshPermissions() {
        boolean exempt = false;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            exempt = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception ignored) {
        }
        boolean alarmOk = exactAlarmAllowed();
        permSyncing = true;
        batterySwitch.setChecked(exempt);
        alarmSwitch.setChecked(alarmOk);
        boolean canToggleAlarm = Build.VERSION.SDK_INT >= 31;
        alarmSwitch.setEnabled(canToggleAlarm);
        alarmSwitch.setAlpha(canToggleAlarm ? 1f : 0.45f);
        permSyncing = false;
    }

    private void refreshAll() {
        if (running) {
            return;
        }
        refreshHero();
        refreshStatus();
        refreshAccounts();
        refreshTasks();
        refreshSettings();
        refreshPermissions();
    }

    private void ensureScheduled() {
        Alarms.ensure(this);
        Store.appendEvent(this, "alarm_log",
                new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                        .format(new java.util.Date()) + " 打开App · 网络 "
                        + Diag.networkShort(this) + " · 电量 " + Diag.batteryShort(this));
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void showTimePicker() {
        new android.app.TimePickerDialog(this,
                new android.app.TimePickerDialog.OnTimeSetListener() {
                    public void onTimeSet(android.widget.TimePicker view, int hourOfDay, int minute) {
                        Store.setRunTime(MainActivity.this, hourOfDay, minute);
                        Alarms.scheduleNext(MainActivity.this,
                                Alarms.nextDailyAt(MainActivity.this));
                        refreshStatus();
                        refreshSettings();
                        toast("已改为每天 "
                                + String.format(Locale.CHINA, "%02d:%02d", hourOfDay, minute) + " 自动执行");
                    }
                }, Store.runHour(this), Store.runMin(this), true).show();
    }

    private void requestBatteryExemption() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } else {
                toast("已经豁免电池优化");
            }
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openBatteryOptimizationSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private boolean exactAlarmAllowed() {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        try {
            android.app.AppOpsManager ops =
                    (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            if (ops != null) {
                int mode = ops.checkOpNoThrow("android:schedule_exact_alarm",
                        android.os.Process.myUid(), getPackageName());

                if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
                    return true;
                }
                if (mode != android.app.AppOpsManager.MODE_DEFAULT) {
                    return false;
                }
            }
        } catch (Exception e) {

        }
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            boolean can = am != null && am.canScheduleExactAlarms();

            return can;
        } catch (Exception e) {
            return false;
        }
    }

    private void requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
                if (!exactAlarmAllowed()) {
                    startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } else {
                    toast("已拥有精确闹钟权限");
                }
            } catch (Exception e) {
                openAppDetails();
            }
        } else {
            toast("安卓 12 以下无需此权限");
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                openAppDetails();
            }
        } else {
            toast("安卓 12 以下无需此权限");
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
        }
    }

    private String nowStr() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        return f.format(new Date());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
