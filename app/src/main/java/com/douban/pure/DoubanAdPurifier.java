package com.douban.pure;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

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
        hookRexxarAdActivity(xposed, cl);
        hookHomeHeaderAd(xposed, cl);
        hookNotificationVenueBanner(xposed, cl);
        hookThirdPartyAndSdkAds(xposed, cl);
        hookAdDurationAndTimeouts(xposed, cl);
        hookBetaUpdateAndRating(xposed, cl);
        hookUmeng(xposed, cl);
        hookViewGroupAddView(xposed);
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
                "com.douban.frodo.baseproject.ad.view.FeedAdItemFakeView",
                "com.douban.frodo.baseproject.ad.sdk.FeedAdItemSdkView",
                "com.douban.frodo.baseproject.ad.view.RecentTopicAdView",
                "com.douban.frodo.baseproject.ad.banner.FeedAdBannerView",
                "com.douban.frodo.baseproject.ad.photo.FeedAdPhotoView",
                "com.douban.frodo.baseproject.ad.photo.IncentiveAdFooter",
                "com.douban.frodo.baseproject.ad.interstitial.AdIntersView"
        };
        for (String adViewCls : adViewClasses) {
            ReflectUtils.hookAllMethods(xposed, adViewCls, cl, "setVisibility", chain -> {
                Context ctx = getContext(chain.getThisObject());
                if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true)) {
                    return chain.proceed(new Object[]{View.GONE});
                }
                return chain.proceed();
            });
            ReflectUtils.hookAllMethods(xposed, adViewCls, cl, "onMeasure", chain -> {
                Context ctx = getContext(chain.getThisObject());
                if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true)) {
                    Object obj = chain.getThisObject();
                    if (obj instanceof View) {
                        View v = (View) obj;
                        try {
                            Method setMD = View.class.getDeclaredMethod("setMeasuredDimension", int.class, int.class);
                            setMD.setAccessible(true);
                            setMD.invoke(v, 0, 0);
                        } catch (Throwable ignored) {
                        }
                    }
                    return null;
                }
                return chain.proceed();
            });
            ReflectUtils.hookAllMethods(xposed, adViewCls, cl, "onAttachedToWindow", chain -> {
                Context ctx = getContext(chain.getThisObject());
                if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true)) {
                    Object obj = chain.getThisObject();
                    if (obj instanceof View) {
                        View v = (View) obj;
                        v.setVisibility(View.GONE);
                        ViewGroup.LayoutParams lp = v.getLayoutParams();
                        if (lp != null) {
                            lp.width = 0;
                            lp.height = 0;
                            v.setLayoutParams(lp);
                        }
                    }
                }
                return chain.proceed();
            });

            String[] updateMethods = {"updateView", "updateFeedAd", "bind", "bindFeed", "populate",
                    "setAd", "setFeedAd", "setData", "init", "initView", "initAdBanner", "showAd", "continueLoopPlay$core_release"};
            for (String uMethod : updateMethods) {
                Class<?> clazz = ReflectUtils.findClass(adViewCls, cl);
                if (clazz != null) {
                    ReflectUtils.hookMethodWithTypeSafety(xposed, clazz, uMethod, null);
                }
            }
        }
        Log.i(TAG, "FeedAd hooks installed");
    }

    private static void hookBooleanAdMethod(XposedInterface xposed, String className,
                                            ClassLoader cl, String methodName, boolean blockedValue) {
        Class<?> type = ReflectUtils.findClass(className, cl);
        if (type == null) return;
        for (Method method : type.getDeclaredMethods()) {
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

    private static void hookRexxarAdActivity(XposedInterface xposed, ClassLoader cl) {
        String cls = "com.douban.frodo.struct2.RexxarAdActivity2";
        Class<?> clazz = ReflectUtils.findClass(cls, cl);
        if (clazz == null) return;
        ReflectUtils.hookMethodWithTypeSafety(xposed, clazz, "buildAdContainer", null);
    }

    private static void hookHomeHeaderAd(XposedInterface xposed, ClassLoader cl) {
        String modelCls = "com.douban.frodo.fragment.homeheader.HomeHeaderModel";
        Class<?> modelClass = ReflectUtils.findClass(modelCls, cl);
        if (modelClass != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, modelClass, "getHomeHeader", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, modelClass, "refreshHomeHeader", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, modelClass, "updateHomeHeader", null);
        }

        String netCls = "n5.e";
        Class<?> netClass = ReflectUtils.findClass(netCls, cl);
        if (netClass != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, netClass, "y", null);
        }
    }

    private static void hookNotificationVenueBanner(XposedInterface xposed, ClassLoader cl) {
        String[] bindingClasses = {
                "com.douban.frodo.databinding.ItemNotificationVenueViewBinding",
                "com.douban.frodo.databinding.ItemNotificationViewBinding",
                "com.douban.frodo.databinding.ItemNotificationViewLayoutDefaultBinding",
                "com.douban.frodo.databinding.ItemNotificationViewLayoutTvCalendarBinding"
        };
        for (String bCls : bindingClasses) {
            Class<?> clazz = ReflectUtils.findClass(bCls, cl);
            if (clazz != null) {
                ReflectUtils.hookAllMethods(xposed, clazz, "bind", chain -> {
                    Object res = chain.proceed();
                    try {
                        Method getRoot = clazz.getMethod("getRoot");
                        View root = (View) getRoot.invoke(res);
                        if (root != null) {
                            root.setVisibility(View.GONE);
                            ViewGroup.LayoutParams lp = root.getLayoutParams();
                            if (lp != null) {
                                lp.width = 0;
                                lp.height = 0;
                                root.setLayoutParams(lp);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return res;
                });
                ReflectUtils.hookAllMethods(xposed, clazz, "inflate", chain -> {
                    Object res = chain.proceed();
                    try {
                        Method getRoot = clazz.getMethod("getRoot");
                        View root = (View) getRoot.invoke(res);
                        if (root != null) {
                            root.setVisibility(View.GONE);
                            ViewGroup.LayoutParams lp = root.getLayoutParams();
                            if (lp != null) {
                                lp.width = 0;
                                lp.height = 0;
                                root.setLayoutParams(lp);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    return res;
                });
            }
        }
    }

    private static void hookThirdPartyAndSdkAds(XposedInterface xposed, ClassLoader cl) {
        // 1. ByteDance CSJ: Hook ad loaders on TTAdNative
        String ttAdNative = "com.bytedance.sdk.openadsdk.TTAdNative";
        Class<?> ttNativeCls = ReflectUtils.findClass(ttAdNative, cl);
        if (ttNativeCls != null) {
            String[] ttMethods = {"loadFeedAd", "loadSplashAd", "loadDrawFeedAd", "loadBannerExpressAd",
                    "loadNativeExpressAd", "loadStream", "loadRewardVideoAd", "loadFullScreenVideoAd"};
            for (String m : ttMethods) {
                ReflectUtils.hookMethodWithTypeSafety(xposed, ttNativeCls, m, null);
            }
        }

        // TTDelegateActivity
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

        // 2. Tencent GDT: Hook ad loaders
        Class<?> gdtAd = ReflectUtils.findClass("com.qq.e.ads.nativ.NativeUnifiedAD", cl);
        if (gdtAd != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtAd, "loadData", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtAd, "loadAD", null);
        }

        Class<?> gdtExpress = ReflectUtils.findClass("com.qq.e.ads.nativ.NativeExpressAD", cl);
        if (gdtExpress != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtExpress, "loadAD", null);
        }

        Class<?> gdtSplash = ReflectUtils.findClass("com.qq.e.ads.splash.SplashAD", cl);
        if (gdtSplash != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtSplash, "fetchAndShowIn", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtSplash, "fetchFullScreenAndShowIn", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtSplash, "fetchAdOnly", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtSplash, "showAd", null);
        }

        Class<?> gdtInters = ReflectUtils.findClass("com.qq.e.ads.interstitial2.UnifiedInterstitialAD", cl);
        if (gdtInters != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtInters, "loadAD", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtInters, "show", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtInters, "showAsPopupWindow", null);
        }

        Class<?> gdtReward = ReflectUtils.findClass("com.qq.e.ads.rewardvideo.RewardVideoAD", cl);
        if (gdtReward != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtReward, "loadAD", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, gdtReward, "showAD", null);
        }

        // 3. Jingdong Ad SDK (JAD)
        String[] jadClasses = {
                "com.jd.ad.sdk.JADBanner",
                "com.jd.ad.sdk.banner.JADBanner",
                "com.jd.ad.sdk.JADFeeds",
                "com.jd.ad.sdk.feed.JADFeed",
                "com.jd.ad.sdk.JADSplash",
                "com.jd.ad.sdk.splash.JADSplash",
                "com.jd.ad.sdk.JADInterstitial",
                "com.jd.ad.sdk.interstitial.JADInterstitial"
        };
        for (String jadCls : jadClasses) {
            Class<?> jc = ReflectUtils.findClass(jadCls, cl);
            if (jc != null) {
                ReflectUtils.hookMethodWithTypeSafety(xposed, jc, "loadAd", null);
            }
        }

        // 4. Baidu MobAds
        Class<?> baiduNative = ReflectUtils.findClass("com.baidu.mobads.sdk.api.BaiduNativeManager", cl);
        if (baiduNative != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, baiduNative, "loadFeedAd", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, baiduNative, "loadExpressAd", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, baiduNative, "loadNativeAd", null);
        }

        Class<?> baiduSplash = ReflectUtils.findClass("com.baidu.mobads.sdk.api.SplashAd", cl);
        if (baiduSplash != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, baiduSplash, "loadAndShow", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, baiduSplash, "load", null);
        }

        // 5. Douban Internal Ad Fetchers
        Class<?> sdkFetcher = ReflectUtils.findClass("com.douban.frodo.baseproject.ad.sdk.AbstractSdkFetcher", cl);
        if (sdkFetcher != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, sdkFetcher, "doFetch", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, sdkFetcher, "fetch", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, sdkFetcher, "fetchAd", null);
        }

        Class<?> intersMgr = ReflectUtils.findClass("com.douban.frodo.baseproject.ad.interstitial.AdIntersManager", cl);
        if (intersMgr != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, intersMgr, "show", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, intersMgr, "showAd", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, intersMgr, "showIntersCard", null);
        }

        Class<?> pullAd = ReflectUtils.findClass("com.douban.frodo.baseproject.pullad.PullAdContainer", cl);
        if (pullAd != null) {
            ReflectUtils.hookMethodWithTypeSafety(xposed, pullAd, "showAd", null);
            ReflectUtils.hookMethodWithTypeSafety(xposed, pullAd, "loadAd", null);
        }

        Class<?> subjectAd = ReflectUtils.findClass("com.douban.frodo.subject.view.SubjectAdHeader", cl);
        if (subjectAd != null) {
            ReflectUtils.hookAllMethods(xposed, subjectAd, "initView", chain -> {
                Object obj = chain.getThisObject();
                if (obj instanceof View) ((View) obj).setVisibility(View.GONE);
                return null;
            });
            ReflectUtils.hookAllMethods(xposed, subjectAd, "updateView", chain -> {
                Object obj = chain.getThisObject();
                if (obj instanceof View) ((View) obj).setVisibility(View.GONE);
                return null;
            });
        }

        Log.i(TAG, "Third-party and internal ad hooks installed");
    }

    private static void hookViewGroupAddView(XposedInterface xposed) {
        try {
            Method addViewMethod = ViewGroup.class.getDeclaredMethod(
                    "addView", View.class, int.class, ViewGroup.LayoutParams.class);
            xposed.hook(addViewMethod).intercept(chain -> {
                View child = (View) chain.getArg(0);
                if (child != null && DoubanLayoutPurifier.isAdView(child)) {
                    Context ctx = child.getContext();
                    if (PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true)) {
                        child.setVisibility(View.GONE);
                        ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) chain.getArg(2);
                        if (lp != null) {
                            lp.width = 0;
                            lp.height = 0;
                        }
                        return null;
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "ViewGroup.addView ad interceptor installed");
        } catch (Throwable t) {
            Log.w(TAG, "hook ViewGroup.addView failed: " + t);
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
        String adApi = "com.douban.frodo.network.AdApi";
        ReflectUtils.hookAllMethods(xposed, adApi, cl, "getSplashAd", chain -> null);
        ReflectUtils.hookAllMethods(xposed, adApi, cl, "requestSplashAd", chain -> null);

        Log.i(TAG, "Splash requestor hooks installed");
    }

    private static void hookSplashActivity(XposedInterface xposed, ClassLoader cl) {
        String splashCls = "com.douban.frodo.activity.SplashActivity";

        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "showSplashAdContainer", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = obj instanceof Activity ? (Context) obj : null;
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                return null;
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

        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "gotoMainActivity", chain -> {
            return chain.proceed();
        });

        ReflectUtils.hookAllMethods(xposed, splashCls, cl, "initSplashFragment", chain -> {
            Object obj = chain.getThisObject();
            Context ctx = obj instanceof Activity ? (Context) obj : null;
            if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                Activity act = (Activity) obj;
                try {
                    Method m = splashCls.equals(act.getClass().getName())
                            ? act.getClass().getDeclaredMethod("gotoMainActivity")
                            : ReflectUtils.findMethod(act.getClass(), "gotoMainActivity");
                    if (m != null) {
                        m.setAccessible(true);
                        m.invoke(act);
                        Log.i(TAG, "initSplashFragment redirected to gotoMainActivity");
                        return null;
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "gotoMainActivity reflection failed: " + t);
                }
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
            ReflectUtils.hookAllMethods(xposed, fragCls, cl, "onCreateView", chain -> {
                Object obj = chain.getThisObject();
                Context ctx = getContext(obj);
                if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                    return null;
                }
                return chain.proceed();
            });
            ReflectUtils.hookAllMethods(xposed, fragCls, cl, "onViewCreated", chain -> {
                Object obj = chain.getThisObject();
                Context ctx = getContext(obj);
                if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                    return null;
                }
                return chain.proceed();
            });
            ReflectUtils.hookAllMethods(xposed, fragCls, cl, "showSplashAd", chain -> {
                Object obj = chain.getThisObject();
                Context ctx = getContext(obj);
                if (PureSettings.getBoolean(ctx, PureSettings.KEY_SKIP_SPLASH, true)) {
                    return null;
                }
                return chain.proceed();
            });
        }
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

        ReflectUtils.hookAllMethods(xposed, "com.douban.ad.widget.CountDownView", cl, "setDuration", chain -> {
            return chain.proceed(new Object[]{0});
        });
        ReflectUtils.hookAllMethods(xposed, "com.douban.ad.widget.CountDownView", cl, "startCountDown", chain -> null);

        Log.i(TAG, "Ad duration and countdown hooks installed");
    }

    private static void hookBetaUpdateAndRating(XposedInterface xposed, ClassLoader cl) {
        // 1. Beta APK Check & Dialog
        String betaCheck = "com.douban.frodo.update.BetaApkChecker";
        ReflectUtils.hookAllMethods(xposed, betaCheck, cl, "check", chain -> null);
        ReflectUtils.hookAllMethods(xposed, betaCheck, cl, "checkUpdate", chain -> null);
        ReflectUtils.hookAllMethods(xposed, betaCheck, cl, "showBetaApkDialog", chain -> null);

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

        // 2. Beta APK Install Activity
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

        // 3. Block Skynet Rating
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
