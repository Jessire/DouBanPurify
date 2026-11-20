package com.douban.pure;
import android.view.View;
import android.widget.LinearLayout;
import java.util.WeakHashMap;

final class SearchLanding {
    private static final String ROOT="com.douban.frodo.search.view.TouchableNestedScrollView";
    // Handles late fragment inflation as well as the existing resume/settings sweeps.
    static void observeInflation(io.github.libxposed.api.XposedInterface api, ClassLoader cl) {
        Class<?> root = ReflectUtils.findClass(ROOT, cl);
        if (root == null) return;
        for (java.lang.reflect.Constructor<?> ctor : root.getDeclaredConstructors()) {
            try {api.hook(ctor).intercept(chain -> {
                Object result=chain.proceed();
                if (chain.getThisObject() instanceof View) {
                    View v=(View)chain.getThisObject();v.post(() -> {if(v.isAttachedToWindow()) DoubanLayoutPurifier.purgeViews(v);});
                }
                return result;
            });} catch (RuntimeException e) {android.util.Log.w("DoubanPure","search landing observer unavailable",e);}
        }
    }

    static boolean inLanding(View v) {
        if (!(v.getParent() instanceof LinearLayout)) return false;
        LinearLayout parent = (LinearLayout)v.getParent();
        return "container".equals(DoubanLayoutPurifier.entryName(parent)) && parent.getParent() instanceof View
                && ROOT.equals(parent.getParent().getClass().getName());
    }

    static boolean isRankPager(View v) {
        return "vpSubject".equals(DoubanLayoutPurifier.entryName(v)) && inLanding(v);
    }

    static View sibling(View pager, String entryName) {
        if (!isRankPager(pager)) return null;
        LinearLayout parent = (LinearLayout)pager.getParent();
        for (int i=0;i<parent.getChildCount();i++) {
            View child=parent.getChildAt(i);
            if (entryName.equals(DoubanLayoutPurifier.entryName(child))) return child;
        }
        return null;
    }

    // Apply the two independent masks before returning to the UI loop. The topic list can
    // start VISIBLE from XML, so the existing setVisibility hook alone is not sufficient.
    static void applyBoundVisibility(View pager) {
        if (!isRankPager(pager)) return;
        boolean hideTrends=PureSettings.getBoolean(pager.getContext(), PureSettings.KEY_HIDE_SEARCH_TRENDS, false);
        apply(pager,hideTrends);
        View indicator=sibling(pager,"pageIndicatorView");
        if (indicator!=null) apply(indicator,hideTrends);
        if (!PureSettings.getBoolean(pager.getContext(), PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS, true)) return;
        for (String id : new String[]{"gallery_topic_title", "gallery_topics"}) {
            View child=sibling(pager,id);
            if (child!=null && child.getVisibility()!=View.GONE) child.setVisibility(View.GONE);
        }
    }

    private static final WeakHashMap<View,Integer> requested = new WeakHashMap<>();
    private static final ThreadLocal<View> updating = new ThreadLocal<>();

    // Keep even GONE/INVISIBLE requests from the host while our setting masks the pager.
    static int onVisibility(View v, int value, boolean hide) {
        if (updating.get() == v) return value;
        if (!hide) { requested.remove(v); return value; }
        requested.put(v, value);
        return View.GONE;
    }

    static void apply(View v, boolean hide) {
        Integer target;
        if (hide) {
            if (!requested.containsKey(v)) requested.put(v, v.getVisibility());
            target = View.GONE;
        } else target = requested.remove(v);
        if (target != null && target != v.getVisibility()) {
            View prior = updating.get();
            updating.set(v);
            try { v.setVisibility(target); }
            finally { if (prior == null) updating.remove(); else updating.set(prior); }
        }
    }
}
