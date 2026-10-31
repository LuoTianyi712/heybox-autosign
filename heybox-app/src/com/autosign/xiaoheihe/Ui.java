package com.autosign.xiaoheihe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

final class Ui {

    static final int BG = 0xFF0F1115;
    static final int CARD = 0xFF171A21;
    static final int STROKE = 0xFF262B36;
    static final int TEXT = 0xFFF2F4F8;
    static final int SUB = 0xFF9AA3B2;
    static final int FAINT = 0xFF5C6472;
    static final int BODY = 0xFFB9C1CE;
    static final int ACCENT = 0xFF7AA7FF;
    static final int ACCENT_DARK = 0xFF0B1020;
    static final int GREEN = 0xFF3DDC97;
    static final int WARN = 0xFFFFB020;
    static final int RED = 0xFFFF6259;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != 0) {
            d.setStroke(dp(c, strokeDp), strokeColor);
        }
        return d;
    }

    static void pillBg(Context c, View v, int normal, int pressed, float radiusDp) {
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_pressed}, rounded(c, pressed, radiusDp, 0, 0));
        sd.addState(new int[]{}, rounded(c, normal, radiusDp, 0, 0));
        v.setBackground(sd);
    }

    static TextView text(Context c, String value, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    static TextView bold(Context c, String value, float sizeSp, int color) {
        TextView t = text(c, value, sizeSp, color);
        t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(c, CARD, 16, STROKE, 1));
        card.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 14);
        card.setLayoutParams(lp);
        return card;
    }

    static TextView sectionTitle(Context c, String title) {
        TextView t = text(c, title, 13, SUB);
        t.setTypeface(null, Typeface.BOLD);
        t.setLetterSpacing(0.06f);
        return t;
    }

    static View divider(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 10);
        v.setLayoutParams(lp);
        v.setBackgroundColor(0xFF222731);
        return v;
    }

    static TextView chip(Context c, String value, int color) {
        TextView t = text(c, value, 11, color);
        styleChip(c, t, color);
        return t;
    }

    static void styleChip(Context c, TextView t, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor((color & 0x00FFFFFF) | 0x22000000);
        d.setCornerRadius(dp(c, 9));
        t.setBackground(d);
        t.setTextColor(color);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
    }

    static LinearLayout settingRow(Context c, String label, View right) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 9), 0, dp(c, 9));
        row.addView(text(c, label, 14, TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(right);
        return row;
    }

    static TextView pillButton(Context c, String label, float sizeSp, int textColor, int bg, int bgPressed) {
        TextView b = text(c, label, sizeSp, textColor);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        pillBg(c, b, bg, bgPressed, 12);
        return b;
    }

    static TextView textButton(Context c, String label, int color) {
        TextView b = text(c, label, 12, color);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        b.setBackground(rounded(c, (color & 0x00FFFFFF) | 0x1A000000, 9, 0, 0));
        return b;
    }

    static void tintSwitch(Switch s) {
        try {
            s.setThumbTintList(ColorStateList.valueOf(ACCENT));
            s.setTrackTintList(ColorStateList.valueOf(0x557AA7FF));
        } catch (Exception ignored) {
        }
    }

    static View spacer(Context c, int dpValue) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpValue)));
        return v;
    }

    static LinearLayout pageRoot(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 28));
        return root;
    }

    static LinearLayout pageHeader(Context c, String title) {
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(bold(c, title, 19, TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }
}
