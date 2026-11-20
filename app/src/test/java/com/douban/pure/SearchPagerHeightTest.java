package com.douban.pure;
import android.content.Context;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import com.douban.frodo.search.adapter.TrendSubjectPagerAdapter;
import com.douban.frodo.search.view.*;
import com.douban.frodo.activity.SplashActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class SearchPagerHeightTest {
 public static class Pager extends FrameLayout {
  int pos; TrendSubjectPagerAdapter adapter; Pager(Context c){super(c);}
  public int getCurrentItem(){return pos;} public TrendSubjectPagerAdapter getAdapter(){return adapter;}
 }
 @Test public void expandsRealPagerParamsAndRestoresOnDifferentPage(){
  SplashActivity c=Robolectric.buildActivity(SplashActivity.class).setup().get();
  TouchableNestedScrollView outer=new TouchableNestedScrollView(c); LinearLayout container=new LinearLayout(c); container.setId(0x7f0a04a8);container.setOrientation(1); outer.addView(container);
  Pager p=new Pager(c);p.setId(0x7f0a1980);container.addView(p,-1,1106);
  SearchTrendSubjectView s=new SearchTrendSubjectView(c);FrameLayout other=new FrameLayout(c); p.addView(other);p.addView(s);p.adapter=new TrendSubjectPagerAdapter(s,other);
  p.layout(0,415,1440,1521); s.layout(0,8,1000,1106); s.rv.layout(0,190,1000,1098);
  for(int i=0;i<4;i++){View row=new View(c);s.rv.addView(row);row.layout(0,266*i,1000,266*(i+1));}
  SearchPagerHeight watcher=new SearchPagerHeight(p);watcher.updateHeight();assertEquals(2858,p.getLayoutParams().height);
  for(int i=4;i<10;i++){View row=new View(c);s.rv.addView(row);row.layout(0,1064+(i-4)*240,1000,1064+(i-3)*240);}
  assertEquals(2702,SearchPagerHeight.measure(p,s.rv,10));
  p.pos=1; watcher.updateHeight();assertEquals(1106,p.getLayoutParams().height);
 }
}
