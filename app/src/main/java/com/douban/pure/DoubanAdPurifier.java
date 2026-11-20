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
                    hookGated(xposed, clazz, uMethod, PureSettings.KEY_BLOCK_FEED_AD);
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
        hookGated(xposed, clazz, "buildAdContainer", PureSettings.KEY_BLOCK_FEED_AD);
    }

    private static void hookHomeHeaderAd(XposedInterface xposed, ClassLoader cl) {
        String modelCls = "com.douban.frodo.fragment.homeheader.HomeHeaderModel";
        Class<?> modelClass = ReflectUtils.findClass(modelCls, cl);
        if (modelClass != null) {
            hookGated(xposed, modelClass, "getHomeHeader", PureSettings.KEY_BLOCK_FEED_AD);
            hookGated(xposed, modelClass, "refreshHomeHeader", PureSettings.KEY_BLOCK_FEED_AD);
            hookGated(xposed, modelClass, "updateHomeHeader", PureSettings.KEY_BLOCK_FEED_AD);
        }

        String netCls = "n5.e";
        Class<?> netClass = ReflectUtils.findClass(netCls, cl);
        if (netClass != null) {
            hookGated(xposed, netClass, "y", PureSettings.KEY_BLOCK_FEED_AD);
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
                    if (!PureSettings.getBoolean(null, PureSettings.KEY_BLOCK_FEED_AD, true)) return res;
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
                    if (!PureSettings.getBoolean(null, PureSettings.KEY_BLOCK_FEED_AD, true)) return res;
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
        Class<?> tt = ReflectUtils.findClass("com.bytedance.sdk.openadsdk.TTAdNative", cl);
        hookSplashSdkMethod(xposed, tt, "loadSplashAd");
        for (String m : new String[]{"loadFeedAd", "loadDrawFeedAd"})
            hookGated(xposed, tt, m, PureSettings.KEY_BLOCK_FEED_AD);
        Class<?> gdtSplash = ReflectUtils.findClass("com.qq.e.ads.splash.SplashAD", cl);
        if (gdtSplash != null) {
            hookSplashSdkMethod(xposed, gdtSplash, "fetchAndShowIn");
            hookSplashSdkMethod(xposed, gdtSplash, "fetchFullScreenAndShowIn");
            hookSplashSdkMethod(xposed, gdtSplash, "fetchAdOnly");
            hookSplashSdkMethod(xposed, gdtSplash, "showAd");
        }

        for (String name : new String[]{"com.jd.ad.sdk.JADSplash", "com.jd.ad.sdk.splash.JADSplash"})
            hookSplashSdkMethod(xposed, ReflectUtils.findClass(name, cl), "loadAd");
        for (String name : new String[]{"com.jd.ad.sdk.JADFeeds", "com.jd.ad.sdk.feed.JADFeed"})
            hookGated(xposed, ReflectUtils.findClass(name, cl), "loadAd", PureSettings.KEY_BLOCK_FEED_AD);
        hookGated(xposed, ReflectUtils.findClass("com.baidu.mobads.sdk.api.BaiduNativeManager", cl), "loadFeedAd", PureSettings.KEY_BLOCK_FEED_AD);
        Class<?> baiduSplash = ReflectUtils.findClass("com.baidu.mobads.sdk.api.SplashAd", cl);
        if (baiduSplash != null) {
            hookSplashSdkMethod(xposed, baiduSplash, "loadAndShow");
            hookSplashSdkMethod(xposed, baiduSplash, "load");
        }

        // 5. Douban Internal Ad Fetchers
        // Shared fetchers, delegates, rewards and interstitials are not feed-specific.
        Class<?> subjectAd = ReflectUtils.findClass("com.douban.frodo.subject.view.SubjectAdHeader", cl);
        if (subjectAd != null) {
            ReflectUtils.hookAllMethods(xposed, subjectAd, "initView", chain -> {
                if (!PureSettings.getBoolean(getContext(chain.getThisObject()), PureSettings.KEY_BLOCK_FEED_AD, true)) return chain.proceed();
                Object obj = chain.getThisObject();
                if (obj instanceof View) ((View) obj).setVisibility(View.GONE);
                return null;
            });
            ReflectUtils.hookAllMethods(xposed, subjectAd, "updateView", chain -> {
                if (!PureSettings.getBoolean(getContext(chain.getThisObject()), PureSettings.KEY_BLOCK_FEED_AD, true)) return chain.proceed();
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

        // 只有开启「跳过开屏广告」时才阻断请求；关闭时必须完整放行，否则开屏永远不会回调，
        // 首页内容也不会装载（表现为顶栏/底栏已绘制但内容区空白）。
        String[] requestorMethods = {"getRequestTimeout", "getBidTimeout", "request", "fetch",
                "fetchAd", "requestSplashAd", "loadSplashAd", "showAd", "showSplashAd", "showSplash"};
        for (String reqCls : requestorClasses) {
            for (String m : requestorMethods) {
                hookSplashTypedMethod(xposed, reqCls, cl, m);
            }
        }

        // SplashAdShowUtils
        hookSplashTypedMethod(xposed, "com.douban.frodo.splash.SplashAdShowUtils", cl, "showSplashAd");

        // AdApi splash request
        String adApi = "com.douban.frodo.network.AdApi";
        hookSplashTypedMethod(xposed, adApi, cl, "getSplashAd");
        hookSplashTypedMethod(xposed, adApi, cl, "requestSplashAd");

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
        // 倒计时/时长为 0 会导致开屏广告永远等不到「播放结束」回调，
        // 因此这些改写必须跟随「跳过开屏广告」开关，关闭时保持宿主原值。
        String adCls = "com.douban.frodo.baseproject.ad.model.DoubanAd";
        ReflectUtils.hookAllMethods(xposed, adCls, cl, "getDuration", chain -> {
            if (skipSplash(chain.getThisObject())) return 0;
            return chain.proceed();
        });
        ReflectUtils.hookAllMethods(xposed, adCls, cl, "getVideoSkipDelay", chain -> {
            if (skipSplash(chain.getThisObject())) return 0;
            return chain.proceed();
        });

        ReflectUtils.hookAllMethods(xposed, "com.douban.ad.p", cl, "handleMessage", chain -> {
            if (!skipSplash(chain.getThisObject())) {
                return chain.proceed();
            }
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
            if (skipSplash(chain.getThisObject())) {
                return chain.proceed(new Object[]{0});
            }
            return chain.proceed();
        });
        ReflectUtils.hookAllMethods(xposed, "com.douban.ad.widget.CountDownView", cl, "startCountDown", chain -> {
            if (skipSplash(chain.getThisObject())) return null;
            return chain.proceed();
        });

        Log.i(TAG, "Ad duration and countdown hooks installed");
    }

    private static void hookBetaUpdateAndRating(XposedInterface xposed, ClassLoader cl) {
        // 1. Beta APK Check & Dialog
        String betaCheck = "com.douban.frodo.update.BetaApkChecker";
        hookGated(xposed, ReflectUtils.findClass(betaCheck, cl), "check", PureSettings.KEY_BLOCK_BETA_UPDATE);
        hookGated(xposed, ReflectUtils.findClass(betaCheck, cl), "checkUpdate", PureSettings.KEY_BLOCK_BETA_UPDATE);
        hookGated(xposed, ReflectUtils.findClass(betaCheck, cl), "showBetaApkDialog", PureSettings.KEY_BLOCK_BETA_UPDATE);

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

        // 4. Intercept startActivity that launches Beta dialog or upgrade activities
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
        hookGated(xposed, ReflectUtils.findClass(mob, cl), "onEventValue", PureSettings.KEY_BLOCK_UMENG);
        hookGated(xposed, ReflectUtils.findClass(mob, cl), "onPageStart", PureSettings.KEY_BLOCK_UMENG);
        hookGated(xposed, ReflectUtils.findClass(mob, cl), "onPageEnd", PureSettings.KEY_BLOCK_UMENG);
        hookGated(xposed, ReflectUtils.findClass(mob, cl), "onResume", PureSettings.KEY_BLOCK_UMENG);
        hookGated(xposed, ReflectUtils.findClass(mob, cl), "onPause", PureSettings.KEY_BLOCK_UMENG);

        String crash = "com.umeng.umcrash.UMCrash";
        hookGated(xposed, ReflectUtils.findClass(crash, cl), "init", PureSettings.KEY_BLOCK_UMENG);
        hookGated(xposed, ReflectUtils.findClass(crash, cl), "initConfig", PureSettings.KEY_BLOCK_UMENG);
        hookGated(xposed, ReflectUtils.findClass(crash, cl), "generateCustomLog", PureSettings.KEY_BLOCK_UMENG);

        String dispatch = "com.umeng.commonsdk.framework.UMWorkDispatch";
        hookGated(xposed, ReflectUtils.findClass(dispatch, cl), "sendEvent", PureSettings.KEY_BLOCK_UMENG);
        hookGated(xposed, ReflectUtils.findClass(dispatch, cl), "sendEventEx", PureSettings.KEY_BLOCK_UMENG);
        hookGated(xposed, ReflectUtils.findClass(dispatch, cl), "sendEventInternal", PureSettings.KEY_BLOCK_UMENG);
    }

    /** 只拦截开屏类 SDK 调用：跟随「跳过开屏广告」开关，关闭时完整放行。 */
    private static void hookSplashSdkMethod(XposedInterface xposed, Class<?> clazz, String methodName) {
        hookSplashTypedMethod(xposed, clazz, methodName);
    }

    private static void hookSplashTypedMethod(XposedInterface xposed, String className,
                                               ClassLoader cl, String methodName) {
        hookSplashTypedMethod(xposed, ReflectUtils.findClass(className, cl), methodName);
    }

    /**
     * 按原方法返回类型给出「空值」，避免把 null/Integer 塞给 void 之外的原始类型导致崩溃。
     * 关闭「跳过开屏广告」时一律放行宿主原实现。
     */
    private static void hookSplashTypedMethod(XposedInterface xposed, Class<?> clazz, String methodName) {
        hookGated(xposed, clazz, methodName, PureSettings.KEY_SKIP_SPLASH);
    }

    private static void hookGated(XposedInterface xposed, Class<?> clazz, String methodName, String key) {
        if (clazz == null || methodName == null) return;
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                if (!methodName.equals(m.getName())) continue;
                m.setAccessible(true);
                final Class<?> returnType = m.getReturnType();
                xposed.hook(m).intercept(chain -> {
                    if (!PureSettings.getBoolean(getContext(chain.getThisObject()), key, true)) return chain.proceed();
                    return emptyValueFor(returnType);
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "hookSplashTypedMethod failed: " + clazz.getName() + "#" + methodName + " - " + t);
        }
    }

    private static Object emptyValueFor(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    /**
     * 白屏兜底：关闭「跳过开屏广告」时走的是宿主原生开屏流程，若广告长时间拉不到，
     * 宿主可能一直停在只有顶栏/底栏的空壳界面上。这里在回前台 8 秒后检查首页内容
     * 容器是否仍为空，为空则主动触发一次 gotoMainActivity，避免永久白屏。
     */
    public static void maybeRecoverBlankHome(Activity act) {
        if (act == null || act.isFinishing() || act.isDestroyed()) return;
        if (!"com.douban.frodo.activity.SplashActivity".equals(act.getClass().getName())) return;
        // 开启「跳过开屏广告」时模块已强制直达主界面，无需兜底
        if (PureSettings.getBoolean(act, PureSettings.KEY_SKIP_SPLASH, true)) return;
        View decor;
        try {
            decor = act.getWindow() == null ? null : act.getWindow().getDecorView();
        } catch (Throwable t) {
            return;
        }
        if (decor == null) return;

        decor.postDelayed(() -> {
            try {
                if (act.isFinishing() || act.isDestroyed() || !act.hasWindowFocus()) return;
                View pager = findViewByEntryName(decor, "view_pager", 0);
                if (pager instanceof ViewGroup && ((ViewGroup) pager).getChildCount() == 0) {
                    Method gotoMain = ReflectUtils.findMethod(act.getClass(), "gotoMainActivity");
                    if (gotoMain != null) {
                        gotoMain.setAccessible(true);
                        gotoMain.invoke(act);
                        Log.w(TAG, "Home content empty after resume, fallback to gotoMainActivity");
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "blank home fallback failed: " + t);
            }
        }, 8000);
    }

    private static View findViewByEntryName(View root, String entryName, int depth) {
        if (root == null || depth > 20) return null;
        int id = root.getId();
        if (id != View.NO_ID) {
            try {
                if (entryName.equals(root.getResources().getResourceEntryName(id))) return root;
            } catch (Throwable ignored) {
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewByEntryName(group.getChildAt(i), entryName, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 「跳过开屏广告」开关；拿不到 View/Activity 上下文时回退到宿主 Application。 */
    private static boolean skipSplash(Object thisObject) {
        return PureSettings.getBoolean(getContext(thisObject), PureSettings.KEY_SKIP_SPLASH, true);
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
