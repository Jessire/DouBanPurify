package com.douban.pure;

import android.app.Activity;
import android.app.Application;
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
    }

    private static void installBaseHooks(XposedInterface xposed, ClassLoader bootClassLoader) {
        if (!HOOKED.compareAndSet(false, true)) {
            return;
        }

        try {
            // Hook Application.onCreate to catch early decrypted ClassLoader
            try {
                Method appOnCreate = Application.class.getDeclaredMethod("onCreate");
                xposed.hook(appOnCreate).intercept(chain -> {
                    Object res = chain.proceed();
                    Object app = chain.getThisObject();
                    if (app instanceof Application) {
                        tryInstallRealHooks(xposed, ((Application) app).getClassLoader());
                    }
                    return res;
                });
            } catch (Throwable t) {
                Log.w(TAG, "hook Application.onCreate warning: " + t);
            }

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

                    // Install hooks on decrypted ClassLoader as soon as real Activity ClassLoader is available
                    tryInstallRealHooks(xposed, act.getClassLoader());

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

    static void tryInstallRealHooks(XposedInterface xposed, ClassLoader cl) {
        if (cl == null || APP_HOOKED.get()) {
            return;
        }

        try {
            // Check if YiDun has unpacked Douban's real classes
            Class<?> feedAdClass = cl.loadClass("com.douban.frodo.baseproject.ad.model.FeedAd");
            if (feedAdClass != null && APP_HOOKED.compareAndSet(false, true)) {
                DoubanAdPurifier.install(xposed, cl);
                DoubanLayoutPurifier.install(xposed, cl);
                Log.i(TAG, "Successfully installed purifier hooks on unpacked ClassLoader!");
            }
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "Douban classes not unpacked yet in classloader: " + cl);
        } catch (Throwable t) {
            Log.w(TAG, "tryInstallRealHooks error: " + t);
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
