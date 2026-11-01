package com.autosign.xiaoheihe;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

final class Alarms {

    static final long RETRY_DELAY_MS = 60L * 60 * 1000;
    static final int MAX_RETRY = 5;

    private Alarms() {
    }

    static PendingIntent pendingIntent(Context ctx) {
        Intent i = new Intent(ctx, SignReceiver.class);
        i.setAction("com.autosign.xiaoheihe.RUN_SIGN");
        return PendingIntent.getBroadcast(ctx, 1001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleNext(Context ctx, long atMillis) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = pendingIntent(ctx);
        boolean exact = true;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            exact = false;
        }
        boolean done = false;
        try {
            // 优先走“闹钟”通道：厂商 ROM（华为/EMUI）对它放行最彻底，深度休眠也能准点触发
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(atMillis, null), pi);
            done = true;
        } catch (Exception ignored) {
        }
        if (!done) {
            try {
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
                } else {
                    am.setWindow(AlarmManager.RTC_WAKEUP, atMillis, 10L * 60 * 1000, pi);
                }
                done = true;
            } catch (SecurityException e) {
                try {
                    am.setWindow(AlarmManager.RTC_WAKEUP, atMillis, 10L * 60 * 1000, pi);
                } catch (Exception ignored2) {
                }
            }
        }
        Store.setNextRun(ctx, atMillis);
    }

    static void ensure(Context ctx) {
        long now = System.currentTimeMillis();
        long next = Store.nextRun(ctx);
        if (next <= now + 60000L) {
            int attempts = Store.attempts(ctx);
            if (attempts > 0 && attempts < MAX_RETRY) {
                next = now + RETRY_DELAY_MS;
            } else {
                Store.setAttempts(ctx, 0);
                next = nextDailyAt(ctx);
            }
        }
        scheduleNext(ctx, next);
    }

    static long nextDailyAt(Context ctx) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, Store.runHour(ctx));
        c.set(Calendar.MINUTE, Store.runMin(ctx));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return c.getTimeInMillis();
    }

    static void rescheduleAfterResult(Context ctx, boolean success) {
        int attempts = Store.attempts(ctx);
        if (success) {
            Store.setAttempts(ctx, 0);
            scheduleNext(ctx, nextDailyAt(ctx));
        } else if (attempts < MAX_RETRY) {
            Store.setAttempts(ctx, attempts + 1);
            scheduleNext(ctx, System.currentTimeMillis() + RETRY_DELAY_MS);
        } else {
            Store.setAttempts(ctx, 0);
            scheduleNext(ctx, nextDailyAt(ctx));
        }
    }
}
