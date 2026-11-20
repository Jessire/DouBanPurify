package com.douban.pure;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;

import java.util.WeakHashMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
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
        SearchLanding.observeInflation(xposed, cl);
        SearchRankLimit.install(xposed, cl);
        SearchNativeBoards.install(xposed, cl);
        SidebarInjector.install(xposed, cl);
    }

    private static void preventTabStripInfiniteLoop(XposedInterface xposed, ClassLoader cl) {
        ReflectUtils.hookAllMethods(xposed, "com.astuetz.PagerSlidingTabStrip$d", cl, "onGlobalLayout", chain -> {
            // Only suppress the listener for the home strips whose child sizes we change.
            Object listener = chain.getThisObject();
            if (listener != null) {
                for (Field field : listener.getClass().getDeclaredFields()) {
                    if (!ViewGroup.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true);
                        Object owner = field.get(listener);
                        if (owner instanceof ViewGroup && isHomeStrip((ViewGroup) owner)) return null;
                    } catch (ReflectiveOperationException ignored) {
                    }
                }
            }
            return chain.proceed();
        });

        ReflectUtils.hookAllMethods(xposed, "com.astuetz.PagerSlidingTabStrip", cl, "resizeContentWidth", chain -> {
            Object strip = chain.getThisObject();
            if (!(strip instanceof ViewGroup) || !isHomeStrip((ViewGroup) strip)) {
                return chain.proceed();
            }
            if (isHomeBottomStrip((ViewGroup) strip) && chain.getExecutable() instanceof Method
                    && ((Method) chain.getExecutable()).getReturnType() == void.class
                    && ((Method) chain.getExecutable()).getParameterTypes().length == 0) {
                purgeStrip((ViewGroup) strip);
                return null;
            }
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
                if (strip instanceof ViewGroup && isHomeTopStrip((ViewGroup) strip)) {
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
                String entryName = entryName(view);
                int requested = (Integer) chain.getArg(0);
                int sidebar = sidebarHostVisibility(view, entryName, requested);
                if (sidebar != requested) return chain.proceed(new Object[]{sidebar});
                if (("vpSubject".equals(entryName) || "pageIndicatorView".equals(entryName)) && SearchLanding.inLanding(view))
                    return chain.proceed(new Object[]{SearchLanding.onVisibility(view, (Integer) chain.getArgs().get(0), PureSettings.getBoolean(view.getContext(), PureSettings.KEY_HIDE_SEARCH_TRENDS, false))});
                Context ctx = view.getContext();

                if (("gallery_topic_title".equals(entryName) || "gallery_topics".equals(entryName)) && SearchLanding.inLanding(view)) {
                    boolean hideTopics = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS, true);
                    if (hideTopics) {
                        return chain.proceed(new Object[]{View.GONE});
                    }
                } else if ("btn_post".equals(entryName) && isHomeView(view)) {
                    boolean hidePost = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_HOME_POST_BTN, true);
                    if (hidePost) {
                        return chain.proceed(new Object[]{View.GONE});
                    }
                } else if ("notification_container".equals(entryName) || "venue_bg".equals(entryName)) {
                    boolean hideBanner = PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true);
                    if (hideBanner) {
                        ViewGroup.LayoutParams lp = view.getLayoutParams();
                        if (lp != null) {
                            lp.width = 0;
                            lp.height = 0;
                            view.setLayoutParams(lp);
                        }
                        return chain.proceed(new Object[]{View.GONE});
                    }
                } else if ("header_right_banner".equals(entryName)) {
                    boolean hidePets = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_PETS, true);
                    if (hidePets) {
                        return chain.proceed(new Object[]{View.GONE});
                    }
                } else if ("menu_podcast".equals(entryName)) {
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

    static String entryName(View view) {
        int id = view.getId();
        if (id <= 0 || (id >>> 24) == 0) return "";
        try { return view.getResources().getResourceEntryName(id); }
        catch (android.content.res.Resources.NotFoundException ignored) { return ""; }
    }

    private static boolean isHomeView(View view) {
        Context context = view.getContext();
        // Views may use a themed wrapper rather than the Activity directly.
        for (int i = 0; context != null && i < 16; i++) {
            if (context instanceof Activity) {
                return "com.douban.frodo.activity.SplashActivity".equals(context.getClass().getName());
            }
            if (!(context instanceof ContextWrapper)) return false;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return false;
            context = base;
        }
        return false;
    }

    private static boolean isHomeTopStrip(ViewGroup strip) {
        return isHomeView(strip) && "tab_layout".equals(entryName(strip));
    }

    private static boolean isHomeBottomStrip(ViewGroup strip) {
        if (!isHomeView(strip) || !"tab_strip".equals(entryName(strip))
                || strip.getChildCount() == 0 || !(strip.getChildAt(0) instanceof LinearLayout)) return false;
        ViewGroup tabs = (ViewGroup) strip.getChildAt(0);
        if (tabs.getChildCount() != 5) return false;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            if (!"com.douban.frodo.view.MainTabItem".equals(tabs.getChildAt(i).getClass().getName())) return false;
        }
        return true;
    }

    private static boolean isHomeStrip(ViewGroup strip) {
        return isHomeTopStrip(strip) || isHomeBottomStrip(strip);
    }

    private static boolean isHomeHackViewPager(Object pager) {
        return pager instanceof View && isHomeView((View) pager)
                && "viewpager".equals(entryName((View) pager))
                && "com.douban.frodo.baseproject.view.HackViewPager".equals(pager.getClass().getName());
    }

    private static void hookHackViewPagerSwiping(XposedInterface xposed, ClassLoader cl) {
        Class<?> hackViewPagerClass = ReflectUtils.findClass("com.douban.frodo.baseproject.view.HackViewPager", cl);
        if (hackViewPagerClass != null) {
            try {
                Method onIntercept = ReflectUtils.findMethod(hackViewPagerClass, "onInterceptTouchEvent", MotionEvent.class);
                if (onIntercept != null) {
                    xposed.hook(onIntercept).intercept(chain -> {
                        Object pager = chain.getThisObject();
                        if (isHomeHackViewPager(pager)) {
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
                        if (isHomeHackViewPager(pager)) {
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

    /** 侧边栏抽屉子树专用：带上抽屉作用域，避免误伤首页/搜索页的细线元素。 */
    public static void purgeDrawerViews(View drawerRoot) {
        if (drawerRoot == null) return;
        purgeSidebarTree(drawerRoot, 0);
        SidebarInjector.inject(drawerRoot);
    }

    public static void purgeStrip(ViewGroup strip) {
        if (strip == null || !isHomeStrip(strip)) return;
        Context ctx = strip.getContext();
        if (strip.getChildCount() > 0 && strip.getChildAt(0) instanceof LinearLayout) {
            LinearLayout container = (LinearLayout) strip.getChildAt(0);
            int childCount = container.getChildCount();

            // Bottom bar has 5 tabs (0: 首页, 1: 书影音, 2: 小组, 3: 市集, 4: 我)
            if (childCount == 5 && isHomeBottomStrip(strip)) {
                boolean hideSubject = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TAB_SUBJECT, true);
                boolean hideGroup = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TAB_GROUP, true);
                boolean hideShiji = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TAB_SHIJI, true);

                strip.removeOnLayoutChangeListener(BOTTOM_RESIZE);
                strip.addOnLayoutChangeListener(BOTTOM_RESIZE);
                layoutBottom(strip, container, hideSubject, hideGroup, hideShiji);
            } else if (childCount == 2 && isHomeTopStrip(strip)) {
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

    private static final View.OnLayoutChangeListener BOTTOM_RESIZE = (v,l,t,r,b,ol,ot,or,ob) -> {
        if (r - l != or - ol) v.post(() -> purgeStrip((ViewGroup) v));
    };

    private static void layoutBottom(ViewGroup strip, LinearLayout row, boolean subject, boolean group, boolean market) {
        // HorizontalScrollView measures its child with an unbounded horizontal spec.
        // Divide actual viewport pixels, not screen metrics or weights in a wrap-content row.
        int count = 5 - (subject ? 1 : 0) - (group ? 1 : 0) - (market ? 1 : 0);
        int width = strip.getWidth() - strip.getPaddingLeft() - strip.getPaddingRight();
        ViewGroup.LayoutParams rp = row.getLayoutParams();
        if (rp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) rp;
            width -= margins.leftMargin + margins.rightMargin;
        }
        width = Math.max(0, width);
        if (width > 0 && rp.width != width) {
            rp.width = width;
            row.setLayoutParams(rp);
        }
        int space = width - row.getPaddingLeft() - row.getPaddingRight();
        boolean[] hidden = {false, subject, group, market, false};
        for (int i = 0; i < 5; i++) {
            if (!hidden[i]) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams();
                space -= lp.leftMargin + lp.rightMargin;
            }
        }
        if (row.getDividerDrawable() != null) {
            int flags = row.getShowDividers();
            int gaps = ((flags & LinearLayout.SHOW_DIVIDER_BEGINNING) != 0 ? 1 : 0)
                    + ((flags & LinearLayout.SHOW_DIVIDER_MIDDLE) != 0 ? count - 1 : 0)
                    + ((flags & LinearLayout.SHOW_DIVIDER_END) != 0 ? 1 : 0);
            space -= gaps * Math.max(0, row.getDividerDrawable().getIntrinsicWidth());
        }
        space = Math.max(0, space);
        int slot = 0;
        for (int i = 0; i < 5; i++) {
            View tab = row.getChildAt(i);
            tab.setVisibility(hidden[i] ? View.GONE : View.VISIBLE);
            if (width == 0) continue; // the listener reapplies after first layout
            int pixels = hidden[i] ? 0 : space / count + (slot++ < space % count ? 1 : 0);
            // Slot sizing replaces the strip's per-tab horizontal spacing. Keep native child dimensions.
            if (!hidden[i] && (tab.getPaddingLeft() != 0 || tab.getPaddingRight() != 0))
                tab.setPadding(0, tab.getPaddingTop(), 0, tab.getPaddingBottom());
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tab.getLayoutParams();
            if (lp.width != pixels || lp.weight != 0) {
                lp.width = pixels;
                lp.weight = 0;
                tab.setLayoutParams(lp);
            }
        }
        if (strip.getScrollX() != 0) strip.scrollTo(0, strip.getScrollY());
    }

    private static boolean isDrawerPanel(ViewGroup parent, View child) {
        // The host and module may load DrawerLayout from different class loaders.
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (!(parent instanceof DrawerLayout) && !"androidx.drawerlayout.widget.DrawerLayout".equals(parent.getClass().getName())) return false;
        if (lp == null) return false;
        try {
            int gravity = lp instanceof DrawerLayout.LayoutParams ? ((DrawerLayout.LayoutParams) lp).gravity
                    : lp.getClass().getField("gravity").getInt(lp);
            int edge = Gravity.getAbsoluteGravity(gravity, parent.getLayoutDirection()) & Gravity.HORIZONTAL_GRAVITY_MASK;
            return edge == Gravity.LEFT || edge == Gravity.RIGHT;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static final WeakHashMap<View, Integer> sidebarVisibility = new WeakHashMap<>();
    private static final ThreadLocal<View> sidebarWrite = new ThreadLocal<>();

    static void onSidebarRowBound(ViewGroup row) {
        if (SidebarInjector.menuRoot(row) != null) purgeSidebarEntry(row, row.getContext());
        else setSidebarHidden(row, false);
    }

    // Intercept native VISIBLE/GONE requests at the write, not a draw later. Remember what the
    // host wanted (including its own GONE), so switching a rule off restores native policy.
    static int sidebarHostVisibility(View view, String entryName, int requested) {
        if (sidebarWrite.get() == view) return requested;
        boolean tracked = sidebarVisibility.containsKey(view);
        String key = sidebarFixedKey(entryName);
        if (!tracked && key == null) return requested;
        if (SidebarInjector.menuRoot(view) == null) {
            if (tracked) sidebarVisibility.remove(view);
            return requested;
        }
        if (key == null && view instanceof ViewGroup && SidebarInjector.isRow(view)) {
            key = sidebarRowKey((ViewGroup) view);
        }
        if (key != null && hideSidebarOption(view.getContext(), key)) {
            sidebarVisibility.put(view, requested);
            return View.GONE;
        }
        sidebarVisibility.remove(view);
        return requested;
    }

    private static String sidebarFixedKey(String entryName) {
        switch (entryName) {
            case "my_beans": return PureSettings.KEY_HIDE_SIDE_BAG;
            case "grid_entry": return PureSettings.KEY_HIDE_SIDE_SERVICES;
            default: return null;
        }
    }

    private static boolean minorTitle(CharSequence text) {
        if (text == null) return false;
        String title = text.toString().trim();
        return title.equals("未成年人模式") || title.equals("未成年模式") || title.equals("青少年模式");
    }

    private static boolean isMinorEntry(View v) {
        if (minorTitle(v.getContentDescription()) || (v instanceof TextView && minorTitle(((TextView)v).getText()))) return true;
        if (v instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)v;
            for (int i = 0; i < group.getChildCount(); i++) if (isMinorEntry(group.getChildAt(i))) return true;
        }
        return false;
    }

    private static void setSidebarHidden(View v, boolean hide) {
        int visibility = v.getVisibility();
        Integer desired;
        if (hide) {
            sidebarVisibility.putIfAbsent(v, visibility);
            desired = View.GONE;
        } else {
            desired = sidebarVisibility.remove(v);
            if (visibility != View.GONE) return;
        }
        if (desired == null || visibility == desired) return;
        View previous = sidebarWrite.get();
        sidebarWrite.set(v);
        try {
            v.setVisibility(desired);
        } finally {
            if (previous == null) sidebarWrite.remove();
            else sidebarWrite.set(previous);
        }
    }

    static void purgeSidebarTree(View view, int depth) {
        if (view == null || depth > 25) return;
        String key = sidebarFixedKey(entryName(view));
        if (key == null && view instanceof ViewGroup && SidebarInjector.isRow(view)) {
            key = sidebarRowKey((ViewGroup) view);
        }
        if (key == null && view instanceof ImageView && !view.getClass().getName().contains("CircleImageView")) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null && lp.height > 0 && lp.height <= 5) key = PureSettings.KEY_HIDE_SIDE_MINOR;
        }
        if (key == null && view instanceof TextView && "证照信息".contentEquals(((TextView) view).getText())) {
            key = PureSettings.KEY_HIDE_SIDE_LICENSE;
        }
        if (key != null || sidebarVisibility.containsKey(view)) {
            setSidebarHidden(view, key != null && hideSidebarOption(view.getContext(), key));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) purgeSidebarTree(group.getChildAt(i), depth + 1);
        }
    }

    private static void traverseAndPurge(View view, int depth) {
        if (view == null || depth > 25) return;
        if (SidebarInjector.isMenuRoot(view)) {
            purgeDrawerViews(view);
            return;
        }
        Context ctx = view.getContext();
        String className = view.getClass().getName();

        // 2. PagerSlidingTabStrip (Bottom nav and top tab)
        if (className.contains("PagerSlidingTabStrip") && (view instanceof ViewGroup)) {
            purgeStrip((ViewGroup) view);
        }

        // 3. Lock HackViewPager to 1 (推荐) if 动态 is hidden
        if (isHomeHackViewPager(view)) {
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

        // 5. Inspect ID based elements
        int id = view.getId();
        if (id != View.NO_ID) {
            String entryName = entryName(view);

            // Top right Podcast (耳机) icon
            if ("menu_podcast".equals(entryName)) {
                boolean hidePodcast = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_TOP_PODCAST, true);
                view.setVisibility(hidePodcast ? View.GONE : View.VISIBLE);
            }
            // Header container, left image, right banner (首页顶部横幅、宠物及活动推广)
            else if ("header_container".equals(entryName)
                    || "header_left_image".equals(entryName)
                    || "header_right_banner".equals(entryName)) {
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
            // Home notification / venue banner (播客马拉松/活动/场馆横幅: notification_container / venue)
            else if ("notification_container".equals(entryName)
                    || entryName.startsWith("venue_") || "left_venue_image".equals(entryName) || "left_venue_mask".equals(entryName)) {
                boolean hideBanner = PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true);
                if (hideBanner) {
                    view.setVisibility(View.GONE);
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    if (lp != null) {
                        lp.width = 0;
                        lp.height = 0;
                        view.setLayoutParams(lp);
                    }
                }
            }
            // RexxarStructHeader llHeaderContainer (contains post WebView, post big ad, and ad dividers)
            else if ("llHeaderContainer".equals(entryName)) {
                if (view instanceof ViewGroup && PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true)) {
                    ViewGroup vg = (ViewGroup) view;
                    int childCount = vg.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        View child = vg.getChildAt(i);
                        if (child != null && isAdView(child)) {
                            child.setVisibility(View.GONE);
                            ViewGroup.LayoutParams clp = child.getLayoutParams();
                            if (clp != null) {
                                clp.width = 0;
                                clp.height = 0;
                                child.setLayoutParams(clp);
                            }
                            if (i > 0) {
                                View prev = vg.getChildAt(i - 1);
                                if (isDividerView(prev)) {
                                    prev.setVisibility(View.GONE);
                                    ViewGroup.LayoutParams plp = prev.getLayoutParams();
                                    if (plp != null) {
                                        plp.height = 0;
                                        prev.setLayoutParams(plp);
                                    }
                                }
                            }
                            if (i < childCount - 1) {
                                View next = vg.getChildAt(i + 1);
                                if (isDividerView(next)) {
                                    next.setVisibility(View.GONE);
                                    ViewGroup.LayoutParams nlp = next.getLayoutParams();
                                    if (nlp != null) {
                                        nlp.height = 0;
                                        next.setLayoutParams(nlp);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Homepage right bottom Post button (铅笔浮动按钮)
            else if (("btn_post".equals(entryName)) && isHomeView(view)) {
                boolean hidePost = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_HOME_POST_BTN, true);
                view.setVisibility(hidePost ? View.GONE : View.VISIBLE);
            }
            // Search page: 热门话题 title & RecyclerView
            else if (("gallery_topic_title".equals(entryName) || "gallery_topics".equals(entryName)) && SearchLanding.inLanding(view)) {
                boolean hideTopics = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS, true);
                view.setVisibility(hideTopics ? View.GONE : View.VISIBLE);
            }
            // Search page: 实时热门书影音/讨论 ViewPager & indicator (默认保留)
            else if (("vpSubject".equals(entryName) || "pageIndicatorView".equals(entryName)) && SearchLanding.inLanding(view)) {
                boolean hideTrends = PureSettings.getBoolean(ctx, PureSettings.KEY_HIDE_SEARCH_TRENDS, false);
                SearchLanding.apply(view, hideTrends);
                if ("vpSubject".equals(entryName)) SearchPagerHeight.watch(view);
            }
        }

        // Ad views & banners
        if (isAdView(view)) {
            boolean hideAd = PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true);
            if (hideAd) {
                view.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp != null) {
                    lp.width = 0;
                    lp.height = 0;
                    view.setLayoutParams(lp);
                }
            }
        }
        if (className.equals("com.youth.banner.Banner")) {
            boolean hideBanner = PureSettings.getBoolean(ctx, PureSettings.KEY_BLOCK_FEED_AD, true);
            if (hideBanner) {
                view.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp != null) {
                    lp.width = 0;
                    lp.height = 0;
                    view.setLayoutParams(lp);
                }
            }
        }

        // Recurse into children
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            // A landing-page child can insert or remove its following rank panel.
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (isDrawerPanel(group, child)) purgeDrawerViews(child);
                else traverseAndPurge(child, depth + 1);
            }
        }
    }

    private static void purgeSidebarEntry(ViewGroup row, Context ctx) {
        String key = sidebarRowKey(row);
        setSidebarHidden(row, key != null && hideSidebarOption(ctx, key));
    }

    private static String sidebarRowKey(ViewGroup row) {
        String text = extractAllText(row);
        if (text.contains("DoubanPure") || text.startsWith("设置")) return null;
        if (isMinorEntry(row)) return PureSettings.KEY_HIDE_SIDE_MINOR;
        if (text.contains("草稿")) return PureSettings.KEY_HIDE_SIDE_DRAFTS;
        if (text.contains("签到")) return PureSettings.KEY_HIDE_SIDE_CHECKIN;
        if (text.contains("帮助") || text.contains("反馈")) return PureSettings.KEY_HIDE_SIDE_FEEDBACK;
        if (text.contains("社区管理")) return PureSettings.KEY_HIDE_SIDE_COMMUNITY;
        if (text.contains("收藏") || text.contains("豆列")) return PureSettings.KEY_HIDE_SIDE_FAV;
        if (text.contains("关注")) return PureSettings.KEY_HIDE_SIDE_FOLLOW;
        if (text.contains("历史")) return PureSettings.KEY_HIDE_SIDE_HISTORY;
        return null;
    }

    private static boolean hideSidebarOption(Context ctx, String key) {
        return PureSettings.getBoolean(ctx, key, !PureSettings.KEY_HIDE_SIDE_FAV.equals(key)
                && !PureSettings.KEY_HIDE_SIDE_FOLLOW.equals(key) && !PureSettings.KEY_HIDE_SIDE_HISTORY.equals(key));
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

    public static boolean isAdView(View view) {
        if (view == null) return false;
        String entryName = entryName(view);
        if ("notification_container".equals(entryName) || "venue_bg".equals(entryName)) {
            return true;
        }
        String name = view.getClass().getName();
        return name.contains("FeedAdItem")
                || name.contains("FeedAdBanner")
                || name.contains("RecentTopicAdView")
                || name.contains("SubjectAdHeader")
                || name.contains("AdIntersView")
                || name.contains("FeedAdPhotoView")
                || name.contains("IncentiveAdFooter")
                || name.contains("PullAdContainer");
    }

    public static boolean isDividerView(View view) {
        if (view == null) return false;
        if (view.getClass() != View.class) return false;
        if (view.getId() != View.NO_ID) return false;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return false;
        return lp.height > 0 && lp.height <= 100;
    }
}


