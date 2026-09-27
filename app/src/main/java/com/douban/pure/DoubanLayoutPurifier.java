package com.douban.pure;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

import androidx.drawerlayout.widget.DrawerLayout;
import io.github.libxposed.api.XposedInterface;

public final class DoubanLayoutPurifier {
    private static final String TAG = "DoubanPure";

    private DoubanLayoutPurifier() {
    }

    public static void install(XposedInterface xposed, ClassLoader cl) {
        preventTabStripInfiniteLoop(xposed, cl);
        hookTabStripPopulate(xposed, cl);
        hookViewPager(xposed, cl);
        hookHackViewPagerSwiping(xposed, cl);
        hookTargetViewVisibility(xposed);
    }

    private static void preventTabStripInfiniteLoop(XposedInterface xposed, ClassLoader cl) {
        ReflectUtils.hookAllMethods(xposed, "com.astuetz.PagerSlidingTabStrip$d", cl, "onGlobalLayout", chain -> null);

        ReflectUtils.hookAllMethods(xposed, "com.astuetz.PagerSlidingTabStrip", cl, "resizeContentWidth", chain -> {
            Object strip = chain.getThisObject();
            try {
                Object sObj = ReflectUtils.getField(strip, "S");
                if (sObj instanceof Integer && ((Integer) sObj) <= 0) {
                    ReflectUtils.setField(strip, "S", 1000);
                }
            } catch (Throwable ignored) {
            }
            return chain.proceed();
        });
        Log.i(TAG, "PagerSlidingTabStrip loop prevention installed");
    }

