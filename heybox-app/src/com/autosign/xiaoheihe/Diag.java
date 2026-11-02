package com.autosign.xiaoheihe;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 账号诊断：脱敏环境 + 两种协议下的真实返回，写进运行日志，方便一键复制。 */
final class Diag {

    private Diag() {
    }

    static String mask(String v) {
        if (v == null || v.isEmpty()) {
            return "无";
        }
        if (v.length() <= 14) {
            return "(长度" + v.length() + ")";
        }
        return v.substring(0, 10) + "…" + v.substring(v.length() - 4) + "(长度" + v.length() + ")";
    }

    static String platformOf(String tag) {
        if ("07".equals(tag)) return "旧网页登录态";
        if ("28".equals(tag)) return "网页登录态";
        if ("48".equals(tag)) return "官方App登录态";
        if ("65".equals(tag)) return "App通道(待定平台)";
        if ("88".equals(tag)) return "App通道(安卓)";
        return tag.isEmpty() ? "无法解析" : "未知标记";
    }

    /** pkey 里的时间戳就是签发时间，能区分“刚登录就被顶”和“自然过期”。 */
    static String issuedAt(String pkey) {
        try {
            String decoded = java.net.URLDecoder.decode(pkey, "UTF-8");
            while (decoded.endsWith("_")) {
                decoded = decoded.substring(0, decoded.length() - 1);
            }
            StringBuilder b64 = new StringBuilder(decoded.replace('-', '+').replace('_', '/'));
            while (b64.length() % 4 != 0) {
                b64.append('=');
            }
            byte[] raw = java.util.Base64.getDecoder().decode(b64.toString());
            String plain = new String(raw, "UTF-8");
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^(\\d{9,12})\\.").matcher(plain);
            if (m.find()) {
                long ts = Long.parseLong(m.group(1));
                return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
                        .format(new Date(ts * 1000L));
            }
        } catch (Exception ignored) {
        }
        return "未知";
    }

    static String fingerprint(String v) {
        if (v == null || v.isEmpty()) {
            return "无";
        }
        return XhhSig.md5Hex(v).substring(0, 12) + "(长度" + v.length() + ")";
    }

