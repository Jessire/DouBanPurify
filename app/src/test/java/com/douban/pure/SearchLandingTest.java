package com.douban.pure;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import com.douban.frodo.activity.SplashActivity;
import com.douban.frodo.search.view.TouchableNestedScrollView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class SearchLandingTest {
    @Test public void boundTopicMaskIsImmediateLocalAndIndependentOfRankSwitch() {
        SplashActivity c=Robolectric.buildActivity(SplashActivity.class).setup().get();
        LinearLayout body=new LinearLayout(c);body.setId(0x7f0a04a8);
        View pager=new View(c),title=new View(c),list=new View(c);
        pager.setId(0x7f0a1980);title.setId(0x7f0a07d1);list.setId(0x7f0a07d2);
        for(View child:new View[]{pager,title,list})body.addView(child);
        PureSettings.setBoolean(c,PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS,true);
        SearchLanding.applyBoundVisibility(pager);
        assertEquals(View.VISIBLE,title.getVisibility());assertEquals(View.VISIBLE,list.getVisibility());
        new TouchableNestedScrollView(c).addView(body);
        for(boolean hideRanks:new boolean[]{false,true}) for(boolean hideTopics:new boolean[]{false,true}) {
            PureSettings.setBoolean(c,PureSettings.KEY_HIDE_SEARCH_TRENDS,hideRanks);
            PureSettings.setBoolean(c,PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS,hideTopics);
            title.setVisibility(View.VISIBLE);list.setVisibility(View.VISIBLE); // host rebind
            SearchLanding.applyBoundVisibility(pager); // no Looper idle/purge pass
            assertEquals(hideRanks?View.GONE:View.VISIBLE,pager.getVisibility());
            assertEquals(hideTopics?View.GONE:View.VISIBLE,title.getVisibility());
            assertEquals(hideTopics?View.GONE:View.VISIBLE,list.getVisibility());
        }
    }
    @Test public void scopesByLandingAndKeepsHostChildren() {
        SplashActivity c=Robolectric.buildActivity(SplashActivity.class).setup().get();
        LinearLayout body=new LinearLayout(c);body.setId(0x7f0a04a8);
        View search=new View(c),pager=new View(c),indicator=new View(c),topic=new View(c);
        pager.setId(0x7f0a1980);indicator.setId(0x7f0a0f73);topic.setId(0x7f0a07d1);
        for(View child:new View[]{search,pager,indicator,topic})body.addView(child);
        DoubanLayoutPurifier.purgeViews(body);assertEquals(4,body.getChildCount());assertEquals(View.VISIBLE,pager.getVisibility());
        new TouchableNestedScrollView(c).addView(body);
        indicator.setVisibility(View.INVISIBLE);
        for(boolean hide:new boolean[]{false,true,false}) {
            PureSettings.setBoolean(c,PureSettings.KEY_HIDE_SEARCH_TRENDS,hide);
            PureSettings.setBoolean(c,PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS,!hide);
            DoubanLayoutPurifier.purgeViews(body);DoubanLayoutPurifier.purgeViews(body);
            assertEquals(4,body.getChildCount());assertSame(pager,body.getChildAt(1));assertSame(indicator,body.getChildAt(2));
            assertEquals(hide?View.GONE:View.VISIBLE,pager.getVisibility());assertEquals(hide?View.GONE:View.INVISIBLE,indicator.getVisibility());
            assertEquals(hide?View.VISIBLE:View.GONE,topic.getVisibility());assertEquals(View.VISIBLE,search.getVisibility());
        }
    }
}
