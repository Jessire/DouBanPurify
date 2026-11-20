package com.douban.pure;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.drawerlayout.widget.DrawerLayout;
import com.astuetz.PagerSlidingTabStrip;
import com.douban.frodo.activity.SplashActivity;
import com.douban.frodo.view.MainTabItem;
import com.douban.frodo.view.SlideMenuView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class LayoutPurifierTest {
    private Activity home;
    @Before public void setup() {
        home = Robolectric.buildActivity(SplashActivity.class).setup().get();
    }

    private void layout(View v, int width) {
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, width, 64);
    }

    @Test public void allEightCombinationsFillTheViewportWithoutScrolling() {
        PagerSlidingTabStrip strip = new PagerSlidingTabStrip(home);
        strip.setId(0x7f0a14c4);
        strip.setPadding(3, 0, 4, 0);
        LinearLayout row = new LinearLayout(home);
        strip.addView(row);
        for (int i = 0; i < 5; i++) row.addView(new MainTabItem(home), 180, 60);
        for (int width : new int[]{383, 673}) {
            layout(strip, width);
            for (int mask = 0; mask < 8; mask++) {
                PureSettings.setBoolean(home, PureSettings.KEY_HIDE_TAB_SUBJECT, (mask & 1) != 0);
                PureSettings.setBoolean(home, PureSettings.KEY_HIDE_TAB_GROUP, (mask & 2) != 0);
                PureSettings.setBoolean(home, PureSettings.KEY_HIDE_TAB_SHIJI, (mask & 4) != 0);
                DoubanLayoutPurifier.purgeStrip(strip);
                layout(strip, width);
                int right = 0, count = 5 - Integer.bitCount(mask);
                for (int i = 0; i < 5; i++) {
                    View v = row.getChildAt(i);
                    boolean hide = i > 0 && i < 4 && (mask & (1 << (i - 1))) != 0;
                    assertEquals(hide ? View.GONE : View.VISIBLE, v.getVisibility());
                    if (!hide) {
                        assertEquals(right, v.getLeft());
                        assertTrue(Math.abs(v.getWidth() - (width - 7) / (double) count) < 1);
                        right = v.getRight();
                    }
                }
                assertEquals(width - 7, right);
                assertFalse(strip.canScrollHorizontally(1));
            }
        }
    }

    @Test public void bottomSlotLeavesNativeIconAndSingleLineTitleUnclipped() {
        PagerSlidingTabStrip strip = new PagerSlidingTabStrip(home); strip.setId(0x7f0a14c4);
        LinearLayout row = new LinearLayout(home); strip.addView(row);
        for (String label : new String[]{"首页", "书影音", "小组", "市集", "我"}) {
            MainTabItem tab = new MainTabItem(home); tab.title.setText(label);
            tab.setPadding(120, 1, 120, 2); row.addView(tab, 288, 188);
        }
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_TAB_SUBJECT, false);
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_TAB_GROUP, false);
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_TAB_SHIJI, false);
        Runnable measure = () -> { strip.measure(View.MeasureSpec.makeMeasureSpec(1440, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(188, View.MeasureSpec.EXACTLY)); strip.layout(0,0,1440,188); };
        measure.run(); DoubanLayoutPurifier.purgeStrip(strip); measure.run();
        for (int i=0;i<5;i++) {
            MainTabItem tab = (MainTabItem)row.getChildAt(i);
            assertEquals(288, tab.getWidth()); assertEquals(113, tab.icons.getWidth());
            assertEquals(105, tab.icon.getWidth()); assertTrue(tab.icon.getLeft() >= 0);
            assertTrue(tab.icon.getRight() <= tab.icons.getWidth());
            assertEquals(1, tab.title.getLineCount());
            assertTrue(tab.title.getWidth() >= Math.ceil(tab.title.getPaint().measureText(tab.title.getText().toString())));
            assertTrue(tab.title.getTop() >= tab.icons.getBottom());
            assertEquals(1,tab.getPaddingTop()); assertEquals(2,tab.getPaddingBottom());
        }
    }
    private static class SlideMenuListEntryView extends LinearLayout {
        SlideMenuListEntryView(Context c) { super(c); }
    }

    @Test public void lateBindingAndReuseArePurifiedWithoutWaitingForPreDraw() {
        SlideMenuView panel = new SlideMenuView(home);
        LinearLayout content = new LinearLayout(home);
        SlideMenuListEntryView row = labeledRow("未成年人模式");
        TextView title = (TextView) row.getChildAt(0);
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_SIDE_MINOR, true);
        panel.addView(row);
        DoubanLayoutPurifier.onSidebarRowBound(row);
        assertEquals(View.GONE, row.getVisibility());
        title.setText("未成年人保护说明");
        DoubanLayoutPurifier.onSidebarRowBound(row);
        assertEquals(View.VISIBLE, row.getVisibility());
        title.setText("");
        row.setContentDescription("青少年模式");
        DoubanLayoutPurifier.onSidebarRowBound(row);
        assertEquals(View.GONE, row.getVisibility());
        panel.removeView(row);
        content.addView(row);
        DoubanLayoutPurifier.onSidebarRowBound(row);
        assertEquals(View.VISIBLE, row.getVisibility());
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_SIDE_MINOR, false);
        content.removeView(row);
        panel.addView(row);
        DoubanLayoutPurifier.onSidebarRowBound(row);
        assertEquals(View.VISIBLE, row.getVisibility());
    }

    private SlideMenuListEntryView labeledRow(String text) {
        SlideMenuListEntryView row = new SlideMenuListEntryView(home);
        TextView title = new TextView(home);
        title.setText(text);
        row.addView(title);
        return row;
    }

    private LinearLayout subtree() {
        LinearLayout root = new LinearLayout(home);
        root.addView(new ImageView(home), 100, 2);
        TextView license = new TextView(home);
        license.setText("证照信息");
        root.addView(license);
        View grid = new View(home);
        grid.setId(0x7f0a07f3);
        root.addView(grid);
        SlideMenuListEntryView entry = new SlideMenuListEntryView(home);
        TextView label = new TextView(home);
        label.setText("草稿箱");
        entry.addView(label);
        entry.setVisibility(View.INVISIBLE);
        root.addView(entry, 80, 48);
        return root;
    }

    @Test public void nativeListBindPurifiesFirstMeasureAndKeepsEntryAcrossRebuilds() {
        LinearLayout list = new LinearLayout(home);
        list.setOrientation(LinearLayout.VERTICAL);
        SlideMenuListEntryView settings = labeledRow("设置");
        SlideMenuListEntryView drafts = labeledRow("草稿箱");
        list.addView(settings, -1, 20);
        list.addView(drafts, -1, 20);
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_SIDE_DRAFTS, true);
        SidebarInjector.afterListBound(list, null);
        View injected = list.getChildAt(1);
        assertSame(injected, SidebarInjector.findEntry(list));
        assertEquals(View.GONE, drafts.getVisibility());
        layout(list, 240);
        assertEquals(20, injected.getTop());
        assertEquals(0, drafts.getMeasuredHeight());
        assertFalse(list.isLayoutRequested());
        SidebarInjector.afterListBound(list, injected);
        assertSame(injected, list.getChildAt(1));
        assertFalse("stable list must not request layout", list.isLayoutRequested());

        list.removeAllViews(); // Host's real setData rebuilds its children.
        list.addView(labeledRow("设置帮助"));
        list.addView(settings);
        list.addView(labeledRow("签到"));
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_SIDE_CHECKIN, true);
        SidebarInjector.afterListBound(list, injected);
        assertSame(settings, list.getChildAt(1));
        assertSame(injected, list.getChildAt(2));
        assertEquals(View.GONE, list.getChildAt(3).getVisibility());
        assertEquals(4, list.getChildCount());

        list.removeView(settings);
        list.addView(settings, 0); // Move only when the actual anchor changes.
        SidebarInjector.afterListBound(list, injected);
        assertSame(injected, list.getChildAt(1));
        assertEquals(4, list.getChildCount());
    }

    @Test public void nativeVisibilityWritesAreScopedAndRestoredAccurately() {
        SlideMenuView menu = new SlideMenuView(home);
        SlideMenuListEntryView young = labeledRow("未成年人模式");
        menu.addView(young);
        LinearLayout body = new LinearLayout(home);
        SlideMenuListEntryView outside = labeledRow("未成年人模式");
        body.addView(outside);
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_SIDE_MINOR, true);
        DoubanLayoutPurifier.onSidebarRowBound(young);
        assertEquals(View.GONE, young.getVisibility());
        assertEquals(View.GONE, DoubanLayoutPurifier.sidebarHostVisibility(young, "", View.VISIBLE));
        assertEquals(View.VISIBLE, DoubanLayoutPurifier.sidebarHostVisibility(outside, "", View.VISIBLE));
        assertEquals(View.GONE, DoubanLayoutPurifier.sidebarHostVisibility(young, "", View.GONE));
        PureSettings.setBoolean(home, PureSettings.KEY_HIDE_SIDE_MINOR, false);
        DoubanLayoutPurifier.onSidebarRowBound(young);
        assertEquals("native account state must still win", View.GONE, young.getVisibility());
        assertEquals(View.VISIBLE, DoubanLayoutPurifier.sidebarHostVisibility(young, "", View.VISIBLE));
    }

    @Test public void onlyDrawerPanelIsHiddenAndItsOriginalStateReturns() {
        DrawerLayout drawer = new DrawerLayout(home);
        LinearLayout content = subtree(), panel = subtree();
        drawer.addView(content, new DrawerLayout.LayoutParams(-1, -1));
        drawer.addView(panel, new DrawerLayout.LayoutParams(240, -1, Gravity.START));
        String[] keys = {PureSettings.KEY_HIDE_SIDE_MINOR, PureSettings.KEY_HIDE_SIDE_LICENSE,
                PureSettings.KEY_HIDE_SIDE_SERVICES, PureSettings.KEY_HIDE_SIDE_DRAFTS};
        for (String key : keys) PureSettings.setBoolean(home, key, true);
        for (int i = 0; i < 2; i++) DoubanLayoutPurifier.purgeViews(drawer);
        for (int i = 0; i < 4; i++) {
            assertEquals(i == 3 ? View.INVISIBLE : View.VISIBLE, content.getChildAt(i).getVisibility());
            assertEquals(View.GONE, panel.getChildAt(i).getVisibility());
        }
        assertEquals(View.VISIBLE, panel.getVisibility());
        for (String key : keys) PureSettings.setBoolean(home, key, false);
        DoubanLayoutPurifier.purgeViews(drawer);
        for (int i = 0; i < 4; i++) assertEquals(content.getChildAt(i).getVisibility(), panel.getChildAt(i).getVisibility());
        LinearLayout entry = (LinearLayout) panel.getChildAt(3);
        assertEquals(80, entry.getLayoutParams().width);
        assertEquals(48, entry.getLayoutParams().height);
        assertEquals(View.VISIBLE, entry.getChildAt(0).getVisibility());
    }
}