    private static void hookTabStripPopulate(XposedInterface xposed, ClassLoader cl) {
        ReflectUtils.hookAllMethods(xposed, "com.astuetz.PagerSlidingTabStrip", cl, "notifyDataSetChanged", chain -> {
            Object res = chain.proceed();
            try {
                Object strip = chain.getThisObject();
                if (strip instanceof ViewGroup) {
                    purgeStrip((ViewGroup) strip);
                }
            } catch (Throwable t) {
                Log.w(TAG, "notifyDataSetChanged hook error: " + t);
            }
            return res;
        });

        ReflectUtils.hookAllMethods(xposed, "com.astuetz.PagerSlidingTabStrip", cl, "E", chain -> {
            Object res = chain.proceed();
            try {
                int pos = (int) chain.getArg(0);
                CharSequence title = (CharSequence) chain.getArg(1);
                Object strip = chain.getThisObject();
                if (strip instanceof View) {
                    Context ctx = ((View) strip).getContext();
                    boolean hideDongtai = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_DONGTAI, true);
                    if (pos == 0 && hideDongtai && title != null && title.toString().contains("动态")) {
                        ViewGroup vg = (ViewGroup) strip;
                        if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof LinearLayout) {
                            LinearLayout ll = (LinearLayout) vg.getChildAt(0);
                            if (ll.getChildCount() > 0) {
                                View tab0 = ll.getChildAt(0);
                                tab0.setVisibility(View.GONE);
                                tab0.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.0f));
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "E hook error: " + t);
            }
            return res;
        });
        Log.i(TAG, "PagerSlidingTabStrip populate hooks installed");
    }

    private static void hookTargetViewVisibility(XposedInterface xposed) {
        try {
            Method setVisibility = View.class.getDeclaredMethod("setVisibility", int.class);
            xposed.hook(setVisibility).intercept(chain -> {
                View view = (View) chain.getThisObject();
                int id = view.getId();
                Context ctx = view.getContext();

                if (id == 0x7f0a07cb || id == 0x7f0a07cc) {
                    // Search page: 热门话题 title & topics list
                    boolean hideTopics = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS, true);
                    if (hideTopics) {
                        return chain.proceed(new Object[]{View.GONE});
                    }
                } else if (id == 0x7f0a02e1) {
                    // Homepage: 发布按钮 (笔)
                    boolean hidePost = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_HOME_POST_BTN, true);
                    if (hidePost) {
                        return chain.proceed(new Object[]{View.GONE});
                    }
                } else if (id == 0x7f0a08c6) {
                    // Homepage: 宠物/活动横幅
                    boolean hidePets = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_PETS, true);
                    if (hidePets) {
                        return chain.proceed(new Object[]{View.GONE});
                    }
                } else if (id == 0x7f0a0d6c) {
                    // Homepage: 耳机/播客图标
                    boolean hidePodcast = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_PODCAST, true);
                    if (hidePodcast) {
                        return chain.proceed(new Object[]{View.GONE});
                    }
                }
                return chain.proceed();
            });
            Log.i(TAG, "Target view setVisibility hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "hookTargetViewVisibility failed: " + t);
        }
    }

    private static void hookViewPager(XposedInterface xposed, ClassLoader cl) {
        Class<?> viewPagerClass = ReflectUtils.findClass("androidx.viewpager.widget.ViewPager", cl);
        if (viewPagerClass == null) {
            viewPagerClass = ReflectUtils.findClass("com.douban.frodo.baseproject.view.HackViewPager", cl);
        }
        if (viewPagerClass != null) {
            try {
                Method m1 = ReflectUtils.findMethod(viewPagerClass, "setCurrentItem", int.class, boolean.class);
                if (m1 != null) {
                    xposed.hook(m1).intercept(chain -> {
                        Object pager = chain.getThisObject();
                        if (isHomeHackViewPager(pager)) {
                            Context ctx = ((View) pager).getContext();
                            boolean hideDongtai = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_DONGTAI, true);
                            int item = (int) chain.getArg(0);
                            if (item == 0 && hideDongtai) {
                                return chain.proceed(new Object[]{1, chain.getArg(1)});
                            }
                        }
                        return chain.proceed();
                    });
                }

                Method m2 = ReflectUtils.findMethod(viewPagerClass, "setCurrentItem", int.class);
                if (m2 != null) {
                    xposed.hook(m2).intercept(chain -> {
                        Object pager = chain.getThisObject();
                        if (isHomeHackViewPager(pager)) {
                            Context ctx = ((View) pager).getContext();
                            boolean hideDongtai = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_DONGTAI, true);
                            int item = (int) chain.getArg(0);
                            if (item == 0 && hideDongtai) {
                                return chain.proceed(new Object[]{1});
                            }
                        }
                        return chain.proceed();
                    });
                }
                Log.i(TAG, "ViewPager.setCurrentItem hooks installed");
            } catch (Throwable t) {
                Log.w(TAG, "hookViewPager failed: " + t);
            }
        }
    }

    private static boolean isHomeHackViewPager(Object pager) {
        if (pager == null) return false;
        return pager.getClass().getName().contains("HackViewPager");
    }

    private static void hookHackViewPagerSwiping(XposedInterface xposed, ClassLoader cl) {
        Class<?> hackViewPagerClass = ReflectUtils.findClass("com.douban.frodo.baseproject.view.HackViewPager", cl);
        if (hackViewPagerClass != null) {
            try {
                Method onIntercept = ReflectUtils.findMethod(hackViewPagerClass, "onInterceptTouchEvent", MotionEvent.class);
                if (onIntercept != null) {
                    xposed.hook(onIntercept).intercept(chain -> {
                        Object pager = chain.getThisObject();
                        if (pager instanceof View) {
                            boolean hideDongtai = PureSettings.getBoolean(((View) pager).getContext(), PureSettings.KEY_HIDE_TOP_DONGTAI, true);
                            if (hideDongtai) {
                                return false;
                            }
                        }
                        return chain.proceed();
                    });
                }

                Method onTouch = ReflectUtils.findMethod(hackViewPagerClass, "onTouchEvent", MotionEvent.class);
                if (onTouch != null) {
                    xposed.hook(onTouch).intercept(chain -> {
                        Object pager = chain.getThisObject();
                        if (pager instanceof View) {
                            boolean hideDongtai = PureSettings.getBoolean(((View) pager).getContext(), PureSettings.KEY_HIDE_TOP_DONGTAI, true);
                            if (hideDongtai) {
                                return false;
                            }
                        }
                        return chain.proceed();
                    });
                }
                Log.i(TAG, "HackViewPager swiping hooks installed");
            } catch (Throwable t) {
                Log.w(TAG, "hookHackViewPagerSwiping failed: " + t);
            }
        }
    }

    public static void onActivityResumed(Activity activity) {
        if (activity == null) return;
        try {
            View decor = activity.getWindow().getDecorView();
            decor.post(() -> purgeViews(decor));
            decor.postDelayed(() -> purgeViews(decor), 300);
            decor.postDelayed(() -> purgeViews(decor), 800);
        } catch (Throwable t) {
            Log.w(TAG, "onActivityResumed layout purge failed: " + t);
        }
    }

    public static void purgeViews(View root) {
        if (root == null) return;
        traverseAndPurge(root, 0);
    }

    public static void purgeStrip(ViewGroup strip) {
        if (strip == null) return;
        Context ctx = strip.getContext();
        if (strip.getChildCount() > 0 && strip.getChildAt(0) instanceof LinearLayout) {
            LinearLayout container = (LinearLayout) strip.getChildAt(0);
            int childCount = container.getChildCount();

            // Bottom bar has 5 tabs (0: 首页, 1: 书影音, 2: 小组, 3: 市集, 4: 我)
            if (childCount == 5) {
                boolean hideSubject = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TAB_SUBJECT, true);
                boolean hideGroup = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TAB_GROUP, true);
                boolean hideShiji = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TAB_SHIJI, true);

                for (int i = 0; i < 5; i++) {
                    View child = container.getChildAt(i);
                    boolean hide = (i == 1 && hideSubject)
                            || (i == 2 && hideGroup)
                            || (i == 3 && hideShiji);
                    if (hide) {
                        child.setVisibility(View.GONE);
                        child.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.0f));
                    } else {
                        child.setVisibility(View.VISIBLE);
                        child.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));
                    }
                }
            } else if (childCount == 2) {
                // Top tab strip (0: 动态, 1: 推荐/精选)
                boolean hideDongtai = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_DONGTAI, true);
                View tab0 = container.getChildAt(0);
                View tab1 = container.getChildAt(1);

                if (hideDongtai) {
                    tab0.setVisibility(View.GONE);
                    tab0.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.0f));

                    tab1.setVisibility(View.VISIBLE);
                    tab1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    ReflectUtils.setIntSilent(strip, "f25984k", 1);
                    strip.invalidate();
                } else {
                    tab0.setVisibility(View.VISIBLE);
                    tab0.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    tab1.setVisibility(View.VISIBLE);
                    tab1.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }
            }
        }
    }

    private static void traverseAndPurge(View view, int depth) {
        if (view == null || depth > 25) return;
        Context ctx = view.getContext();
        String className = view.getClass().getName();

        // 1. DrawerLayout listener injection
        if (className.contains("DrawerLayout")) {
            try {
                if (view instanceof DrawerLayout) {
                    DrawerLayout drawer = (DrawerLayout) view;
                    if (drawer.getTag(0x7f0a0621) == null) {
                        drawer.setTag(0x7f0a0621, Boolean.TRUE);
                        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                            @Override
                            public void onDrawerSlide(View drawerView, float slideOffset) {
                                purgeViews(drawerView);
                                SidebarInjector.inject(drawerView);
                            }

                            @Override
                            public void onDrawerOpened(View drawerView) {
                                purgeViews(drawerView);
                                SidebarInjector.inject(drawerView);
                            }
                        });
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "DrawerLayout hook warning: " + t);
            }
        }

        // 2. PagerSlidingTabStrip (Bottom nav and top tab)
        if (className.contains("PagerSlidingTabStrip") && (view instanceof ViewGroup)) {
            purgeStrip((ViewGroup) view);
        }

        // 3. Lock HackViewPager to 1 (推荐) if 动态 is hidden
        if (className.contains("HackViewPager") && (view instanceof ViewGroup)) {
            boolean hideDongtai = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_DONGTAI, true);
            if (hideDongtai) {
                try {
                    Method getCurrentItem = view.getClass().getMethod("getCurrentItem");
                    int current = (int) getCurrentItem.invoke(view);
                    if (current == 0) {
                        Method setCurrentItem = view.getClass().getMethod("setCurrentItem", int.class, boolean.class);
                        setCurrentItem.invoke(view, 1, false);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // 4. SlideMenuView
        if (className.contains("SlideMenuView") || className.contains("silde_menu")) {
            SidebarInjector.inject(view);
        }

        // 5. Inspect ID based elements
        int id = view.getId();
        if (id != View.NO_ID) {
            String entryName = "";
            try {
                entryName = view.getResources().getResourceEntryName(id);
            } catch (Throwable ignored) {
            }

            // Top right Podcast (耳机) icon
            if ("menu_podcast".equals(entryName) || id == 0x7f0a0d6c) {
                boolean hidePodcast = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_PODCAST, true);
                view.setVisibility(hidePodcast ? View.GONE : View.VISIBLE);
            }
            // Header right banner (宠物/活动挂件)
            else if ("header_right_banner".equals(entryName) || id == 0x7f0a08c6) {
                boolean hidePets = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_PETS, true);
                view.setVisibility(hidePets ? View.GONE : View.VISIBLE);
                if (hidePets) {
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    if (lp != null) {
                        lp.width = 0;
                        lp.height = 0;
                        view.setLayoutParams(lp);
                    }
                }
            }
            // Homepage right bottom Post button (铅笔浮动按钮)
            else if ("btn_post".equals(entryName) || id == 0x7f0a02e1) {
                boolean hidePost = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_HOME_POST_BTN, true);
                view.setVisibility(hidePost ? View.GONE : View.VISIBLE);
            }
            // Sidebar "我的背包"
            else if ("my_beans".equals(entryName)) {
                boolean hideBag = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_BAG, true);
                view.setVisibility(hideBag ? View.GONE : View.VISIBLE);
            }
            // Sidebar bottom service grid (订单、购物车、钱包、阅读等)
            else if ("grid_entry".equals(entryName) || id == 0x7f0a07ed) {
                boolean hideServices = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_SERVICES, true);
                view.setVisibility(hideServices ? View.GONE : View.VISIBLE);
            }
            // Search page: 热门话题 title & RecyclerView
            else if ("gallery_topic_title".equals(entryName) || "gallery_topics".equals(entryName) || id == 0x7f0a07cb || id == 0x7f0a07cc) {
                boolean hideTopics = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS, true);
                view.setVisibility(hideTopics ? View.GONE : View.VISIBLE);
            }
            // Search page: 实时热门书影音/讨论 ViewPager & indicator (默认保留)
            else if ("vpSubject".equals(entryName) || "pageIndicatorView".equals(entryName) || id == 0x7f0a1978 || id == 0x7f0a0f6d) {
                boolean hideTrends = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SEARCH_TRENDS, false);
                view.setVisibility(hideTrends ? View.GONE : View.VISIBLE);
            }
        }

        // 6. Sidebar list entries (SlideMenuListEntryView)
        if (className.contains("SlideMenuListEntryView") && (view instanceof ViewGroup)) {
            purgeSidebarEntry((ViewGroup) view, ctx);
        }

        // 7. Divider lines in sidebar
        if (view instanceof ImageView && !(className.contains("CircleImageView"))) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null && lp.height > 0 && lp.height <= 5) {
                boolean hideMinor = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_MINOR, true);
                if (hideMinor) {
                    view.setVisibility(View.GONE);
                }
            }
        }

        // 8. License info at sidebar bottom
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            if (cs != null && cs.toString().contains("证照信息")) {
                boolean hideLicense = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_LICENSE, true);
                if (hideLicense) {
                    ViewParent parent = view.getParent();
                    if (parent instanceof View) {
                        ((View) parent).setVisibility(View.GONE);
                    } else {
                        view.setVisibility(View.GONE);
                    }
                }
            }
        }

        // Recurse into children
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                traverseAndPurge(group.getChildAt(i), depth + 1);
            }
        }
    }

    private static void purgeSidebarEntry(ViewGroup entryView, Context ctx) {
        String allText = extractAllText(entryView);
        if (allText == null || allText.isEmpty()) return;

        // Never hide our injected entry or Settings
        if (allText.contains("DoubanPure") || allText.equals("设置") || allText.startsWith("设置")) {
            entryView.setVisibility(View.VISIBLE);
            return;
        }

        boolean hide = false;
        if (allText.contains("未成年")) {
            hide = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_MINOR, true);
        } else if (allText.contains("草稿")) {
            hide = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_DRAFTS, true);
        } else if (allText.contains("签到")) {
            hide = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_CHECKIN, true);
        } else if (allText.contains("帮助") || allText.contains("反馈")) {
            hide = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_FEEDBACK, true);
        } else if (allText.contains("社区管理")) {
            hide = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_COMMUNITY, true);
        } else if (allText.contains("收藏") || allText.contains("豆列")) {
            hide = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_FAV, false);
        } else if (allText.contains("关注")) {
            hide = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_FOLLOW, false);
        } else if (allText.contains("历史")) {
            hide = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SIDE_HISTORY, false);
        }

        if (hide) {
            entryView.setVisibility(View.GONE);
            ViewGroup.LayoutParams lp = entryView.getLayoutParams();
            if (lp != null) {
                lp.width = 0;
                lp.height = 0;
                entryView.setLayoutParams(lp);
            }
            if (entryView.getChildCount() > 0) {
                entryView.getChildAt(0).setVisibility(View.GONE);
            }
        } else {
            entryView.setVisibility(View.VISIBLE);
        }
    }

    private static String extractAllText(View view) {
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            return cs == null ? "" : cs.toString();
        }
        if (view instanceof ViewGroup) {
            StringBuilder sb = new StringBuilder();
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                sb.append(extractAllText(vg.getChildAt(i)));
            }
            return sb.toString();
        }
        return "";
    }
}

