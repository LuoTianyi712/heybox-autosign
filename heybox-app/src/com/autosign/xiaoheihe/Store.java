package com.autosign.xiaoheihe;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Store {

    private Store() {
    }

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("heybox_sign", Context.MODE_PRIVATE);
    }

    static List<String> cookies(Context c) {
        List<String> out = new ArrayList<String>();
        String text = sp(c).getString("cookie_text", "");
        for (String line : text.split("\n")) {
            String item = line.trim();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    static String cookieText(Context c) {
        return sp(c).getString("cookie_text", "");
    }

    static void setCookieText(Context c, String text) {
        sp(c).edit().putString("cookie_text", text).apply();
    }

    static boolean allowExternal(Context c) {
        SharedPreferences p = sp(c);
        boolean v = p.getBoolean("allow_external", true);
        if (!p.contains("migrated_v2")) {
            p.edit().putBoolean("allow_external", true).putBoolean("migrated_v2", true).apply();
            v = true;
        }
        return v;
    }

    static void setAllowExternal(Context c, boolean v) {
        sp(c).edit().putBoolean("allow_external", v).apply();
    }

    static boolean sharesEnabled(Context c) {
        return sp(c).getBoolean("shares_enabled", true);
    }

    static void setSharesEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("shares_enabled", v).apply();
    }

    static int runHour(Context c) {
        return sp(c).getInt("run_hour", 0);
    }

    static int runMin(Context c) {
        return sp(c).getInt("run_min", 30);
    }

    static void setRunTime(Context c, int hour, int min) {
        sp(c).edit().putInt("run_hour", hour).putInt("run_min", min).apply();
    }

    static void saveLastResult(Context c, String text) {
        sp(c).edit().putString("last_result", text).apply();
    }

    static String lastResult(Context c) {
        return sp(c).getString("last_result", "");
    }

    static long nextRun(Context c) {
        return sp(c).getLong("next_run", 0L);
    }

    static void setNextRun(Context c, long at) {
        sp(c).edit().putLong("next_run", at).apply();
    }

    static String deviceId(Context c) {
        SharedPreferences p = sp(c);
        String id = p.getString("device_id", "");
        if (id.length() == 32 && id.matches("[0-9a-f]{32}")) {
            return id;
        }
        StringBuilder sb = new StringBuilder(32);
        java.security.SecureRandom r = new java.security.SecureRandom();
        for (int i = 0; i < 16; i++) {
            sb.append(String.format(Locale.US, "%02x", r.nextInt(256)));
        }
        id = sb.toString();
        p.edit().putString("device_id", id).apply();
        return id;
    }

    static void saveResultFile(Context c, String text) {
        try {
            java.io.File dir = c.getExternalFilesDir(null);
            if (dir != null) {
                java.io.File f = new java.io.File(dir, "last_result.txt");
                java.io.FileWriter w = new java.io.FileWriter(f, false);
                w.write(text);
                w.close();
            }
        } catch (Exception ignored) {
        }
    }

    static void setAttempts(Context c, int n) {
        sp(c).edit().putInt("attempts", n).apply();
    }

    static int attempts(Context c) {
        return sp(c).getInt("attempts", 0);
    }
}
