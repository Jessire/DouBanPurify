package com.douban.pure;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.astuetz.PagerSlidingTabStrip;
import com.douban.frodo.activity.SplashActivity;
import com.douban.frodo.view.MainTabItem;
import com.douban.frodo.baseproject.view.HackViewPager;
import com.douban.frodo.baseproject.ad.model.FeedAd;
import io.github.libxposed.api.XposedInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class PurifierRegressionTest {
    private Activity home, detail;
    private final Map<Executable, XposedInterface.Hooker> hooks = new HashMap<>();
    @Before public void setup() {
        home = Robolectric.buildActivity(SplashActivity.class).setup().get();
        detail = Robolectric.buildActivity(Activity.class).setup().get();
        XposedInterface api = mock(XposedInterface.class);
        when(api.hook(any(Executable.class))).thenAnswer(call -> {
            Executable method = call.getArgument(0);
            XposedInterface.HookBuilder builder = mock(XposedInterface.HookBuilder.class);
            when(builder.intercept(any())).thenAnswer(h -> { hooks.put(method, h.getArgument(0)); return null; });
            return builder;
        });
        DoubanLayoutPurifier.install(api, getClass().getClassLoader());
        DoubanAdPurifier.install(api, getClass().getClassLoader());
    }
    private PagerSlidingTabStrip strip(Context ctx, int id, boolean main, String... titles) {
        PagerSlidingTabStrip strip = new PagerSlidingTabStrip(ctx);
        strip.setId(id);
        LinearLayout row = new LinearLayout(ctx);
        strip.addView(row);
        for (String title : titles) {
            TextView tab = main ? new MainTabItem(ctx) : new TextView(ctx);
            tab.setText(title);
            row.addView(tab, new LinearLayout.LayoutParams(180, 60));
        }
        return strip;
    }
    private View tab(PagerSlidingTabStrip s, int i) { return ((LinearLayout)s.getChildAt(0)).getChildAt(i); }
    private XposedInterface.Chain chain(Method m, Object receiver) throws Throwable {
        XposedInterface.Chain chain = mock(XposedInterface.Chain.class);
        when(chain.getThisObject()).thenReturn(receiver);
        when(chain.getExecutable()).thenReturn(m);
        when(chain.proceed()).thenReturn(true);
        return chain;
    }
    @Test public void openingThreeThenTwoTabSubjectsPreservesBothStrips() {
        PagerSlidingTabStrip movie = strip(detail, 0x7f0a14bb, false, "综合", "影评", "讨论");
        PagerSlidingTabStrip tv = strip(detail, 0x7f0a14bb, false, "剧评", "小组讨论");
        DoubanLayoutPurifier.purgeViews(movie);
        DoubanLayoutPurifier.purgeViews(tv);
        for (int i = 0; i < 3; i++) assertEquals(View.VISIBLE, tab(movie,i).getVisibility());
        for (int i = 0; i < 2; i++) {
            assertEquals("TV tab must not disappear", View.VISIBLE, tab(tv,i).getVisibility());
            assertEquals(180, tab(tv,i).getLayoutParams().width);
        }
    }
    @Test public void fiveSearchTabsAreNotBottomNavigation() {
        PagerSlidingTabStrip s = strip(detail, 0x7f0a14bb, false, "综合", "书影音", "小组", "日记", "用户");
        DoubanLayoutPurifier.purgeStrip(s);
        for(int i=0;i<5;i++) assertEquals(View.VISIBLE,tab(s,i).getVisibility());
    }
    @Test public void otherHomeFragmentTabsRemainUntouched() {
        PagerSlidingTabStrip s = strip(home, 0x7f0a146a, false, "推荐", "榜单");
        DoubanLayoutPurifier.purgeStrip(s);
        assertEquals(View.VISIBLE,tab(s,0).getVisibility());
    }
    @Test public void homeBottomNavigationStillPurifies() {
        PagerSlidingTabStrip s = strip(home, 0x7f0a14bb, true, "首页", "书影音", "小组", "市集", "我");
        DoubanLayoutPurifier.purgeStrip(s);
        for(int i=0;i<5;i++) assertEquals(i==0 || i==4 ? View.VISIBLE : View.GONE,tab(s,i).getVisibility());
    }
    @Test public void wrappedHomeTopTabsStillPurify() {
        PagerSlidingTabStrip s = strip(new ContextThemeWrapper(home, android.R.style.Theme_Material), 0x7f0a14b8, false, "动态", "精选");
        DoubanLayoutPurifier.purgeStrip(s);
        assertEquals(View.GONE,tab(s,0).getVisibility());
        assertEquals(View.VISIBLE,tab(s,1).getVisibility());
    }
    @Test public void detailPublishIconRemainsVisibleDuringTraversal() {
        View icon = new View(detail); icon.setId(0x7f0a02e1);
        DoubanLayoutPurifier.purgeViews(icon);
        assertEquals(View.VISIBLE, icon.getVisibility());
    }
    @Test public void homePublishButtonStillHides() {
        View icon = new View(home); icon.setId(0x7f0a02e1);
        DoubanLayoutPurifier.purgeViews(icon);
        assertEquals(View.GONE, icon.getVisibility());
    }
    @Test public void detailPublishVisibilityHookPassesThrough() throws Throwable {
        Method m=View.class.getDeclaredMethod("setVisibility",int.class);
        View icon=new View(detail); icon.setId(0x7f0a02e1);
        XposedInterface.Chain c=chain(m,icon);
        when(c.getArg(0)).thenReturn(View.VISIBLE);
        hooks.get(m).intercept(c);
        verify(c).proceed();
        verify(c,never()).proceed(any(Object[].class));
    }
    @Test public void detailPagerIsNeitherForcedNorTouchBlocked() throws Throwable {
        HackViewPager p=new HackViewPager(detail); p.setId(0x7f0a1950);
        DoubanLayoutPurifier.purgeViews(p);
        assertEquals(0,p.getCurrentItem());
        for(String name : new String[]{"onTouchEvent","onInterceptTouchEvent"}) {
            Method m=HackViewPager.class.getDeclaredMethod(name,MotionEvent.class);
            XposedInterface.Chain c=chain(m,p);
            assertEquals(true,hooks.get(m).intercept(c));
            verify(c).proceed();
        }
    }
    @Test public void onlyExactHomeFeedPagerIsForced() {
        HackViewPager feed=new HackViewPager(home); feed.setId(0x7f0a1950);
        HackViewPager subjects=new HackViewPager(home); subjects.setId(0x7f0a1470);
        DoubanLayoutPurifier.purgeViews(feed);
        DoubanLayoutPurifier.purgeViews(subjects);
        assertEquals(1,feed.getCurrentItem());
        assertEquals(0,subjects.getCurrentItem());
    }
    @Test public void detailTabLayoutListenerAndWidthRemainNative() throws Throwable {
        PagerSlidingTabStrip s=strip(detail,0x7f0a14bb,false,"剧评","小组讨论");
        Object listener=s.new d();
        Method m=listener.getClass().getDeclaredMethod("onGlobalLayout");
        XposedInterface.Chain c=chain(m,listener); hooks.get(m).intercept(c); verify(c).proceed();
        Method width=PagerSlidingTabStrip.class.getDeclaredMethod("resizeContentWidth");
        XposedInterface.Chain w=chain(width,s); hooks.get(width).intercept(w);
        assertEquals(0,s.S);
    }
    @Test public void integerAdValidationCannotReceiveBoolean() throws Throwable {
        Method m=FeedAd.class.getDeclaredMethod("isValid");
        FeedAd ad=new FeedAd();
        Object result=hooks.containsKey(m) ? hooks.get(m).intercept(chain(m,ad)) : m.invoke(ad);
        assertTrue("Integer-returning ad validation must stay integer", result instanceof Integer);
    }
    @Test public void booleanAdBlockStillWorks() throws Throwable {
        Method m=FeedAd.class.getDeclaredMethod("isBlocked");
        assertEquals(true,hooks.get(m).intercept(chain(m,new FeedAd())));
    }
}
