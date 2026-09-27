package com.douban.pure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

public final class PureSettings {
    public static final String PREF_NAME = "douban_pure_settings";
    public static final String ACTION_SETTINGS_CHANGED = "com.douban.pure.ACTION_SETTINGS_CHANGED";

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

    public static boolean getBoolean(Context context, String key, boolean defValue) {
        if (context == null) return defValue;

        // 1. Try remotePreferences from LibXposed module
        try {
            SharedPreferences remote = DoubanPureHook.remotePreferences();
            if (remote != null && remote.contains(key)) {
                return remote.getBoolean(key, defValue);
            }
        } catch (Throwable ignored) {
        }

        // 2. Try Provider cross-process lookup
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

        // 3. Fallback to local SharedPreferences
        try {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(key, defValue);
        } catch (Throwable ignored) {
            return defValue;
        }
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
}
