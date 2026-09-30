package com.douban.pure;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

public final class DoubanAdPurifier {
    private static final String TAG = "DoubanPure";

    private DoubanAdPurifier() {
    }

    public static void install(XposedInterface xposed, ClassLoader cl) {
        hookFeedAd(xposed, cl);
        hookSplashRequestor(xposed, cl);
        hookSplashActivity(xposed, cl);
        hookSplashFragment(xposed, cl);
        hookByteDanceAndTencentAds(xposed, cl);
        hookAdDurationAndTimeouts(xposed, cl);
        hookBetaUpdateAndRating(xposed, cl);
        hookUmeng(xposed, cl);
    }

    private static void hookFeedAd(XposedInterface xposed, ClassLoader cl) {
        String cls = "com.douban.frodo.baseproject.ad.model.FeedAd";
        hookBooleanAdMethod(xposed, cls, cl, "isBlocked", true);
        hookBooleanAdMethod(xposed, cls, cl, "getIsBlocked", true);
        hookBooleanAdMethod(xposed, cls, cl, "isAd", false);
        hookBooleanAdMethod(xposed, cls, cl, "isValid", false);
        hookBooleanAdMethod(xposed, cls, cl, "isAvailable", false);

        String[] adViewClasses = new String[]{
                "com.douban.frodo.baseproject.ad.view.FeedAdItemParent",
                "com.douban.frodo.baseproject.ad.view.FeedAdItemView1",
                "com.douban.frodo.baseproject.ad.view.FeedAdItemView2",
                "com.douban.frodo.baseproject.ad.view.FeedAdItemView3",
                "com.douban.frodo.baseproject.ad.view.FeedAdItemView4",
                "com.douban.frodo.baseproject.ad.view.FeedAdItemView5",
                "com.douban.frodo.baseproject.ad.view.FeedAdItemView60",
                "com.douban.frodo.baseproject.ad.view.FeedAdItemView7",
                "com.douban.frodo.baseproject.ad.sdk.FeedAdItemSdkView",
                "com.douban.frodo.baseproject.ad.view.RecentTopicAdView"
        };
        for (String adViewCls : adViewClasses) {
            ReflectUtils.hookAllMethods(xposed, adViewCls, cl, "updateView", chain -> {
                Object obj = chain.getThisObject();
                if (obj instanceof View) {
                    ((View) obj).setVisibility(View.GONE);
                }
                return null;
            });
            ReflectUtils.hookAllMethods(xposed, adViewCls, cl, "bind", chain -> {
                Object obj = chain.getThisObject();
                if (obj instanceof View) {
                    ((View) obj).setVisibility(View.GONE);
                }
                return null;
            });
            ReflectUtils.hookAllMethods(xposed, adViewCls, cl, "populate", chain -> {
                Object obj = chain.getThisObject();
                if (obj instanceof View) {
                    ((View) obj).setVisibility(View.GONE);
                }
                return null;
            });
        }
        Log.i(TAG, "FeedAd hooks installed");
    }

