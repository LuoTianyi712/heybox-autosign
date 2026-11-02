package com.autosign.xiaoheihe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class SignReceiver extends BroadcastReceiver {

    public static final String ACTION_RESULT_UPDATED = "com.autosign.xiaoheihe.RESULT_UPDATED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"com.autosign.xiaoheihe.RUN_SIGN".equals(intent.getAction())) {
            return;
        }
        final Context app = context.getApplicationContext();
        final PowerManager.WakeLock wl = ((PowerManager) app.getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "autosign.xhh:sign");
        wl.acquire(120000L);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean sharesEnabled = Store.sharesEnabled(app);
                    long expected = Store.nextRun(app);
                    long now = System.currentTimeMillis();
                    SignWorker.RunResult r = SignWorker.runAll(
                            Store.cookies(app),
                            Store.allowExternal(app),
                            sharesEnabled,
                            Store.deviceId(app));
                    String text = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                            .format(new java.util.Date()) + " " + triggerText(expected, now) + "\n"
                            + Diag.runtimeInfo(app)
                            + SignWorker.join(r.lines, "\n");
                    Store.saveLastResult(app, text);
                    Store.saveResultFile(app, text);
                    Notif.post(app, r.allOk, text);
                    Intent update = new Intent(ACTION_RESULT_UPDATED);
                    update.setPackage(app.getPackageName());
                    app.sendBroadcast(update);
                    Alarms.rescheduleAfterResult(app, r.allOk);
                } catch (Throwable t) {
                    Alarms.rescheduleAfterResult(app, false);
                } finally {
                    try {
                        wl.release();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "xhh-sign").start();
    }

    /** 记录本次是准点还是被系统推迟，便于判断厂商休眠策略。 */
    private static String triggerText(long expected, long now) {
        if (expected <= 0) {
            return "闹钟(未记录排定)";
        }
        long diff = now - expected;
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA);
        String at = f.format(new java.util.Date(expected));
        if (diff < 60000L) {
            return "闹钟(" + at + " 准点)";
        }
        long min = diff / 60000L;
        return "闹钟(排定 " + at + "，延迟 " + (min >= 60
                ? (min / 60) + "h" + (min % 60) + "m" : min + "m") + ")";
    }
}
