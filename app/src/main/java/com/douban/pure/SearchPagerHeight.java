package com.douban.pure;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.reflect.Field;
import java.util.List;
import java.util.WeakHashMap;

/** Measure live host rows, not detached duplicate holders. All access is on the UI thread. */
final class SearchPagerHeight implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnScrollChangedListener, Runnable {
    private static final WeakHashMap<View, Boolean> watched = new WeakHashMap<>();
    private static final String PAGER = "com.douban.frodo.search.adapter.TrendSubjectPagerAdapter";
    private static final String SUBJECT = "com.douban.frodo.search.view.SearchTrendSubjectView";
    private final ViewGroup pager;
    private int original, applied, scrollX;
    private boolean pending, warned;
    private ViewTreeObserver tree;

    static void watch(View view) {
        if (!(view instanceof ViewGroup) || watched.put(view, true) != null) return;
        SearchPagerHeight observer = new SearchPagerHeight((ViewGroup)view);
        view.addOnAttachStateChangeListener(observer);
        if (view.isAttachedToWindow()) observer.onViewAttachedToWindow(view);
    }
    SearchPagerHeight(ViewGroup pager) { this.pager = pager; }
    public void onViewAttachedToWindow(View v) {
        tree = v.getViewTreeObserver(); tree.addOnGlobalLayoutListener(this); tree.addOnScrollChangedListener(this); schedule();
    }
    public void onViewDetachedFromWindow(View v) {
        if (tree != null && tree.isAlive()) { tree.removeOnGlobalLayoutListener(this); tree.removeOnScrollChangedListener(this); }
        tree = null; v.removeCallbacks(this); pending = false;
    }
    private void schedule() { if (!pending) { pending = true; pager.post(this); } }
    public void onGlobalLayout() { schedule(); }
    public void onScrollChanged() { if (scrollX != pager.getScrollX()) { scrollX = pager.getScrollX(); schedule(); } }

    public void run() {
        pending = false;
        if (tree == null || !pager.isShown() || !SearchLanding.inLanding(pager)) return;
        SearchNativeBoards.update(pager);
        updateHeight();
    }
    void updateHeight() {
        try {
            Object adapter = pager.getClass().getMethod("getAdapter").invoke(pager);
            int h = 0;
            if (adapter != null && PAGER.equals(adapter.getClass().getName())) {
                // 367 instantiateItem returns k[position] itself; child indices include adjacent pages.
                Field k = adapter.getClass().getDeclaredField("k"); k.setAccessible(true);
                List<?> pages = (List<?>)k.get(adapter);
                int pos = (Integer)pager.getClass().getMethod("getCurrentItem").invoke(pager);
                if (pos < 0 || pos >= pages.size()) return;
                Object page = pages.get(pos);
                if (!(page instanceof ViewGroup) || ((View)page).getParent() != pager) return;
                ViewGroup subject = find((ViewGroup)page);
                if (subject != null) {
                    ViewGroup rv = (ViewGroup)subject.getClass().getMethod("getLvContent").invoke(subject);
                    Object rowAdapter = rv.getClass().getMethod("getAdapter").invoke(rv);
                    if (rowAdapter == null) return;
                    String name = rowAdapter.getClass().getName();
                    if (name.equals("com.douban.frodo.search.adapter.SearchTrendSubjectsAdapter") || name.equals("com.douban.frodo.search.adapter.SearchHotBoardAdapter")) {
                        int count = (Integer)rowAdapter.getClass().getMethod("getItemCount").invoke(rowAdapter);
                        h = measure(pager, rv, count);
                        if (h < 0) return; // rows not laid out yet; next global layout retries
                    }
                }
            }
            ViewGroup.LayoutParams lp = pager.getLayoutParams();
            if (applied == 0 || lp.height != applied) original = lp.height;
            int target = h == 0 ? original : h;
            if (lp.height != target) { lp.height = target; pager.setLayoutParams(lp); }
            applied = h;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) { warned = true; Log.w("DoubanPure", "search pager height unavailable", e); }
        }
    }
    static ViewGroup find(ViewGroup v) {
        if (SUBJECT.equals(v.getClass().getName())) return v;
        for (int i=0;i<v.getChildCount();i++) if (v.getChildAt(i) instanceof ViewGroup) {
            ViewGroup found = find((ViewGroup)v.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    static int measure(ViewGroup pager, ViewGroup rv, int count) {
        if (count <= 0 || count > 10) return 0;
        int n = rv.getChildCount();
        if (n == 0 || n > count || rv.getWidth() <= 0) return -1;
        int chrome = rv.getPaddingTop()+rv.getPaddingBottom();
        // Actual offsets include page/header/shadow; include bottom margins and ancestor padding separately.
        for (View v=rv; v!=pager;) {
            if (!(v.getParent() instanceof ViewGroup)) return -1;
            ViewGroup parent=(ViewGroup)v.getParent(); chrome += v.getTop()+parent.getPaddingBottom();
            if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) chrome += ((ViewGroup.MarginLayoutParams)v.getLayoutParams()).bottomMargin;
            v=parent;
        }
        View first=rv.getChildAt(0), last=rv.getChildAt(n-1);
        int top=first.getTop(), bottom=last.getBottom();
        if (first.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) top-=((ViewGroup.MarginLayoutParams)first.getLayoutParams()).topMargin;
        if (last.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) bottom+=((ViewGroup.MarginLayoutParams)last.getLayoutParams()).bottomMargin;
        int span=bottom-top;
        if (span<=0) return -1;
        // Bootstrap from all attached rows. Once expanded, all N are measured and the span is exact.
        return chrome + (int)(((long)span*count+n-1)/n);
    }
}
