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

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

public final class SidebarInjector {
    private static final String TAG = "DoubanPure";
    private static final String SIDEBAR_ENTRY_TAG = "douban_pure_sidebar_entry";
    private static final int DOUBAN_GREEN = Color.parseColor("#42BD56");

    private SidebarInjector() {
    }

    private static final String HOST_VIEW = "com.douban.frodo.view.";
    private static final String MENU = HOST_VIEW + "SlideMenuView";
    private static final String LIST = HOST_VIEW + "SlideMenuListEntriesView";
    private static final String ROW = HOST_VIEW + "SlideMenuListEntryView";

    static void install(XposedInterface xposed, ClassLoader cl) {
        after(xposed, ReflectUtils.findClass(MENU, cl), "onAttachedToWindow",
                v -> DoubanLayoutPurifier.purgeDrawerViews(v));
        Class<?> list = ReflectUtils.findClass(LIST, cl);
        if (list != null) try {
            Method bind = list.getDeclaredMethod("setData", List.class);
            if (bind.getReturnType() == void.class && ViewGroup.class.isAssignableFrom(list)) {
                xposed.deoptimize(bind); // Native list bind invokes the row setter in a loop.
                xposed.hook(bind).intercept(chain -> {
                    ViewGroup entries = (ViewGroup) chain.getThisObject();
                    if (menuRoot(entries) == null) return chain.proceed();
                    // Native setData calls removeAllViews. Keep our row alive across that call,
                    // but leave native binding/child order/handles completely untouched.
                    View existing = findEntry(entries);
                    Object result = chain.proceed();
                    afterListBound(entries, existing);
                    return result;
                });
                Log.i(TAG, "sidebar native list bind hook registered");
            }
        } catch (Throwable t) {
            Log.w(TAG, "sidebar list binding hook unavailable", t);
        }
        Class<?> rowModel = ReflectUtils.findClass(ROW + "$b", cl);
        if (rowModel != null) after(xposed, ReflectUtils.findClass(ROW, cl), "setData", v -> {
            if (v instanceof ViewGroup) DoubanLayoutPurifier.onSidebarRowBound((ViewGroup) v);
        }, rowModel);
    }

    private interface After { void run(View view); }

    private static void after(XposedInterface xposed, Class<?> owner, String name, After action,
                              Class<?>... arguments) {
        if (owner == null || !View.class.isAssignableFrom(owner)) return;
        try {
            Method method = owner.getDeclaredMethod(name, arguments);
            if (method.getReturnType() != void.class) return;
            xposed.hook(method).intercept(chain -> {
                Object result = chain.proceed();
                try { action.run((View) chain.getThisObject()); }
                catch (RuntimeException e) { Log.w(TAG, "sidebar callback failed: " + name, e); }
                return result;
            });
            Log.i(TAG, "sidebar native hook registered: " + owner.getSimpleName() + "." + name);
        } catch (Throwable t) {
            Log.w(TAG, "sidebar hook unavailable: " + name, t);
        }
    }

    static boolean isMenuRoot(View view) { return MENU.equals(view.getClass().getName()); }
    static boolean isRow(View view) { return "SlideMenuListEntryView".equals(view.getClass().getSimpleName()); }

    static View menuRoot(View view) {
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (isMenuRoot(v)) return v;
        }
        return null;
    }

    static View findEntry(ViewGroup list) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (SIDEBAR_ENTRY_TAG.equals(child.getTag())) return child;
        }
        return null;
    }

    static void afterListBound(ViewGroup list, View oldEntry) {
        try {
            // All native rows are now labeled and are parents' children, but not measured yet.
            DoubanLayoutPurifier.purgeSidebarTree(list, 0);
            reconcileEntry(list, oldEntry);
        } catch (RuntimeException e) {
            Log.w(TAG, "sidebar list reconciliation failed", e);
        }
    }

    public static void inject(View root) {
        if (root == null) return;
        ViewGroup list = findListEntriesView(root);
        if (list == null) return;
        try { reconcileEntry(list, null); }
        catch (RuntimeException e) { Log.w(TAG, "sidebar entry injection failed", e); }
    }

    private static void reconcileEntry(ViewGroup list, View oldEntry) {
        int count = list.getChildCount();
        if (count == 0) return; // Waiting for the host's initial list bind.
        View entry = findEntry(list);
        int current = entry == null ? -1 : list.indexOfChild(entry);
        int target = count - (current >= 0 ? 1 : 0); // Append if the host has no Settings row.
        for (int i = 0; i < count; i++) {
            View child = list.getChildAt(i);
            if (isRow(child) && containsSettingsTitle(child)) {
                target = i + 1 - (current >= 0 && current < i ? 1 : 0);
                break;
            }
        }
        if (current == target) return;
        if (entry == null) {
            if (oldEntry != null && oldEntry.getParent() == null) entry = oldEntry;
            else {
                Activity activity = findActivity(list.getContext());
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
                entry = createSidebarEntryView(activity);
                entry.setTag(SIDEBAR_ENTRY_TAG);
            }
        } else list.removeViewAt(current); // Only if the anchor actually moved.
        list.addView(entry, target);
    }

    private static ViewGroup findListEntriesView(View view) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        if (LIST.equals(view.getClass().getName()) && menuRoot(group) != null) return group;
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = findListEntriesView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean containsSettingsTitle(View view) {
        if (view instanceof TextView) return "设置".contentEquals(((TextView) view).getText());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsSettingsTitle(group.getChildAt(i))) return true;
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
