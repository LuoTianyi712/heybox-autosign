package com.autosign.xiaoheihe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildUi();
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
        header.addView(Ui.bold(this, "设置", 19, Ui.TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(Ui.chip(this, "v" + versionName(), Ui.SUB));
        root.addView(header);

        LinearLayout moreCard = Ui.card(this);
        moreCard.addView(Ui.sectionTitle(this, "更多"));
        moreCard.addView(Ui.divider(this));

        TextView logArrow = Ui.text(this, "›", 18, Ui.ACCENT);
        LinearLayout logRow = Ui.settingRow(this, "运行日志", logArrow);
        logRow.setClickable(true);
        logRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, LogActivity.class));
            }
        });
        moreCard.addView(logRow);

        moreCard.addView(Ui.divider(this));
        moreCard.addView(Ui.settingRow(this, "版本", Ui.text(this, "v" + versionName(), 13, Ui.SUB)));
        root.addView(moreCard);

        TextView footer = Ui.text(this, "分享任务依赖社区接口，偶发不结算是正常现象", 11, Ui.FAINT);
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
}