    private static String prop(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Object v = c.getMethod("get", String.class).invoke(null, key);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            return "";
        }
    }

    private static String appVer(Context ctx) {        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            return pi.versionName + " (" + code + ")";
        } catch (Exception e) {
            return "?";
        }
    }

    private static String netType(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) {
                return "无网络";
            }
            String t = ni.getTypeName();
            if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                return "WIFI";
            }
            return t + "/" + ni.getSubtypeName();
        } catch (Exception e) {
            return "未知";
        }
    }

    /** 运行时一行式环境+调度快照，写进每次运行日志首部。 */
    static String runtimeInfo(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("环境: ").append(appVer(ctx)).append(" · ")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append('(').append(Build.VERSION.SDK_INT).append(')');
        String emui = prop("ro.build.version.emui");
        String hw = prop("hw_sc.build.platform.version");
        if (hw.isEmpty()) {
            hw = prop("ro.build.version.harmony");
        }
        if (!emui.isEmpty() || !hw.isEmpty()) {
            sb.append(" · ").append(emui);
            if (!hw.isEmpty()) {
                sb.append("/Harmony ").append(hw);
            }
        }
        sb.append(" · 网络 ").append(netType(ctx)).append(" · 代理 ").append(proxyOf(ctx))
                .append(" · VPN ").append(vpnOn(ctx) ? "开" : "无");
        sb.append('\n');
        long next = Store.nextRun(ctx);
        sb.append("调度: 下次 ").append(next > 0
                ? new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(next))
                : "未排定");
        sb.append(" · 重试 ").append(Store.attempts(ctx)).append(" 次")
                .append(" · 精确闹钟 ").append(exactOk(ctx) ? "已授权" : "未授权")
                .append(" · 电池优化 ").append(batteryExempt(ctx) ? "已忽略" : "受限")
                .append(" · Doze ").append(dozing(ctx) ? "是" : "否")
                .append(" · 开机 ").append(uptimeText())
                .append(" · 电量 ").append(batteryText(ctx));
        sb.append('\n');
        return sb.toString();
    }

    private static String proxyOf(Context ctx) {
        try {
            String p = Settings.Global.getString(ctx.getContentResolver(), "http_proxy");
            if (p == null || p.isEmpty() || ":0".equals(p) || "null".equals(p)) {
                return "无";
            }
            return p;
        } catch (Exception e) {
            return "未知";
        }
    }

    private static boolean vpnOn(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null || Build.VERSION.SDK_INT < 23) {
                return false;
            }
            android.net.Network n = cm.getActiveNetwork();
            android.net.NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            return nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean exactOk(Context ctx) {
        try {
            android.app.AlarmManager am =
                    (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return Build.VERSION.SDK_INT < 31 || am == null || am.canScheduleExactAlarms();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean batteryExempt(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean dozing(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return Build.VERSION.SDK_INT >= 23 && pm != null && pm.isDeviceIdleMode();
        } catch (Exception e) {
            return false;
        }
    }

    private static String uptimeText() {
        long s = SystemClock.elapsedRealtime() / 1000L;
        return (s / 86400) + "d" + ((s % 86400) / 3600) + "h";
    }

    private static String batteryText(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            int pct = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            boolean saver = pm != null && pm.isPowerSaveMode();
            return (pct < 0 ? "?" : pct + "%") + (saver ? "(省电开)" : "");
        } catch (Exception e) {
            return "?";
        }
    }

    static String environment(Context ctx) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        sb.append("== 环境 ==\n");
        sb.append("时间: ").append(f.format(new Date())).append('\n');
        sb.append("App: ").append(appVer(ctx)).append('\n');
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        String emui = prop("ro.build.version.emui");
        String harmony = prop("hw_sc.build.platform.version");
        if (harmony.isEmpty()) {
            harmony = prop("ro.build.version.harmony");
        }
        StringBuilder rom = new StringBuilder();
        if (!emui.isEmpty()) rom.append(emui);
        if (!harmony.isEmpty()) {
            if (rom.length() > 0) rom.append(" / ");
            rom.append("Harmony ").append(harmony);
        }
        if (rom.length() > 0) {
            sb.append("系统: ").append(rom).append('\n');
        }
        sb.append("系统构建: ").append(Build.DISPLAY).append('\n');
        sb.append("网络: ").append(netType(ctx)).append('\n');
        sb.append("时区: ").append(java.util.TimeZone.getDefault().getID())
                .append(" 语言: ").append(Locale.getDefault().toString()).append('\n');
        return sb.toString();
    }

    private static String one(HeyboxClient c, String path) {
        long t0 = System.currentTimeMillis();
        String out;
        try {
            JSONObject r = c.getJson(path, null);
            String status = r.optString("status", "");
            String msg = r.optString("msg", "");
            if (status.isEmpty()) {
                status = "无status";
            }
            out = status + (msg.isEmpty() ? "" : " / " + msg);
        } catch (IllegalArgumentException e) {
            out = "解析失败 / " + e.getMessage();
        } catch (Exception e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            out = "异常 / " + e.getClass().getSimpleName() + " " + m;
        }
        return out + " (" + (System.currentTimeMillis() - t0) + "ms)";
    }

    static String run(Context ctx, String cookieLine, String deviceId) {
        java.util.LinkedHashMap<String, String> map = HeyboxClient.parseCookie(cookieLine);
        String pkey = map.containsKey("pkey") ? map.get("pkey") : "";
        String token = map.containsKey("x_xhh_tokenid") ? map.get("x_xhh_tokenid") : "";
        String tag = XhhSig.pkeyClientTag(pkey);
        StringBuilder sb = new StringBuilder();
        sb.append("== 账号 ==\n");
        sb.append("pkey标记: ").append(tag.isEmpty() ? "?" : tag)
                .append("（").append(platformOf(tag)).append("）\n");
        sb.append("签发: ").append(issuedAt(pkey)).append('\n');
        sb.append("uid: ").append(XhhSig.decodePkeyToUserId(pkey)).append('\n');
        sb.append("pkey: ").append(mask(pkey)).append('\n');
        sb.append("tokenid: ").append(token.isEmpty() ? "无" : mask(token)).append('\n');
        sb.append("deviceId: ").append(fingerprint(deviceId)).append('\n');

        String pkeyCookie = "pkey=" + pkey;
        String webCookie = pkeyCookie + "; x_xhh_tokenid=" + (token.isEmpty() ? "probe" : token);
        sb.append("== 测试 ==\n");
        try {
            HeyboxClient app = new HeyboxClient(pkeyCookie, true, deviceId);
            sb.append("[App参数] 拉任务列表: ").append(one(app, "/task/list_v2/")).append('\n');
            sb.append("[App参数] 签到状态: ").append(one(app, "/task/sign_v3/get_sign_state")).append('\n');
        } catch (Exception e) {
            sb.append("[App参数] 初始化失败: ").append(e.getMessage()).append('\n');
        }
        try {
            HeyboxClient web = new HeyboxClient(webCookie, true, deviceId);
            sb.append("[网页参数] 拉任务列表: ").append(one(web, "/task/list_v2/")).append('\n');
            sb.append("[网页参数] 签到状态: ").append(one(web, "/task/sign_v3/get_sign_state")).append('\n');
        } catch (Exception e) {
            sb.append("[网页参数] 初始化失败: ").append(e.getMessage()).append('\n');
        }
        sb.append("== 结论 ==\n").append(verdict(sb.toString()));
        return sb.toString();
    }

    private static String verdict(String body) {
        boolean appList = body.contains("[App参数] 拉任务列表: ok");
        boolean appState = body.contains("[App参数] 签到状态: ok");
        boolean webList = body.contains("[网页参数] 拉任务列表: ok");
        if (appState) {
            return "App 通道可用，且签到接口通过校验（可签到）";
        }
        if (appList) {
            return "App 通道可拉数据，但签到接口未通过（可能只能分享，需看上面返回）";
        }
        if (webList) {
            return "仅网页通道可用：能拉任务/分享，签到会被拒（这就是 28 类网页登录态的典型表现）";
        }
        return "两种协议都未通过登录校验：登录态已失效，或该设备/环境的登录未被服务端激活（请把上面内容整段复制反馈）";
    }
}
