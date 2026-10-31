package com.autosign.xiaoheihe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginActivity extends Activity {

    private WebView webView;
    private final Handler handler = new Handler();
    private volatile boolean finished = false;
    private TextView tipView;
    private EditText phoneEdit;
    private EditText codeEdit;
    private String deviceId = "";
    private String fakeToken = "";
    private String appToken = "";
    private CaptchaCallback pendingCaptcha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 18));

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
        header.addView(Ui.bold(this, "登录小黑盒", 19, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(Ui.chip(this, "短信验证码", Ui.ACCENT));
        root.addView(header);
        root.addView(Ui.text(this, "用绑定小黑盒的手机号接收验证码登录，登录态仅保存在本机", 12, Ui.SUB));

        LinearLayout card = Ui.card(this);
        card.addView(Ui.sectionTitle(this, "手机号登录"));

        phoneEdit = input("手机号", android.text.InputType.TYPE_CLASS_PHONE);
        LinearLayout.LayoutParams phoneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        phoneLp.topMargin = Ui.dp(this, 10);
        phoneEdit.setLayoutParams(phoneLp);
        card.addView(phoneEdit);

        LinearLayout codeRow = new LinearLayout(this);
        codeRow.setOrientation(LinearLayout.HORIZONTAL);
        codeRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams codeRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        codeRowLp.topMargin = Ui.dp(this, 10);
        codeRow.setLayoutParams(codeRowLp);
        codeEdit = input("短信验证码", android.text.InputType.TYPE_CLASS_NUMBER);
        codeRow.addView(codeEdit, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button codeBtn = actionButton("获取验证码", false);
        LinearLayout.LayoutParams codeBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        codeBtnLp.leftMargin = Ui.dp(this, 8);
        codeRow.addView(codeBtn, codeBtnLp);
        card.addView(codeRow);

        Button loginBtn = actionButton("登录", true);
        LinearLayout.LayoutParams loginLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loginLp.topMargin = Ui.dp(this, 12);
        loginBtn.setLayoutParams(loginLp);
        card.addView(loginBtn);
        root.addView(card);

        tipView = Ui.text(this, "", 12, Ui.SUB);
        tipView.setPadding(Ui.dp(this, 2), Ui.dp(this, 10), Ui.dp(this, 2), 0);
        tipView.setMinHeight(Ui.dp(this, 36));
        root.addView(tipView);

        webView = new WebView(this);
        webView.setBackgroundColor(Ui.BG);
        webView.setVisibility(View.GONE);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webLp.topMargin = Ui.dp(this, 10);
        webView.setLayoutParams(webLp);
        root.addView(webView);
        setupWebView();

        deviceId = getIntent().getStringExtra("device_id");
        if (deviceId == null) {
            deviceId = "";
        }
        fakeToken = "B" + HeyboxClient.randomDeviceId() + HeyboxClient.randomDeviceId()
                + "abcd1234efgh5678ijkl9012mnop";
        appToken = HeyboxClient.makeDeviceToken();

        codeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String phone = phoneEdit.getText().toString().trim();
                if (phone.length() != 11) {
                    tipView.setText("请输入11位手机号");
                    return;
                }
                codeBtn.setEnabled(false);
                tipView.setText("正在获取验证码…");
                sendCodeFlow(phone, codeBtn);
            }
        });
        loginBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final String phone = phoneEdit.getText().toString().trim();
                final String code = codeEdit.getText().toString().trim();
                if (phone.length() != 11 || code.isEmpty()) {
                    tipView.setText("请先填好手机号和验证码");
                    return;
                }
                loginBtn.setEnabled(false);
                tipView.setText("正在登录…");
                loginFlow(phone, code, loginBtn);
            }
        });

        setContentView(root);
    }

    private EditText input(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.FAINT);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setBackground(Ui.rounded(this, Ui.BG, 10, Ui.STROKE, 1));
        e.setPadding(Ui.dp(this, 12), Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
        return e;
    }

    private Button actionButton(String text, boolean filled) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(filled ? Ui.ACCENT_DARK : Ui.ACCENT);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        if (filled) {
            Ui.pillBg(this, b, Ui.ACCENT, 0xFF5F8FE6, 12);
        } else {
            Ui.pillBg(this, b, 0x227AA7FF, 0x337AA7FF, 12);
        }
        b.setStateListAnimator(null);
        return b;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return url != null && !url.startsWith("http://") && !url.startsWith("https://");
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                tipView.setText("验证页加载出错: " + errorCode + " " + description);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void onCap(final String ticket, final String randstr) {
                handler.post(new Runnable() {
                    public void run() {
                        hideCaptcha();
                        CaptchaCallback cb = pendingCaptcha;
                        pendingCaptcha = null;
                        if (cb != null) {
                            cb.onResult(ticket, randstr, true);
                        }
                    }
                });
            }

            @android.webkit.JavascriptInterface
            public void onCapFail() {
                handler.post(new Runnable() {
                    public void run() {
                        hideCaptcha();
                        CaptchaCallback cb = pendingCaptcha;
                        pendingCaptcha = null;
                        if (cb != null) {
                            cb.onResult("", "", false);
                        }
                    }
                });
            }
        }, "capbridge");
    }

    private void sendCodeFlow(final String phone, final Button codeBtn) {
        new Thread(new Runnable() {
            public void run() {
                final String imei = HeyboxClient.appLoginImei(deviceId);
                JSONObject resp = null;
                String err = "";
                try {
                    resp = HeyboxClient.appSendLoginCode(phone, imei, appToken, null);
                    dbg("app sendCode resp=" + resp);
                } catch (Exception e) {
                    err = e.getClass().getSimpleName() + " " + e.getMessage();
                    dbg("app sendCode error " + err);
                }
                if (resp != null && "ok".equals(resp.optString("status"))) {
                    postCodeOk(codeBtn, "验证码已发送（App 通道），请查收短信");
                    return;
                }
                if (resp != null && "show_captcha".equals(resp.optString("status"))) {
                    final String appid = resp.optString("appid", "2061805035");
                    handler.post(new Runnable() {
                        public void run() {
                            showCaptcha(appid, new CaptchaCallback() {
                                public void onResult(String ticket, String randstr, boolean ok) {
                                    if (!ok) {
                                        codeBtn.setEnabled(true);
                                        tipView.setText("滑块未通过");
                                        return;
                                    }
                                    tipView.setText("滑块已通过，正在重试…");
                                    retryAppSendCode(phone, imei, ticket, randstr, codeBtn);
                                }
                            });
                        }
                    });
                    return;
                }
                dbg("app sendCode fallback web, msg=" + (resp == null ? err : resp.optString("msg")));
                webSendCodeFlow(phone, codeBtn);
            }
        }, "xhh-app-sms").start();
    }

    private void postCodeOk(final Button btn, final String text) {
        handler.post(new Runnable() {
            public void run() {
                btn.setEnabled(true);
                tipView.setText(text);
            }
        });
    }

    private void retryAppSendCode(final String phone, final String imei, final String ticket,
            final String randstr, final Button codeBtn) {
        new Thread(new Runnable() {
            public void run() {
                java.util.LinkedHashMap<String, String> cap = new java.util.LinkedHashMap<String, String>();
                cap.put("ticket", ticket);
                cap.put("randstr", randstr);
                cap.put("captcha_type", "confirm");
                try {
                    JSONObject r2 = HeyboxClient.appSendLoginCode(phone, imei, appToken, cap);
                    dbg("app sendCode-retry resp=" + r2);
                    if ("ok".equals(r2.optString("status"))) {
                        postCodeOk(codeBtn, "验证码已发送（App 通道），请查收短信");
                        return;
                    }
                    dbg("app sendCode-retry failed, fallback web: " + r2.optString("msg"));
                } catch (Exception e) {
                    dbg("app sendCode-retry error " + e);
                }
                webSendCodeFlow(phone, codeBtn);
            }
        }, "xhh-app-sms2").start();
    }

    private void webSendCodeFlow(final String phone, final Button codeBtn) {
        dbg("sendCode phone=" + phone + " dev=" + deviceId + " token.len=" + fakeToken.length());
        new Thread(new Runnable() {
            public void run() {
                try {
                    final JSONObject resp = HeyboxClient.sendLoginCode(phone, deviceId, fakeToken);
                    dbg("sendCode resp=" + resp);
                    if ("show_captcha".equals(resp.optString("status"))) {
                        handler.post(new Runnable() {
                            public void run() {
                                showCaptcha(resp.optString("appid", "2061805035"), new CaptchaCallback() {
                                    @Override
                                    public void onResult(String ticket, String randstr, boolean ok) {
                                        if (!ok) {
                                            codeBtn.setEnabled(true);
                                            tipView.setText("滑块未通过");
                                            return;
                                        }
                                        tipView.setText("滑块已通过，正在重试…");
                                        dbg("captcha passed, ticket.len=" + (ticket == null ? -1 : ticket.length()));
                                        new Thread(new Runnable() {
                                            public void run() {
                                                java.util.LinkedHashMap<String, String> cap =
                                                        new java.util.LinkedHashMap<String, String>();
                                                cap.put("ticket", ticket);
                                                cap.put("randstr", randstr);
                                                cap.put("captcha_type", "confirm");
                                                try {
                                                    final JSONObject r2 = HeyboxClient.sendLoginCodeQ(
                                                            phone, deviceId, fakeToken, cap);
                                                    dbg("sendCode-retry resp=" + r2);
                                                    handler.post(new Runnable() {
                                                        public void run() {
                                                            codeBtn.setEnabled(true);
                                                            if ("ok".equals(r2.optString("status"))) {
                                                                tipView.setText("验证码已发送，请查收短信");
                                                            } else {
                                                                tipView.setText("发送失败: " + r2.optString("msg"));
                                                            }
                                                        }
                                                    });
                                                } catch (final Exception e) {
                                                    handler.post(new Runnable() {
                                                        public void run() {
                                                            codeBtn.setEnabled(true);
                                                            tipView.setText("发送异常: " + e.getMessage());
                                                        }
                                                    });
                                                }
                                            }
                                        }).start();
                                    }
                                });
                            }
                        });
                        return;
                    }
                    handler.post(new Runnable() {
                        public void run() {
                            codeBtn.setEnabled(true);
                            if ("ok".equals(resp.optString("status"))) {
                                tipView.setText("验证码已发送，请查收短信");
                            } else {
                                tipView.setText("发送失败: " + resp.optString("msg"));
                            }
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        public void run() {
                            codeBtn.setEnabled(true);
                            tipView.setText("发送异常: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void loginFlow(final String phone, final String code, final Button loginBtn) {
        new Thread(new Runnable() {
            public void run() {
                final String imei = HeyboxClient.appLoginImei(deviceId);
                JSONObject resp = null;
                String err = "";
                try {
                    resp = HeyboxClient.appLoginWithCode(phone, code, imei, appToken, null);
                    dbg("app login resp=" + resp);
                } catch (Exception e) {
                    err = e.getClass().getSimpleName() + " " + e.getMessage();
                    dbg("app login error " + err);
                }
                if (resp != null && "ok".equals(resp.optString("status"))) {
                    final JSONObject okResp = resp;
                    handler.post(new Runnable() {
                        public void run() {
                            handleLoginResponse(okResp, loginBtn, appToken);
                        }
                    });
                    return;
                }
                if (resp != null && "show_captcha".equals(resp.optString("status"))) {
                    final String appid = resp.optString("appid", "2061805035");
                    handler.post(new Runnable() {
                        public void run() {
                            showCaptcha(appid, new CaptchaCallback() {
                                public void onResult(String ticket, String randstr, boolean ok) {
                                    if (!ok) {
                                        loginBtn.setEnabled(true);
                                        tipView.setText("滑块未通过");
                                        return;
                                    }
                                    tipView.setText("滑块已通过，正在登录…");
                                    retryAppLogin(phone, code, imei, ticket, randstr, loginBtn);
                                }
                            });
                        }
                    });
                    return;
                }
                dbg("app login fallback web, msg=" + (resp == null ? err : resp.optString("msg")));
                webLoginFlow(phone, code, loginBtn);
            }
        }, "xhh-app-login").start();
    }

    private void retryAppLogin(final String phone, final String code, final String imei,
            final String ticket, final String randstr, final Button loginBtn) {
        new Thread(new Runnable() {
            public void run() {
                java.util.LinkedHashMap<String, String> cap = new java.util.LinkedHashMap<String, String>();
                cap.put("ticket", ticket);
                cap.put("randstr", randstr);
                cap.put("captcha_type", "confirm");
                try {
                    final JSONObject r2 = HeyboxClient.appLoginWithCode(phone, code, imei, appToken, cap);
                    dbg("app login-retry resp=" + r2);
                    if ("ok".equals(r2.optString("status"))) {
                        handler.post(new Runnable() {
                            public void run() {
                                handleLoginResponse(r2, loginBtn, appToken);
                            }
                        });
                        return;
                    }
                    dbg("app login-retry failed, fallback web: " + r2.optString("msg"));
                } catch (Exception e) {
                    dbg("app login-retry error " + e);
                }
                webLoginFlow(phone, code, loginBtn);
            }
        }, "xhh-app-login2").start();
    }

    private void webLoginFlow(final String phone, final String code, final Button loginBtn) {
        dbg("login phone=" + phone + " code.len=" + code.length());
        new Thread(new Runnable() {
            public void run() {
                try {
                    final JSONObject resp = HeyboxClient.nativeLoginWithCode(phone, code, deviceId, fakeToken);
                    dbg("login resp=" + resp);
                    if ("show_captcha".equals(resp.optString("status"))) {
                        handler.post(new Runnable() {
                            public void run() {
                                showCaptcha(resp.optString("appid", "2061805035"), new CaptchaCallback() {
                                    @Override
                                    public void onResult(String ticket, String randstr, boolean ok) {
                                        if (!ok) {
                                            loginBtn.setEnabled(true);
                                            tipView.setText("滑块未通过");
                                            return;
                                        }
                                        java.util.LinkedHashMap<String, String> cap =
                                                new java.util.LinkedHashMap<String, String>();
                                        cap.put("ticket", ticket);
                                        cap.put("randstr", randstr);
                                        cap.put("captcha_type", "confirm");
                                        new Thread(new Runnable() {
                                            public void run() {
                                                try {
                                                    final JSONObject r2 = HeyboxClient.nativeLoginWithCodeQ(
                                                            phone, code, deviceId, fakeToken, cap);
                                                    dbg("login-retry resp=" + r2);
                                                    handler.post(new Runnable() {
                                                        public void run() {
                                                            handleLoginResponse(r2, loginBtn, fakeToken);
                                                        }
                                                    });
                                                } catch (final Exception e) {
                                                    dbg("login-retry error " + e.getClass().getName() + " " + e.getMessage());
                                                    handler.post(new Runnable() {
                                                        public void run() {
                                                            loginBtn.setEnabled(true);
                                                            tipView.setText("登录异常: " + e.getClass().getSimpleName() + " " + e.getMessage());
                                                        }
                                                    });
                                                }
                                            }
                                        }).start();
                                    }
                                });
                            }
                        });
                        return;
                    }
                    handler.post(new Runnable() {
                        public void run() {
                            handleLoginResponse(resp, loginBtn, fakeToken);
                        }
                    });
                } catch (final Exception e) {
                    dbg("login outer error " + e.getClass().getName() + " " + e.getMessage());
                    handler.post(new Runnable() {
                        public void run() {
                            loginBtn.setEnabled(true);
                            tipView.setText("登录异常: " + e.getClass().getSimpleName() + " " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void handleLoginResponse(JSONObject resp, Button loginBtn, String token) {
        loginBtn.setEnabled(true);
        String status = resp.optString("status");
        if ("ok".equals(status)) {
            JSONObject result = resp.optJSONObject("result");
            String pkey = result == null ? "" : result.optString("pkey");
            String hbId = result == null ? "" : result.optString("heybox_id");
            if (pkey == null || pkey.isEmpty()) {
                tipView.setText("登录返回ok但缺pkey, 响应=" + resp);
                return;
            }
            String tag = XhhSig.pkeyClientTag(pkey);
            boolean appPkey = XhhSig.isAppPkey(pkey);
            String tokenId = token;
            if (result != null && !result.optString("x_xhh_tokenid", "").isEmpty()) {
                tokenId = result.optString("x_xhh_tokenid");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("pkey=").append(pkey).append("; ");
            sb.append("heybox_id=").append(hbId.isEmpty() ? "0" : hbId);
            if (!appPkey) {
                sb.append("; x_xhh_tokenid=").append(tokenId);
            }
            dbg("login ok pkey.tag=" + tag + " len=" + pkey.length() + " appPkey=" + appPkey);
            finished = true;
            Intent resultIntent = new Intent();
            resultIntent.putExtra("cookie", sb.toString());
            setResult(RESULT_OK, resultIntent);
            Toast.makeText(this, appPkey ? "登录成功（可签到 pkey 已获取）" : "登录成功（网页登录态，不能签到；请改用官方 App 的 pkey）",
                    Toast.LENGTH_LONG).show();
            finish();
        } else {
            tipView.setText("登录失败: " + resp.optString("msg"));
        }
    }

    interface CaptchaCallback {
        void onResult(String ticket, String randstr, boolean ok);
    }

    private void hideCaptcha() {
        webView.setVisibility(View.GONE);
    }

    private void showCaptcha(final String appid, final CaptchaCallback cb) {
        pendingCaptcha = cb;
        tipView.setText("请完成滑块验证");
        webView.setVisibility(View.VISIBLE);
        String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<script src='https://turing.captcha.qcloud.com/TCaptcha.js'></script></head>"
                + "<body style='margin:0'><div id='box' style='padding:20px'>正在加载滑块…</div>"
                + "<script>function go(){new TencentCaptcha('" + appid + "',function(s){"
                + "if(s.ret===0){capbridge.onCap(s.ticket,s.randstr);}else{capbridge.onCapFail();}}).show();}go();"
                + "</script></body></html>";
        webView.loadDataWithBaseURL("https://web.xiaoheihe.cn/", html, "text/html", "UTF-8", null);
    }

    private void dbg(String line) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) {
                return;
            }
            java.io.File f = new java.io.File(dir, "login_debug.txt");
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date())
                    + " " + line + "\n");
            w.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            hideCaptcha();
            CaptchaCallback cb = pendingCaptcha;
            pendingCaptcha = null;
            if (cb != null) {
                cb.onResult("", "", false);
            } else {
                tipView.setText("已取消滑块验证");
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        finished = true;
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
