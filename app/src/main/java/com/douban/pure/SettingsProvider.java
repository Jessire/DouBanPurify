package com.douban.pure;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class SettingsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.douban.pure.settings";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context ctx = getContext();
        if (ctx == null) return null;
        SharedPreferences sp = ctx.getSharedPreferences(PureSettings.PREF_NAME, Context.MODE_PRIVATE);
        Bundle res = new Bundle();

        if ("get_boolean".equals(method) && arg != null) {
            boolean def = extras != null && extras.getBoolean("def", true);
            res.putBoolean("value", sp.getBoolean(arg, def));
            return res;
        } else if ("set_boolean".equals(method) && arg != null && extras != null) {
            boolean val = extras.getBoolean("value", true);
            sp.edit().putBoolean(arg, val).apply();
            res.putBoolean("success", true);
            return res;
        }
        return res;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
