package com.douban.pure;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class InAppSettingsDialog {
    private static final int DOUBAN_GREEN = Color.parseColor("#42BD56");
    private static final int DOUBAN_GREEN_LIGHT = Color.parseColor("#A3E6B1");
    private static final int DOUBAN_GREEN_BADGE_BG = Color.parseColor("#E8F8EC");

    private InAppSettingsDialog() {
    }

    public static void show(Activity activity) {
        show(activity, null);
    }

    public static void show(Activity activity, Runnable onDismissListener) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        buildDialog(activity, onDismissListener).show();
    }

    static Dialog buildDialog(Activity activity, Runnable onDismissListener) {
        boolean isDark = isDarkMode(activity);
        int bgColor = isDark ? Color.parseColor("#1C1C1E") : Color.parseColor("#FFFFFF");
        int cardBgColor = isDark ? Color.parseColor("#2C2C2E") : Color.parseColor("#F6F7F9");
        int primaryText = isDark ? Color.parseColor("#F2F2F7") : Color.parseColor("#1D1D1F");
        int subText = isDark ? Color.parseColor("#8E8E93") : Color.parseColor("#86868B");
        int tabInactiveBg = isDark ? Color.parseColor("#2C2C2E") : Color.parseColor("#EFEFF4");
        int tabInactiveText = isDark ? Color.parseColor("#AEAEB2") : Color.parseColor("#636366");

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createRoundedDrawable(bgColor, dp2px(activity, 20)));
        int pad = dp2px(activity, 20);
        root.setPadding(pad, pad, pad, pad);

        // Header Title
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp2px(activity, 14));

        // Douban App Icon
        ImageView appIconView = new ImageView(activity);
        try {
            Drawable iconDrawable = activity.getPackageManager().getApplicationIcon("com.douban.frodo");
            appIconView.setImageDrawable(iconDrawable);
        } catch (Throwable t) {
            try {
                appIconView.setImageResource(R.mipmap.ic_launcher);
            } catch (Throwable ignored) {
                appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }
        int iconSize = dp2px(activity, 32);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMargins(0, 0, dp2px(activity, 12), 0);
        appIconView.setLayoutParams(iconLp);
        header.addView(appIconView);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleCol.setLayoutParams(titleLp);

        TextView titleView = new TextView(activity);
        titleView.setText("DouBanPurify");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(primaryText);
        titleCol.addView(titleView);

        TextView subTitleView = new TextView(activity);
        subTitleView.setText("LSPosed API 102 · 即时生效");
        subTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subTitleView.setTextColor(subText);
        subTitleView.setPadding(0, dp2px(activity, 3), 0, 0);
        titleCol.addView(subTitleView);
        header.addView(titleCol);

        TextView badge = new TextView(activity);
        badge.setText(" v1.4.13 ");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setTextColor(DOUBAN_GREEN);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(createRoundedDrawable(DOUBAN_GREEN_BADGE_BG, dp2px(activity, 6)));
        badge.setPadding(dp2px(activity, 8), dp2px(activity, 3), dp2px(activity, 8), dp2px(activity, 3));
        header.addView(badge);

        root.addView(header);

        // Segmented Tab Bar（短标题等比等宽，避免长文案被裁切）
        LinearLayout tabLayout = new LinearLayout(activity);
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setPadding(0, 0, 0, dp2px(activity, 12));

        String[] tabNames = new String[]{"🚀 广告", "📱 页面", "🔍 搜索", "📚 侧栏", "ℹ️ 关于"};
        List<TextView> tabViews = new ArrayList<>();
        List<View> pageViews = new ArrayList<>();

        // 内容区自适应高度：内容短时收缩，内容长时在 60% 屏高内滚动
        final int maxContentHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.60f);
        ScrollView contentScrollView = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec,
                        View.MeasureSpec.makeMeasureSpec(maxContentHeight, View.MeasureSpec.AT_MOST));
            }
        };
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentScrollView.setLayoutParams(scrollParams);
        contentScrollView.setVerticalScrollBarEnabled(false);

        LinearLayout contentContainer = new LinearLayout(activity);
        contentContainer.setOrientation(LinearLayout.VERTICAL);

        // === TAB 1: 核心拦截 ===
        LinearLayout page1 = new LinearLayout(activity);
        page1.setOrientation(LinearLayout.VERTICAL);

        LinearLayout card1_1 = createCard(activity, cardBgColor);
        addSectionHeader(card1_1, "启动与弹窗拦截", activity);
        addSwitchRow(card1_1, activity, "跳过开屏广告", "彻底跳过冷启动与热启动开屏广告",
                PureSettings.KEY_SKIP_SPLASH, true, primaryText, subText);
        addDivider(card1_1, activity, isDark);
        addSwitchRow(card1_1, activity, "屏蔽 Beta 版本更新", "拦截内测与测试版更新提示及安装弹窗",
                PureSettings.KEY_BLOCK_BETA_UPDATE, true, primaryText, subText);
        addDivider(card1_1, activity, isDark);
        addSwitchRow(card1_1, activity, "屏蔽求好评弹窗", "拦截应用内评价评分与打分提示弹窗",
                PureSettings.KEY_BLOCK_RATING_DIALOG, true, primaryText, subText);
        page1.addView(card1_1);

        LinearLayout card1_2 = createCard(activity, cardBgColor);
        addSectionHeader(card1_2, "信息流与统计分析", activity);
        addSwitchRow(card1_2, activity, "拦截信息流广告", "屏蔽首页精选推荐中的商业广告卡片",
                PureSettings.KEY_BLOCK_FEED_AD, true, primaryText, subText);
        addDivider(card1_2, activity, isDark);
        addSwitchRow(card1_2, activity, "屏蔽友盟统计分析", "阻断 Umeng+ 统计打点、崩溃采集与队列上传",
                PureSettings.KEY_BLOCK_UMENG, true, primaryText, subText);
        page1.addView(card1_2);
        pageViews.add(page1);

        // === TAB 2: 页面与导航精简 ===
        LinearLayout page2 = new LinearLayout(activity);
        page2.setOrientation(LinearLayout.VERTICAL);

        LinearLayout card2_1 = createCard(activity, cardBgColor);
        addSectionHeader(card2_1, "底栏 Tab 精简 (逐项控制 · 自动等宽)", activity);
        addSwitchRow(card2_1, activity, "隐藏「书影音」Tab", "仅隐藏书影音入口，其余 Tab 自动均分剩余宽度",
                PureSettings.KEY_HIDE_TAB_SUBJECT, true, primaryText, subText);
        addDivider(card2_1, activity, isDark);
        addSwitchRow(card2_1, activity, "隐藏「小组」Tab", "仅隐藏小组入口，其余 Tab 自动均分剩余宽度",
                PureSettings.KEY_HIDE_TAB_GROUP, true, primaryText, subText);
        addDivider(card2_1, activity, isDark);
        addSwitchRow(card2_1, activity, "隐藏「市集」Tab", "仅隐藏市集入口，其余 Tab 自动均分剩余宽度",
                PureSettings.KEY_HIDE_TAB_SHIJI, true, primaryText, subText);
        page2.addView(card2_1);

        LinearLayout card2_2 = createCard(activity, cardBgColor);
        addSectionHeader(card2_2, "首页顶栏与浮动按钮", activity);
        addSwitchRow(card2_2, activity, "首页顶部移除动态 Tab", "仅保留精选/推荐，禁止滑动误触空白动态页",
                PureSettings.KEY_HIDE_TOP_DONGTAI, true, primaryText, subText);
        addDivider(card2_2, activity, isDark);
        addSwitchRow(card2_2, activity, "隐藏右上角播客/耳机", "移除首页顶栏右侧多余图标",
                PureSettings.KEY_HIDE_TOP_PODCAST, true, primaryText, subText);
        addDivider(card2_2, activity, isDark);
        addSwitchRow(card2_2, activity, "隐藏搜索框下方宠物挂件", "移除小狗宠物推广与横幅活动挂件",
                PureSettings.KEY_HIDE_TOP_PETS, true, primaryText, subText);
        addDivider(card2_2, activity, isDark);
        addSwitchRow(card2_2, activity, "隐藏右下角发布悬浮笔", "移除首页浮动的写日记/发布笔按钮",
                PureSettings.KEY_HIDE_HOME_POST_BTN, true, primaryText, subText);
        page2.addView(card2_2);
        pageViews.add(page2);

        // === TAB 3: 搜索页 ===
        LinearLayout page3 = new LinearLayout(activity);
        page3.setOrientation(LinearLayout.VERTICAL);

        LinearLayout card3_1 = createCard(activity, cardBgColor);
        addSectionHeader(card3_1, "搜索页净化", activity);
        addSwitchRow(card3_1, activity, "搜索页屏蔽热门话题", "隐藏热门话题标题与列表，不控制上方轮播区",
                PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS, true, primaryText, subText);
        addDivider(card3_1, activity, isDark);
        addSwitchRow(card3_1, activity, "搜索页屏蔽实时热门榜", "隐藏「实时热门书影音 / 讨论」轮播区，默认关闭",
                PureSettings.KEY_HIDE_SEARCH_TRENDS, false, primaryText, subText);
        addDivider(card3_1, activity, isDark);
        addSwitchRow(card3_1, activity, "用电影榜和电视榜替换讨论榜", "关闭后保留原生讨论榜和书影音前十；屏蔽实时热门榜时不显示整个榜单区",
                PureSettings.KEY_REPLACE_SEARCH_BOARDS, PureSettings.DEFAULT_REPLACE_SEARCH_BOARDS, primaryText, subText);
        page3.addView(card3_1);
        pageViews.add(page3);

        // === TAB 4: 侧边栏抽屉 ===
        LinearLayout page4 = new LinearLayout(activity);
        page4.setOrientation(LinearLayout.VERTICAL);

        LinearLayout card3_2 = createCard(activity, cardBgColor);
        addSectionHeader(card3_2, "侧边栏抽屉精简", activity);
        addSwitchRow(card3_2, activity, "侧边栏隐藏未成年人模式", "彻底从抽屉列表中移除未成年人模式入口",
                PureSettings.KEY_HIDE_SIDE_MINOR, true, primaryText, subText);
        addDivider(card3_2, activity, isDark);
        addSwitchRow(card3_2, activity, "侧边栏隐藏我的背包", "移除我的背包入口",
                PureSettings.KEY_HIDE_SIDE_BAG, true, primaryText, subText);
        addDivider(card3_2, activity, isDark);
        addSwitchRow(card3_2, activity, "侧边栏隐藏草稿箱", "移除草稿箱入口",
                PureSettings.KEY_HIDE_SIDE_DRAFTS, true, primaryText, subText);
        addDivider(card3_2, activity, isDark);
        addSwitchRow(card3_2, activity, "侧边栏隐藏签到", "移除签到入口",
                PureSettings.KEY_HIDE_SIDE_CHECKIN, true, primaryText, subText);
        addDivider(card3_2, activity, isDark);
        addSwitchRow(card3_2, activity, "侧边栏隐藏帮助与反馈", "移除帮助与反馈入口",
                PureSettings.KEY_HIDE_SIDE_FEEDBACK, true, primaryText, subText);
        addDivider(card3_2, activity, isDark);
        addSwitchRow(card3_2, activity, "侧边栏隐藏社区管理中心", "移除社区管理中心入口",
                PureSettings.KEY_HIDE_SIDE_COMMUNITY, true, primaryText, subText);
        addDivider(card3_2, activity, isDark);
        addSwitchRow(card3_2, activity, "侧边栏隐藏底部服务网格", "移除我的订单、钱包、礼券等电商金融格子",
                PureSettings.KEY_HIDE_SIDE_SERVICES, true, primaryText, subText);
        page4.addView(card3_2);
        pageViews.add(page4);

        // === TAB 5: 关于模块 ===
        LinearLayout page5 = new LinearLayout(activity);
        page5.setOrientation(LinearLayout.VERTICAL);

        LinearLayout card4_1 = createCard(activity, cardBgColor);
        addSectionHeader(card4_1, "模块信息", activity);
        addInfoRow(card4_1, "模块名称", "DouBanPurify", primaryText, subText, activity);
        addDivider(card4_1, activity, isDark);
        addInfoRow(card4_1, "模块版本", "v1.4.13", primaryText, subText, activity);
        addDivider(card4_1, activity, isDark);
        addInfoRow(card4_1, "适配目标", "豆瓣 Frodo (7.138.0)", primaryText, subText, activity);
        addDivider(card4_1, activity, isDark);

        LinearLayout ghRow = addInfoRow(card4_1, "GitHub 仓库", "Jessire / DouBanPurify", DOUBAN_GREEN, subText, activity);
        ghRow.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jessire/DouBanPurify"));
                activity.startActivity(browserIntent);
            } catch (Throwable t) {
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("github", "https://github.com/Jessire/DouBanPurify"));
                    Toast.makeText(activity, "已复制 GitHub 链接", Toast.LENGTH_SHORT).show();
                }
            }
        });
        page5.addView(card4_1);
        pageViews.add(page5);

        // Build Tabs & Tab Click
        for (int i = 0; i < tabNames.length; i++) {
            final int index = i;
            TextView tab = new TextView(activity);
            tab.setText(tabNames[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tab.setPadding(dp2px(activity, 3), dp2px(activity, 7), dp2px(activity, 3), dp2px(activity, 7));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            tLp.setMargins(dp2px(activity, 2), 0, dp2px(activity, 2), 0);
            tab.setLayoutParams(tLp);

            tab.setOnClickListener(v ->
                    applyTabState(tabViews, pageViews, index, activity, tabInactiveBg, tabInactiveText, true));

            tabViews.add(tab);
            tabLayout.addView(tab);

            contentContainer.addView(pageViews.get(i));
        }

        // Restore last opened tab（默认回到第一个页签）
        int initialTab = PureSettings.getInt(activity, PureSettings.KEY_UI_LAST_TAB, 0);
        if (initialTab < 0 || initialTab >= pageViews.size()) {
            initialTab = 0;
        }
        applyTabState(tabViews, pageViews, initialTab, activity, tabInactiveBg, tabInactiveText, false);

        root.addView(tabLayout);

        contentScrollView.addView(contentContainer);
        root.addView(contentScrollView);

        // Bottom Action Button
        Button btnConfirm = new Button(activity);
        btnConfirm.setText("完成");
        btnConfirm.setTextColor(Color.WHITE);
        btnConfirm.setTypeface(Typeface.DEFAULT_BOLD);
        btnConfirm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnConfirm.setBackground(createRoundedDrawable(DOUBAN_GREEN, dp2px(activity, 12)));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2px(activity, 44));
        btnLp.setMargins(0, dp2px(activity, 14), 0, 0);
        btnConfirm.setLayoutParams(btnLp);
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                DoubanLayoutPurifier.purgeViews(activity.getWindow().getDecorView());
            } catch (Throwable ignored) {
            }
        });
        root.addView(btnConfirm);

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            if (onDismissListener != null) {
                onDismissListener.run();
            }
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            // 高度交给内容自适应：短页签不留大块空白，长页签在 60% 屏高内滚动
            window.setLayout((int) (screenWidth * 0.90f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private static void applyTabState(List<TextView> tabViews, List<View> pageViews, int index,
                                      Context context, int inactiveBg, int inactiveText,
                                      boolean remember) {
        for (int j = 0; j < tabViews.size(); j++) {
            boolean active = (j == index);
            TextView tv = tabViews.get(j);
            tv.setBackground(createRoundedDrawable(active ? DOUBAN_GREEN : inactiveBg, dp2px(context, 16)));
            tv.setTextColor(active ? Color.WHITE : inactiveText);
            tv.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            if (j < pageViews.size()) {
                pageViews.get(j).setVisibility(active ? View.VISIBLE : View.GONE);
            }
        }
        if (remember) {
            PureSettings.setInt(context, PureSettings.KEY_UI_LAST_TAB, index);
        }
    }

    private static LinearLayout createCard(Context context, int bgColor) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(createRoundedDrawable(bgColor, dp2px(context, 14)));
        int pad = dp2px(context, 14);
        card.setPadding(pad, dp2px(context, 8), pad, dp2px(context, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp2px(context, 10));
        card.setLayoutParams(lp);
        return card;
    }

    private static void addSectionHeader(LinearLayout container, String title, Context context) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(DOUBAN_GREEN);
        header.setPadding(dp2px(context, 2), dp2px(context, 4), dp2px(context, 2), dp2px(context, 6));
        container.addView(header);
    }

    private static void addSwitchRow(LinearLayout container, Activity activity, String title,
                                     String desc, String prefKey, boolean defaultValue,
                                     int primaryColor, int subColor) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp2px(activity, 9), 0, dp2px(activity, 9));

        LinearLayout textLayout = new LinearLayout(activity);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(textLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvTitle.setTextColor(primaryColor);
        textLayout.addView(tvTitle);

        if (desc != null && !desc.isEmpty()) {
            TextView tvDesc = new TextView(activity);
            tvDesc.setText(desc);
            tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvDesc.setTextColor(subColor);
            tvDesc.setPadding(0, dp2px(activity, 2), 0, 0);
            textLayout.addView(tvDesc);
        }

        row.addView(textLayout);

        Switch sw = new Switch(activity);
        boolean currentVal = PureSettings.getBoolean(activity, prefKey, defaultValue);
        sw.setChecked(currentVal);

        ColorStateList thumbStates = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{DOUBAN_GREEN, Color.parseColor("#B0B0B0")}
        );
        ColorStateList trackStates = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{DOUBAN_GREEN_LIGHT, Color.parseColor("#E0E0E0")}
        );
        sw.setThumbTintList(thumbStates);
        sw.setTrackTintList(trackStates);

        sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PureSettings.setBoolean(activity, prefKey, isChecked);
            try {
                DoubanLayoutPurifier.purgeViews(activity.getWindow().getDecorView());
            } catch (Throwable ignored) {
            }
        });

        row.addView(sw);
        container.addView(row);
    }

    private static LinearLayout addInfoRow(LinearLayout container, String label, String value,
                                   int valueColor, int subColor, Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp2px(context, 10), 0, dp2px(context, 10));

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvLabel.setTextColor(subColor);
        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tvLabel.setLayoutParams(lParams);
        row.addView(tvLabel);

        TextView tvValue = new TextView(context);
        tvValue.setText(value);
        tvValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvValue.setTextColor(valueColor);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tvValue);

        container.addView(row);
        return row;
    }

    private static void addDivider(LinearLayout container, Context context, boolean isDark) {
        View divider = new View(context);
        divider.setBackgroundColor(isDark ? Color.parseColor("#38383A") : Color.parseColor("#E5E5EA"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2px(context, 0.6f));
        divider.setLayoutParams(lp);
        container.addView(divider);
    }

    private static GradientDrawable createRoundedDrawable(int color, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radiusPx);
        return gd;
    }

    private static boolean isDarkMode(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
