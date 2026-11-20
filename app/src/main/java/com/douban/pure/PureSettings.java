package com.douban.pure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

public final class PureSettings {
    public static final String PREF_NAME = "douban_pure_settings";
    public static final String ACTION_SETTINGS_CHANGED = "com.douban.pure.ACTION_SETTINGS_CHANGED";

    // 界面状态（仅本进程使用，不参与净化开关）
    public static final String KEY_UI_LAST_TAB = "ui_last_tab";

    // 宿主进程内的 Application Context 兜底：没有 View/Activity 上下文的 Hook 也能读到真实开关值
    private static volatile Context sFallbackContext = null;

    public static void setFallbackContext(Context context) {
        if (context == null) return;
        try {
            Context app = context.getApplicationContext();
            sFallbackContext = app != null ? app : context;
        } catch (Throwable ignored) {
            sFallbackContext = context;
        }
    }

    // 广告与秒开
    public static final String KEY_SKIP_SPLASH = "skip_splash";
    public static final String KEY_BLOCK_FEED_AD = "block_feed_ad";
    public static final String KEY_BLOCK_UMENG = "block_umeng";
    public static final String KEY_BLOCK_BETA_UPDATE = "block_beta_update";
    public static final String KEY_BLOCK_RATING_DIALOG = "block_rating_dialog";

    // 底栏
    public static final String KEY_HIDE_TAB_SUBJECT = "hide_tab_subject";
    public static final String KEY_HIDE_TAB_GROUP = "hide_tab_group";
    public static final String KEY_HIDE_TAB_SHIJI = "hide_tab_shiji";

    // 首页顶栏与元素
    public static final String KEY_HIDE_TOP_DONGTAI = "hide_top_dongtai";
    public static final String KEY_HIDE_TOP_PODCAST = "hide_top_podcast";
    public static final String KEY_HIDE_TOP_PETS = "hide_top_pets";
    public static final String KEY_HIDE_HOME_POST_BTN = "hide_home_post_btn";

    // 搜索页
    public static final String KEY_HIDE_SEARCH_HOT_TOPICS = "hide_search_hot_topics";
    public static final String KEY_HIDE_SEARCH_TRENDS = "hide_search_trends";
    public static final String KEY_REPLACE_SEARCH_BOARDS = "replace_search_boards";
    public static final boolean DEFAULT_REPLACE_SEARCH_BOARDS = true;

    // 侧边栏
    public static final String KEY_HIDE_SIDE_BAG = "hide_side_bag";
    public static final String KEY_HIDE_SIDE_DRAFTS = "hide_side_drafts";
    public static final String KEY_HIDE_SIDE_FAV = "hide_side_fav";
    public static final String KEY_HIDE_SIDE_FOLLOW = "hide_side_follow";
    public static final String KEY_HIDE_SIDE_HISTORY = "hide_side_history";
    public static final String KEY_HIDE_SIDE_CHECKIN = "hide_side_checkin";
    public static final String KEY_HIDE_SIDE_MINOR = "hide_side_minor";
    public static final String KEY_HIDE_SIDE_FEEDBACK = "hide_side_feedback";
    public static final String KEY_HIDE_SIDE_COMMUNITY = "hide_side_community";
    public static final String KEY_HIDE_SIDE_SERVICES = "hide_side_services";
    public static final String KEY_HIDE_SIDE_LICENSE = "hide_side_license";

    private PureSettings() {
    }

    /** 整榜隐藏优先；此判定只控制侧卡替换，不控制原生书影音榜的前十条。 */
    public static boolean shouldReplaceSearchBoards(Context context) {
        return !getBoolean(context, KEY_HIDE_SEARCH_TRENDS, false)
                && getBoolean(context, KEY_REPLACE_SEARCH_BOARDS, DEFAULT_REPLACE_SEARCH_BOARDS);
    }

    public static boolean getBoolean(Context context, String key, boolean defValue) {
        if (context == null) context = sFallbackContext;
        if (context == null) return defValue;

        // Prefer the shared store that setBoolean actually updates.
        try {
            Bundle extras = new Bundle();
            extras.putBoolean("def", defValue);
            Bundle res = context.getContentResolver().call(
                    Uri.parse("content://" + SettingsProvider.AUTHORITY), "get_boolean", key, extras);
            if (res != null && res.containsKey("value")) {
                return res.getBoolean("value", defValue);
            }
        } catch (Throwable ignored) {
        }

        try {
            SharedPreferences local = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            if (local.contains(key)) return local.getBoolean(key, defValue);
        } catch (Throwable ignored) {
        }

        // Legacy fallback only: dialog writes never update remotePreferences.
        try {
            SharedPreferences remote = DoubanPureHook.remotePreferences();
            if (remote != null) return remote.getBoolean(key, defValue);
        } catch (Throwable ignored) {
        }
        return defValue;
    }

    public static void setBoolean(Context context, String key, boolean value) {
        if (context == null) return;

        // 1. Write local SharedPreferences
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(key, value).apply();
        } catch (Throwable ignored) {
        }

        // 2. Write to Provider
        try {
            Bundle extras = new Bundle();
            extras.putBoolean("value", value);
            context.getContentResolver().call(
                    Uri.parse("content://" + SettingsProvider.AUTHORITY), "set_boolean", key, extras);
        } catch (Throwable ignored) {
        }

        // 3. Broadcast setting change notification
        try {
            Intent intent = new Intent(ACTION_SETTINGS_CHANGED);
            intent.putExtra("key", key);
            intent.putExtra("value", value);
            intent.setPackage("com.douban.frodo");
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }

    /** 仅供界面使用：读取上次停留的设置页签，不写入模块 Provider。 */
    public static int getInt(Context context, String key, int defValue) {
        if (context == null) context = sFallbackContext;
        if (context == null) return defValue;
        try {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(key, defValue);
        } catch (Throwable ignored) {
            return defValue;
        }
    }

    /** 仅供界面使用：保存当前设置页签。 */
    public static void setInt(Context context, String key, int value) {
        if (context == null) context = sFallbackContext;
        if (context == null) return;
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(key, value).apply();
        } catch (Throwable ignored) {
        }
    }
}
