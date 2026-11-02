package com.autosign.xiaoheihe;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountActivity extends Activity {

    private EditText cookieBox;
    private TextView parsedView;
    private LinearLayout accountContainer;
    private TextView checkBtn;
    private final Map<String, String> checkState = new HashMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
        cookieBox.setText(Store.cookieText(this));
        refreshAccounts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccounts();
    }

    private BroadcastReceiver resultReceiver;

    @Override
    protected void onStart() {
        super.onStart();
        if (resultReceiver == null) {
            resultReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    refreshAccounts();
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
        header.addView(Ui.bold(this, "账号", 19, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(Ui.chip(this, "仅保存在本机", Ui.SUB));
        root.addView(header);

        LinearLayout accountCard = Ui.card(this);
        LinearLayout accountHead = new LinearLayout(this);
        accountHead.setOrientation(LinearLayout.HORIZONTAL);
        accountHead.setGravity(Gravity.CENTER_VERTICAL);
        accountHead.addView(Ui.sectionTitle(this, "当前账号"), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        checkBtn = Ui.textButton(this, "检查", Ui.ACCENT);
        checkBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                checkAll();
            }
        });
        accountHead.addView(checkBtn);
        accountCard.addView(accountHead);
        accountContainer = new LinearLayout(this);
        accountContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        acLp.topMargin = Ui.dp(this, 2);
        accountContainer.setLayoutParams(acLp);
        accountCard.addView(accountContainer);
        root.addView(accountCard);

        LinearLayout cookieCard = Ui.card(this);
        cookieCard.addView(Ui.sectionTitle(this, "登录态 Cookie"));
        TextView hint = Ui.text(this,
                "签到必须使用官方 App 的 pkey（App 登录态）；网页登录 Cookie 只能做拉取和分享。"
                        + "可直接粘贴 pkey=... 一行，多个账号一行一个。",
                11, Ui.FAINT);
        hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        cookieCard.addView(hint);

        cookieBox = new EditText(this);
        cookieBox.setMinLines(3);
        cookieBox.setMaxLines(8);
        cookieBox.setGravity(Gravity.TOP);
        cookieBox.setTextSize(11);
        cookieBox.setTextColor(0xFFE6E9F0);
        cookieBox.setHintTextColor(Ui.FAINT);
        cookieBox.setHint("pkey=...; heybox_id=...; x_xhh_tokenid=...");
        cookieBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        cookieBox.setBackground(Ui.rounded(this, Ui.BG, 10, Ui.STROKE, 1));
        cookieBox.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        cookieCard.addView(cookieBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cookieBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                parsedView.setText(describeCookies(s.toString()));
                refreshAccounts();
            }
        });

        parsedView = Ui.text(this, "", 11, Ui.SUB);
        parsedView.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), 0);
        cookieCard.addView(parsedView);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView loginBtn = Ui.pillButton(this, "手机验证码登录", 13, Ui.ACCENT_DARK, Ui.ACCENT, 0xFF5F8FE6);
        TextView saveBtn = Ui.pillButton(this, "保存 Cookie", 13, Ui.TEXT, 0x1AFFFFFF, 0x2AFFFFFF);
        LinearLayout.LayoutParams loginLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f);
        loginLp.topMargin = Ui.dp(this, 10);
        loginLp.rightMargin = Ui.dp(this, 4);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f);
        saveLp.topMargin = Ui.dp(this, 10);
        saveLp.leftMargin = Ui.dp(this, 4);
        buttons.addView(loginBtn, loginLp);
        buttons.addView(saveBtn, saveLp);
        cookieCard.addView(buttons);
        root.addView(cookieCard);

        TextView footer = Ui.text(this, "Cookie 等同于账号登录态，请勿分享给他人", 11, Ui.FAINT);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = Ui.dp(this, 18);
        footer.setLayoutParams(footerLp);
        root.addView(footer);

        loginBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(AccountActivity.this, LoginActivity.class);
                i.putExtra("device_id", Store.deviceId(AccountActivity.this));
                startActivityForResult(i, 7);
            }
        });
        saveBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (save()) {
                    Ui.toast(AccountActivity.this, "已保存");
                    finish();
                }
            }
        });

        setContentView(scroll);
    }

    private void refreshAccounts() {
        if (accountContainer == null) {
            return;
        }
        accountContainer.removeAllViews();
        String text = cookieBox == null ? Store.cookieText(this) : cookieBox.getText().toString();
        List<Accounts.Row> rows = Accounts.readText(this, text);
        if (rows.isEmpty()) {
            TextView empty = Ui.text(this, "未登录，点下方「手机验证码登录」", 12, Ui.FAINT);
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

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(Ui.bold(this, r.hasNick() ? r.nick : "账号 " + r.id, 14, Ui.TEXT));
            info.addView(Ui.text(this, "ID " + r.id, 11, Ui.FAINT));
            row.addView(info, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            String state = checkState.get(r.id);
            if (state != null) {
                int color = Ui.SUB;
                if ("有效".equals(state)) {
                    color = Ui.GREEN;
                } else if ("已失效".equals(state) || "无效".equals(state)) {
                    color = Ui.RED;
                } else if ("接口异常".equals(state) || "签到受限".equals(state)) {
                    color = Ui.WARN;
                }
                TextView badge = Ui.chip(this, state, color);
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                badgeLp.rightMargin = Ui.dp(this, 8);
                row.addView(badge, badgeLp);
            }

            TextView diag = Ui.textButton(this, "诊断", Ui.ACCENT);
            LinearLayout.LayoutParams diagLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            diagLp.rightMargin = Ui.dp(this, 8);
            diag.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    runDiag(r);
                }
            });
            row.addView(diag, diagLp);

            TextView del = Ui.textButton(this, "删除", Ui.RED);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    confirmDelete(r);
                }
            });
            row.addView(del);
            accountContainer.addView(row);
        }
    }

    private void runDiag(final Accounts.Row r) {
        String line = "";
        for (String item : splitCookies(cookieBox.getText().toString())) {
            if (r.id.equals(Accounts.cookieId(item))) {
                line = item;
                break;
            }
        }
        if (line.isEmpty()) {
            Ui.toast(this, "找不到该账号的登录态");
            return;
        }
        final String cookie = line;
        final String deviceId = Store.deviceId(this);
        Ui.toast(this, "正在诊断…");
        new Thread(new Runnable() {
            public void run() {
                String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                        java.util.Locale.CHINA).format(new java.util.Date());
                String text = stamp + " 诊断\n" + Diag.environment(AccountActivity.this)
                        + Diag.run(AccountActivity.this, cookie, deviceId);
                Store.saveDiag(AccountActivity.this, text);
                Store.saveDiagFile(AccountActivity.this, text);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Ui.toast(AccountActivity.this, "诊断结果已写入运行日志");
                        startActivity(new Intent(AccountActivity.this, LogActivity.class));
                    }
                });
            }
        }, "xhh-diag").start();
    }

    private void checkAll() {
        final List<String> lines = splitCookies(cookieBox.getText().toString());
        if (lines.isEmpty()) {
            Ui.toast(this, "还没有可检查的账号");
            return;
        }
        checkBtn.setEnabled(false);
        checkBtn.setAlpha(0.5f);
        checkState.clear();
        refreshAccounts();
        new Thread(new Runnable() {
            public void run() {
                int valid = 0;
                int expired = 0;
                int broken = 0;
                for (int i = 0; i < lines.size(); i++) {
                    final String line = lines.get(i);
                    final String id = Accounts.cookieId(line);
                    if (id.isEmpty()) {
                        continue;
                    }
                    checkState.put(id, "检查中");
                    postRefresh();
                    String state;
                    try {
                        HeyboxClient client = new HeyboxClient(line, Store.allowExternal(AccountActivity.this));
                        SignWorker.fetchSnapshot(client);
                        state = "有效";
                        valid++;
                        state = probeApp(client, state);
                    } catch (IllegalArgumentException e) {
                        state = "无效";
                        expired++;
                    } catch (Exception e) {
                        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                        if (msg.contains("登录") || msg.contains("login")) {
                            state = "已失效";
                            expired++;
                        } else {
                            state = "接口异常";
                            broken++;
                        }
                    }
                    checkState.put(id, state);
                    postRefresh();
                }
                final int v = valid;
                final int x = expired;
                final int b = broken;
                runOnUiThread(new Runnable() {
                    public void run() {
                        checkBtn.setEnabled(true);
                        checkBtn.setAlpha(1f);
                        refreshAccounts();
                        StringBuilder sb = new StringBuilder("检查完成：有效 ").append(v)
                                .append("，失效 ").append(x);
                        if (b > 0) {
                            sb.append("，接口异常 ").append(b);
                        }
                        Ui.toast(AccountActivity.this, sb.toString());
                    }
                });
            }
        }, "xhh-check").start();
    }

    private void postRefresh() {
        runOnUiThread(new Runnable() {
            public void run() {
                refreshAccounts();
            }
        });
    }

    private String probeApp(HeyboxClient client, String webState) {
        if (!client.allowExternal) {
            logProbe("disabled");
            return "签到受限";
        }
        try {
            org.json.JSONObject app = client.getAppJson("/task/list_v2/");
            String status = app.optString("status");
            logProbe("status=" + status + " msg=" + app.optString("msg"));
            return "ok".equals(status) ? webState : "签到受限";
        } catch (Exception e) {
            logProbe("ex=" + e);
            return "签到受限";
        }
    }

    private void logProbe(String line) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) {
                return;
            }
            java.io.FileWriter w = new java.io.FileWriter(new java.io.File(dir, "app_probe.txt"), true);
            w.write(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                    .format(new java.util.Date()) + " " + line + "\n");
            w.close();
        } catch (Exception ignored) {
        }
    }

    private void confirmDelete(final Accounts.Row r) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(this, Ui.CARD, 16, Ui.STROKE, 1));
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 16));

        box.addView(Ui.bold(this, "删除账号", 16, Ui.TEXT));
        String label = r.hasNick() ? r.nick : ("账号 " + r.id);
        TextView msg = Ui.text(this,
                "确定删除「" + label + "」的登录态？删除后该账号将不再自动签到。", 13, Ui.SUB);
        msg.setLineSpacing(Ui.dp(this, 3), 1f);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = Ui.dp(this, 10);
        box.addView(msg, msgLp);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        LinearLayout.LayoutParams btnsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnsLp.topMargin = Ui.dp(this, 18);
        btns.setLayoutParams(btnsLp);

        TextView cancel = Ui.pillButton(this, "取消", 13, Ui.TEXT, 0x1AFFFFFF, 0x2AFFFFFF);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        TextView del = Ui.pillButton(this, "删除", 13, Ui.RED, 0x26FF6259, 0x44FF6259);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        delLp.leftMargin = Ui.dp(this, 10);
        btns.addView(cancel);
        btns.addView(del, delLp);
        box.addView(btns);

        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
                deleteAccount(r);
            }
        });

        dialog.setContentView(box);
        dialog.show();

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            w.setLayout((int) (dm.widthPixels * 0.86), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void deleteAccount(Accounts.Row r) {
        checkState.remove(r.id);
        StringBuilder sb = new StringBuilder();
        for (String line : splitCookies(cookieBox.getText().toString())) {
            if (r.id.equals(Accounts.cookieId(line))) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        String remaining = sb.toString();
        if (remaining.trim().isEmpty()) {
            Store.setCookieText(this, "");
            cookieBox.setText("");
            Ui.toast(this, "已删除最后一个账号");
        } else {
            cookieBox.setText(remaining);
            if (!save()) {
                return;
            }
            Ui.toast(this, "已删除");
        }
        refreshAccounts();
    }

    private boolean save() {
        List<String> lines = splitCookies(cookieBox.getText().toString());
        if (lines.isEmpty()) {
            Ui.toast(this, "请先登录或粘贴 Cookie");
            return false;
        }
        for (String line : lines) {
            try {
                new HeyboxClient(line, Store.allowExternal(AccountActivity.this));
            } catch (IllegalArgumentException e) {
                Ui.toast(this, "Cookie 无效: " + e.getMessage());
                return false;
            }
        }
        Store.setCookieText(this, cookieBox.getText().toString());
        Alarms.ensure(this);
        return true;
    }

    private List<String> splitCookies(String text) {
        List<String> out = new ArrayList<String>();
        for (String line : text.split("\n")) {
            String item = line.trim();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    private String describeCookies(String text) {
        List<String> lines = splitCookies(text);
        if (lines.isEmpty()) {
            return "";
        }
        List<String> ids = new ArrayList<String>();
        List<String> errors = new ArrayList<String>();
        for (String line : lines) {
            try {
                HeyboxClient c = new HeyboxClient(line, Store.allowExternal(AccountActivity.this));
                ids.add(c.heyboxId);
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!ids.isEmpty()) {
            sb.append("已识别 ").append(ids.size()).append(" 个账号: ")
                    .append(SignWorker.join(ids, " · "));
        }
        if (!errors.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("　");
            }
            sb.append("⚠ ").append(errors.get(0));
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 7 && resultCode == RESULT_OK && data != null) {
            String cookie = data.getStringExtra("cookie");
            if (cookie == null || cookie.isEmpty()) {
                return;
            }
            String newId = Accounts.cookieId(cookie);
            StringBuilder sb = new StringBuilder();
            for (String line : splitCookies(cookieBox.getText().toString())) {
                String id = Accounts.cookieId(line);
                if (id.isEmpty() || id.equals(newId)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(line);
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(cookie);
            cookieBox.setText(sb.toString());
            if (save()) {
                Ui.toast(this, "登录成功，已保存");
                finish();
            }
        }
    }
}
