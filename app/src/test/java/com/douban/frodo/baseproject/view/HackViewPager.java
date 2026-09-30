package com.douban.frodo.baseproject.view;
public class HackViewPager extends android.widget.FrameLayout {
    private int current;
    public HackViewPager(android.content.Context c) { super(c); }
    public int getCurrentItem() { return current; }
    public void setCurrentItem(int item) { current = item; }
    public void setCurrentItem(int item, boolean smooth) { current = item; }
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) { return true; }
    @Override public boolean onTouchEvent(android.view.MotionEvent e) { return true; }
}
