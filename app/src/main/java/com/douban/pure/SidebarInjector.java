package com.douban.pure;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SidebarInjector {
    private static final String TAG = "DoubanPure";
    private static final String SIDEBAR_ENTRY_TAG = "douban_pure_sidebar_entry";
    private static final int DOUBAN_GREEN = Color.parseColor("#42BD56");

    private SidebarInjector() {
    }

    public static void inject(View root) {
        if (root == null) return;
        Context ctx = root.getContext();
        Activity activity = findActivity(ctx);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        try {
            ViewGroup listEntries = findListEntriesView(root);
            if (listEntries == null) {
                return;
            }

            if (listEntries.findViewWithTag(SIDEBAR_ENTRY_TAG) != null) {
                return;
            }

            View entry = createSidebarEntryView(activity);
            entry.setTag(SIDEBAR_ENTRY_TAG);

            int targetIndex = -1;
            int count = listEntries.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = listEntries.getChildAt(i);
                if (containsText(child, "设置")) {
                    targetIndex = i + 1;
                    break;
                }
            }

            if (targetIndex >= 0 && targetIndex <= listEntries.getChildCount()) {
                listEntries.addView(entry, targetIndex);
            } else {
                listEntries.addView(entry);
            }

            Log.i(TAG, "Sidebar entry injected successfully");
        } catch (Throwable t) {
            Log.w(TAG, "inject failed: " + t);
        }
    }

    private static ViewGroup findListEntriesView(View view) {
        if (view == null) return null;
        String name = view.getClass().getName();
        if (name.contains("SlideMenuListEntriesView")) {
            return (ViewGroup) view;
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                ViewGroup res = findListEntriesView(vg.getChildAt(i));
                if (res != null) return res;
            }
        }
        return null;
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            if (cs != null && cs.toString().contains(text)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (containsText(vg.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private static View createSidebarEntryView(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp2px(activity, 20);
        int padV = dp2px(activity, 14);
        row.setPadding(padH, padV, padH, padV);

        // Douban App Icon
        ImageView icon = new ImageView(activity);
        try {
            Drawable doubanIcon = activity.getPackageManager().getApplicationIcon("com.douban.frodo");
            icon.setImageDrawable(doubanIcon);
        } catch (Throwable t) {
            try {
                icon.setImageResource(R.mipmap.ic_launcher);
            } catch (Throwable ignored) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }
        int iconSize = dp2px(activity, 22);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMargins(0, 0, dp2px(activity, 16), 0);
        icon.setLayoutParams(iconLp);
        row.addView(icon);

        // Text Layout
        LinearLayout textLayout = new LinearLayout(activity);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(textLp);

        TextView title = new TextView(activity);
        title.setText("DouBanPurify 净化设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(DOUBAN_GREEN);
        textLayout.addView(title);

        TextView subTitle = new TextView(activity);
        subTitle.setText("净化功能开关 · 即时生效");
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subTitle.setTextColor(Color.parseColor("#8E8E93"));
        subTitle.setPadding(0, dp2px(activity, 2), 0, 0);
        textLayout.addView(subTitle);

        row.addView(textLayout);

        row.setOnClickListener(v -> InAppSettingsDialog.show(activity));

        return row;
    }

    private static Activity findActivity(Context ctx) {
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx instanceof Activity) return (Activity) ctx;
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    private static int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