    private static void hookBooleanAdMethod(XposedInterface xposed, String className,
                                            ClassLoader cl, String methodName, boolean blockedValue) {
        Class<?> type = ReflectUtils.findClass(className, cl);
        if (type == null) return;
        for (Method method : type.getDeclaredMethods()) {
            // isValid in Douban 7.134.0 returns an integer status, not a boolean.
            // Preserve unknown contracts instead of guessing the meaning of a status code.
            if (!methodName.equals(method.getName())
                    || (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class)) continue;
            try {
                method.setAccessible(true);
                xposed.hook(method).intercept(chain -> {
                    Context ctx = getContext(chain.getThisObject());
                    if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true)) return blockedValue;
                    return chain.proceed();
                });
            } catch (Throwable t) {
                Log.w(TAG, "Boolean ad hook failed: " + methodName + " - " + t);
            }
        }
    }

    private static void hookSplashRequestor(XposedInterface xposed, ClassLoader cl) {
        String[] requestorClasses = new String[]{
                "com.douban.frodo.splash.requester.SplashAdNewRequestor",
                "com.douban.frodo.splash.requester.SplashBaseRequestor",
                "com.douban.frodo.splash.SplashAdNewRequestor",
                "com.douban.frodo.splash.SplashBaseRequestor"
        };

        for (String reqCls : requestorClasses) {
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "getRequestTimeout", chain -> 0);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "getBidTimeout", chain -> 0);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "request", chain -> null);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "fetch", chain -> null);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "fetchAd", chain -> null);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "requestSplashAd", chain -> null);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "loadSplashAd", chain -> null);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "showAd", chain -> null);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "showSplashAd", chain -> null);
            ReflectUtils.hookAllMethods(xposed, reqCls, cl, "showSplash", chain -> null);
        }

        // SplashAdShowUtils
        ReflectUtils.hookAllMethods(xposed, "com.douban.frodo.splash.SplashAdShowUtils", cl, "showSplashAd", chain -> null);

        // AdApi splash request
        ReflectUtils.hookAllMethods(xposed, "com.douban.ad.api.AdApi", cl, "requestSplashShow", chain -> null);

        // AdView b
        ReflectUtils.hookAllMethods(xposed, "com.douban.ad.AdView", cl, "b", chain -> null);
        Log.i(TAG, "Splash requestor hooks installed");
    }

    private static void hookSplashActivity(XposedInterface xposed, ClassLoader cl) {
        String splashCls = "com.douban.frodo.activity.SplashActivity";
        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "isSplashHot", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = obj instanceof Activity ? (Context) obj : null;
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                return false;
            }
            return chain.proceed();
        });

        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "isSplashResume", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = obj instanceof Activity ? (Context) obj : null;
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                return false;
            }
            return chain.proceed();
        });

        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "initSplashHotControl", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = obj instanceof Activity ? (Context) obj : null;
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                return null;
            }
            return chain.proceed();
        });

        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "showSplash", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = obj instanceof Activity ? (Context) obj : null;
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                return null;
            }
            return chain.proceed();
        });

        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "showSplashAd", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = obj instanceof Activity ? (Context) obj : null;
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                return null;
            }
            return chain.proceed();
        });

        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "fallbackShowDefaultSplash", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = obj instanceof Activity ? (Context) obj : null;
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                return null;
            }
            return chain.proceed();
        });

        // Hot splash activity
        String hotCls = "com.douban.frodo.activity.SplashActivityHot";
        ReflectUtils.hookMethod(xposed, hotCls, cl, "onCreate", new Class<?>[]{Bundle.class}, chain -> {
            try {
                Activity act = (Activity) chain.getThisObject();
                if (PureSettings.getBoolean(act, PureSettings.KEY_SKIP_SPLASH, true)) {
                    act.finish();
                    Log.i(TAG, "SplashActivityHot finished immediately");
                    return null;
                }
            } catch (Throwable ignored) {
            }
            return chain.proceed();
        });
    }

    private static void hookSplashFragment(XposedInterface xposed, ClassLoader cl) {
        String[] splashFragmentClasses = new String[]{
                "com.douban.frodo.splash.SplashAdFragment",
                "com.douban.frodo.splash.s"
        };
        for (String fragCls : splashFragmentClasses) {
            ReflectUtils.hookMethod(xposed, fragCls, cl, "onViewCreated", new Class<?>[]{View.class, Bundle.class}, chain -> {
                Object res = chain.proceed();
                try {
                    View view = (View) chain.getArg(0);
                    Context ctx = view != null ? view.getContext() : null;
                    if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                        Object handler = ReflectUtils.getField(chain.getThisObject(), "J");
                        if (handler instanceof Handler) {
                            ((Handler) handler).removeCallbacksAndMessages(null);
                        }
                        Method b1 = ReflectUtils.findMethod(chain.getThisObject().getClass(), "b1");
                        if (b1 != null) {
                            b1.invoke(chain.getThisObject());
                            Log.i(TAG, "Splash fragment fast exit b1() invoked");
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "splash fragment onViewCreated error: " + t);
                }
                return res;
            });
        }
    }

    private static void hookByteDanceAndTencentAds(XposedInterface xposed, ClassLoader cl) {
        // 1. Block ByteDance CSJ splash ad loading
        ReflectUtils.hookAllMethods(xposed, "com.bytedance.sdk.openadsdk.TTAdNative", cl, "loadSplashAd", chain -> {
            Log.i(TAG, "Blocked TTAdNative.loadSplashAd");
            return null;
        });

        // 2. Block ByteDance TTDelegateActivity (splash ad delegate activity)
        String ttDelegate = "com.bytedance.sdk.openadsdk.core.activity.base.TTDelegateActivity";
        ReflectUtils.hookMethod(xposed, ttDelegate, cl, "onCreate", new Class<?>[]{Bundle.class}, chain -> {
            try {
                Activity act = (Activity) chain.getThisObject();
                act.finish();
                Log.i(TAG, "TTDelegateActivity finished immediately");
                return null;
            } catch (Throwable ignored) {
            }
            return chain.proceed();
        });

        // 3. Block Tencent GDT splash ads
        String gdtSplash = "com.qq.e.ads.splash.SplashAD";
        ReflectUtils.hookAllMethods(xposed, gdtSplash, cl, "fetchAndShowIn", chain -> null);
        ReflectUtils.hookAllMethods(xposed, gdtSplash, cl, "fetchFullScreenAndShowIn", chain -> null);
        ReflectUtils.hookAllMethods(xposed, gdtSplash, cl, "showAd", chain -> null);

        Log.i(TAG, "ByteDance and Tencent ad hooks installed");
    }

    private static void hookAdDurationAndTimeouts(XposedInterface xposed, ClassLoader cl) {
        String adCls = "com.douban.frodo.baseproject.ad.model.DoubanAd";
        ReflectUtils.hookAllMethods(xposed, adCls, cl, "getDuration", chain -> 0);
        ReflectUtils.hookAllMethods(xposed, adCls, cl, "getVideoSkipDelay", chain -> 0);

        ReflectUtils.hookAllMethods(xposed, "com.douban.ad.p", cl, "handleMessage", chain -> {
            try {
                Object msgObj = chain.getArg(0);
                if (msgObj instanceof android.os.Message) {
                    android.os.Message msg = (android.os.Message) msgObj;
                    if (msg.what == 1) {
                        msg.arg1 = 0;
                    }
                }
            } catch (Throwable ignored) {
            }
            return chain.proceed();
        });
    }

    private static void hookBetaUpdateAndRating(XposedInterface xposed, ClassLoader cl) {
        // 1. Block Beta APK Dialog Activity
        String betaDialog = "com.douban.frodo.activity.BetaApkDialogActivity";
        ReflectUtils.hookMethod(xposed, betaDialog, cl, "onCreate", new Class<?>[]{Bundle.class}, chain -> {
            try {
                Activity act = (Activity) chain.getThisObject();
                if (PureSettings.getBoolean(act, PureSettings.KEY_BLOCK_BETA_UPDATE, true)) {
                    act.finish();
                    Log.i(TAG, "BetaApkDialogActivity blocked and finished");
                    return null;
                }
            } catch (Throwable ignored) {
            }
            return chain.proceed();
        });

        // 2. Block Beta APK Install Activity
        String betaInstall = "com.douban.frodo.activity.BetaApkInstallActivity";
        ReflectUtils.hookMethod(xposed, betaInstall, cl, "onCreate", new Class<?>[]{Bundle.class}, chain -> {
            try {
                Activity act = (Activity) chain.getThisObject();
                if (PureSettings.getBoolean(act, PureSettings.KEY_BLOCK_BETA_UPDATE, true)) {
                    act.finish();
                    Log.i(TAG, "BetaApkInstallActivity blocked and finished");
                    return null;
                }
            } catch (Throwable ignored) {
            }
            return chain.proceed();
        });

        // 3. Block Skynet Rating (评价/评分弹窗)
        String ratingDialog = "com.douban.frodo.skynet.widget.SkynetRatingDialogFragment";
        ReflectUtils.hookAllMethods(xposed, ratingDialog, cl, "show", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = getContext(obj);
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_RATING_DIALOG, true)) {
                Log.i(TAG, "Blocked SkynetRatingDialogFragment.show");
                return null;
            }
            return chain.proceed();
        });
        ReflectUtils.hookAllMethods(xposed, ratingDialog, cl, "onCreateDialog", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = getContext(obj);
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_RATING_DIALOG, true)) {
                return null;
            }
            return chain.proceed();
        });
        ReflectUtils.hookAllMethods(xposed, ratingDialog, cl, "onCreateView", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = getContext(obj);
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_RATING_DIALOG, true)) {
                return null;
            }
            return chain.proceed();
        });

        // 4. Intercept startActivity that launches Beta dialog, Hot splash, or CSJ ad activities
        try {
            Method startActivityMethod = Activity.class.getDeclaredMethod("startActivity", Intent.class);
            xposed.hook(startActivityMethod).intercept(chain -> {
                Activity caller = (Activity) chain.getThisObject();
                Intent intent = (Intent) chain.getArg(0);
                if (intent != null) {
                    ComponentName comp = intent.getComponent();
                    if (comp != null) {
                        String clsName = comp.getClassName();
                        if (clsName.contains("BetaApk") || clsName.contains("BetaApkDialogActivity") || clsName.contains("UpgradeActivity")) {
                            if (PureSettings.getBoolean(caller, PureSettings.KEY_BLOCK_BETA_UPDATE, true)) {
                                Log.i(TAG, "Prevented launching: " + clsName);
                                return null;
                            }
                        }
                        if (clsName.contains("SplashActivityHot")) {
                            if (PureSettings.getBoolean(caller, PureSettings.KEY_SKIP_SPLASH, true)) {
                                Log.i(TAG, "Prevented launching SplashActivityHot");
                                return null;
                            }
                        }
                        if (clsName.contains("openadsdk") || clsName.contains("TTDelegateActivity")) {
                            if (PureSettings.getBoolean(caller, PureSettings.KEY_SKIP_SPLASH, true)) {
                                Log.i(TAG, "Prevented launching CSJ Ad Activity: " + clsName);
                                return null;
                            }
                        }
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.w(TAG, "hook startActivity failed: " + t);
        }

        Log.i(TAG, "Beta update and rating popup hooks installed");
    }

    private static void hookUmeng(XposedInterface xposed, ClassLoader cl) {
        String umc = "com.umeng.commonsdk.UMConfigure";
        ReflectUtils.hookAllMethods(xposed, umc, cl, "init", chain -> {
            Context ctx = (Context) chain.getArg(0);
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_UMENG, true)) return null;
            return chain.proceed();
        });
        ReflectUtils.hookAllMethods(xposed, umc, cl, "preInit", chain -> {
            Context ctx = (Context) chain.getArg(0);
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_UMENG, true)) return null;
            return chain.proceed();
        });

        String mob = "com.umeng.analytics.MobclickAgent";
        ReflectUtils.hookAllMethods(xposed, mob, cl, "onEvent", chain -> {
            Context ctx = (Context) chain.getArg(0);
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_UMENG, true)) return null;
            return chain.proceed();
        });
        ReflectUtils.hookAllMethods(xposed, mob, cl, "onEventValue", chain -> null);
        ReflectUtils.hookAllMethods(xposed, mob, cl, "onPageStart", chain -> null);
        ReflectUtils.hookAllMethods(xposed, mob, cl, "onPageEnd", chain -> null);
        ReflectUtils.hookAllMethods(xposed, mob, cl, "onResume", chain -> null);
        ReflectUtils.hookAllMethods(xposed, mob, cl, "onPause", chain -> null);

        String crash = "com.umeng.umcrash.UMCrash";
        ReflectUtils.hookAllMethods(xposed, crash, cl, "init", chain -> null);
        ReflectUtils.hookAllMethods(xposed, crash, cl, "initConfig", chain -> null);
        ReflectUtils.hookAllMethods(xposed, crash, cl, "generateCustomLog", chain -> null);

        String dispatch = "com.umeng.commonsdk.framework.UMWorkDispatch";
        ReflectUtils.hookAllMethods(xposed, dispatch, cl, "sendEvent", chain -> null);
        ReflectUtils.hookAllMethods(xposed, dispatch, cl, "sendEventEx", chain -> null);
        ReflectUtils.hookAllMethods(xposed, dispatch, cl, "sendEventInternal", chain -> null);
    }

    private static Context getContext(Object obj) {
        if (obj instanceof View) {
            return ((View) obj).getContext();
        }
        if (obj instanceof Activity) {
            return (Context) obj;
        }
        return null;
    }
}

