package com.douban.pure;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class DoubanPureHook extends XposedModule {
    public static final String TAG = "DoubanPure";
    private static final String TARGET_PKG = "com.douban.frodo";
    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean APP_HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean RECEIVER_REGISTERED = new AtomicBoolean(false);
    private static volatile DoubanPureHook instance;

    public static android.content.SharedPreferences remotePreferences() {
        if (instance != null) {
            try {
                return instance.getRemotePreferences(PureSettings.PREF_NAME);
            } catch (Throwable t) {
                Log.w(TAG, "getRemotePreferences failed: " + t);
            }
        }
        return null;
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        instance = this;
        Log.i(TAG, "DoubanPure API 102 module loaded.");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!TARGET_PKG.equals(param.getPackageName())) {
            return;
        }
        instance = this;
        Log.i(TAG, "DoubanPure onPackageReady: " + param.getPackageName());
        installBaseHooks(this, param.getClassLoader());
        installAppHooks(this, param.getClassLoader());
    }

    private static void installBaseHooks(XposedInterface xposed, ClassLoader bootClassLoader) {
        if (!HOOKED.compareAndSet(false, true)) {
            return;
        }

        try {
            // Hook Activity.onCreate
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(onCreate).intercept(chain -> {
                Object target = chain.getThisObject();
                if (target instanceof Activity) {
                    Activity act = (Activity) target;
                    String actName = act.getClass().getName();

                    // Check if skip_splash is enabled
                    boolean skipSplash = PureSettings.getBoolean(act, PureSettings.KEY_SKIP_SPLASH, true);
                    if (skipSplash && actName.contains("SplashActivity")) {
                        Intent intent = act.getIntent();
                        if (intent == null) {
                            intent = new Intent();
                            act.setIntent(intent);
                        }
                        intent.putExtra("no_splash", true);
                        intent.putExtra("show_main", true);
                        Log.i(TAG, "Forced no_splash=true and show_main=true on SplashActivity");
                    }

                    // Install app-classloader hooks as soon as real ClassLoader is available
                    installAppHooks(xposed, act.getClassLoader());

                    // Register settings changed listener
                    registerReceiverIfNeeded(act);
                }
                return chain.proceed();
            });

            // Hook Activity.onResume
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            xposed.hook(onResume).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object target = chain.getThisObject();
                    if (target instanceof Activity) {
                        DoubanLayoutPurifier.onActivityResumed((Activity) target);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "onResume hook error: " + t);
                }
                return result;
            });

            Log.i(TAG, "Base Activity lifecycle hooks installed.");
        } catch (Throwable t) {
            Log.e(TAG, "installBaseHooks failed: " + t);
        }
    }

    static void installAppHooks(XposedInterface xposed, ClassLoader appClassLoader) {
        if (!APP_HOOKED.compareAndSet(false, true)) {
            return;
        }
        try {
            DoubanAdPurifier.install(xposed, appClassLoader);
            DoubanLayoutPurifier.install(xposed, appClassLoader);
            Log.i(TAG, "Douban app-classloader hooks installed successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "installAppHooks error: " + t);
        }
    }

    private static void registerReceiverIfNeeded(Activity act) {
        if (!RECEIVER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            IntentFilter filter = new IntentFilter(PureSettings.ACTION_SETTINGS_CHANGED);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.i(TAG, "Settings change broadcast received, refreshing views");
                    try {
                        DoubanLayoutPurifier.purgeViews(act.getWindow().getDecorView());
                    } catch (Throwable ignored) {
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                act.getApplicationContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                act.getApplicationContext().registerReceiver(receiver, filter);
            }
            Log.i(TAG, "Settings change broadcast receiver registered");
        } catch (Throwable t) {
            Log.w(TAG, "registerReceiver error: " + t);
        }
    }
}
