package com.douban.pure;
import android.app.Dialog;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import com.douban.frodo.activity.SplashActivity;
import com.douban.frodo.view.MainTabItem;
import com.astuetz.PagerSlidingTabStrip;
public class DeviceBehaviorTest {
 SplashActivity a; Context c;String store;
 void setup(){c=InstrumentationRegistry.getInstrumentation().getTargetContext();store="instrument_"+UUID.randomUUID();a=new SplashActivity(c,store);}
 @After public void cleanup(){if(c!=null)c.deleteSharedPreferences(store);}
 void flag(String k,boolean v){PureSettings.setBoolean(a,k,v);}
 void visible(View v,boolean b){assertEquals(b?View.VISIBLE:View.GONE,v.getVisibility());}
 View item(LinearLayout p,int id){View v=new View(a);v.setId(id);p.addView(v,72,48);return v;}
 static final String[] KEYS={
PureSettings.KEY_SKIP_SPLASH,
PureSettings.KEY_BLOCK_BETA_UPDATE,
PureSettings.KEY_BLOCK_RATING_DIALOG,
PureSettings.KEY_BLOCK_FEED_AD,
PureSettings.KEY_BLOCK_UMENG,
PureSettings.KEY_HIDE_TAB_SUBJECT,
PureSettings.KEY_HIDE_TAB_GROUP,
PureSettings.KEY_HIDE_TAB_SHIJI,
PureSettings.KEY_HIDE_TOP_DONGTAI,
PureSettings.KEY_HIDE_TOP_PODCAST,
PureSettings.KEY_HIDE_TOP_PETS,
PureSettings.KEY_HIDE_HOME_POST_BTN,
PureSettings.KEY_HIDE_SEARCH_HOT_TOPICS,
PureSettings.KEY_HIDE_SEARCH_TRENDS,
PureSettings.KEY_REPLACE_SEARCH_BOARDS,
PureSettings.KEY_HIDE_SIDE_MINOR,
PureSettings.KEY_HIDE_SIDE_BAG,
PureSettings.KEY_HIDE_SIDE_DRAFTS,
PureSettings.KEY_HIDE_SIDE_CHECKIN,
PureSettings.KEY_HIDE_SIDE_FEEDBACK,
PureSettings.KEY_HIDE_SIDE_COMMUNITY,
PureSettings.KEY_HIDE_SIDE_SERVICES,
 }; static final String[] LABELS={
"跳过开屏广告",
"屏蔽 Beta 版本更新",
"屏蔽求好评弹窗",
"拦截信息流广告",
"屏蔽友盟统计分析",
"隐藏「书影音」Tab",
"隐藏「小组」Tab",
"隐藏「市集」Tab",
"首页顶部移除动态 Tab",
"隐藏右上角播客/耳机",
"隐藏搜索框下方宠物挂件",
"隐藏右下角发布悬浮笔",
"搜索页屏蔽热门话题",
"搜索页屏蔽实时热门榜",
"用电影榜和电视榜替换讨论榜",
"侧边栏隐藏未成年人模式",
"侧边栏隐藏我的背包",
"侧边栏隐藏草稿箱",
"侧边栏隐藏签到",
"侧边栏隐藏帮助与反馈",
"侧边栏隐藏社区管理中心",
"侧边栏隐藏底部服务网格",
 };
 static final int SIDEBAR_START=Arrays.asList(KEYS).indexOf(PureSettings.KEY_HIDE_SIDE_MINOR);
 static boolean defaultChecked(int i){return !PureSettings.KEY_HIDE_SEARCH_TRENDS.equals(KEYS[i]);}
 int switchCount(View view){int count=view instanceof Switch?1:0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=switchCount(group.getChildAt(i));}return count;}
 View named(View v,String t){if(v instanceof TextView && t.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View child=named(g.getChildAt(i),t);if(child!=null)return child;}}return null;}
 Switch sw(Dialog dialog,int i){View t=named(dialog.getWindow().getDecorView(),LABELS[i]);assertNotNull(LABELS[i],t);return (Switch)((ViewGroup)t.getParent().getParent()).getChildAt(1);}
 @Test public void all22SwitchesSaveAndRebuildWithSelectedPage(){InstrumentationRegistry.getInstrumentation().runOnMainSync(this::all22SwitchesSaveAndRebuildWithSelectedPageBody);} void all22SwitchesSaveAndRebuildWithSelectedPageBody(){setup();Dialog d=InAppSettingsDialog.buildDialog(a,null);assertEquals(22,KEYS.length);assertEquals(KEYS.length,LABELS.length);assertEquals(KEYS.length,switchCount(d.getWindow().getDecorView()));for(int pass=0;pass<2;pass++){
 for(int i=0;i<KEYS.length;i++){Switch s=sw(d,i);boolean prior=pass==0?defaultChecked(i):!defaultChecked(i);assertEquals(KEYS[i],prior,s.isChecked());s.performClick();assertEquals(KEYS[i],!prior,PureSettings.getBoolean(a,KEYS[i],prior));}
 named(d.getWindow().getDecorView(),"📚 侧栏").performClick();assertEquals(3,PureSettings.getInt(a,PureSettings.KEY_UI_LAST_TAB,-1));d=InAppSettingsDialog.buildDialog(a,null);for(int i=0;i<KEYS.length;i++)assertEquals(KEYS[i],pass==0?!defaultChecked(i):defaultChecked(i),sw(d,i).isChecked());View page=sw(d,SIDEBAR_START);while(page!=null){assertNotEquals(View.GONE,page.getVisibility());page=page.getParent() instanceof View?(View)page.getParent():null;}
 }}
 @Test public void homeAndSearchCombinations(){InstrumentationRegistry.getInstrumentation().runOnMainSync(this::homeAndSearchCombinationsBody);} void homeAndSearchCombinationsBody(){setup();PagerSlidingTabStrip strip=new PagerSlidingTabStrip(a);strip.setId(0x7f0a14c4);LinearLayout row=new LinearLayout(a);strip.addView(row);for(int i=0;i<5;i++)row.addView(new MainTabItem(a),200,100);for(int mask=0;mask<8;mask++){for(int j=0;j<3;j++)flag(KEYS[5+j],(mask&(1<<j))!=0); measure(strip);DoubanLayoutPurifier.purgeStrip(strip);measure(strip);int sum=0;for(int i=0;i<5;i++){boolean show=i==0||i==4||(mask&(1<<(i-1)))==0;View tab=row.getChildAt(i);visible(tab,show);if(show){sum+=tab.getWidth();assertTrue(Math.abs(tab.getWidth()-1000.0/(5-Integer.bitCount(mask)))<=1);}}assertEquals(1000,sum);}
 LinearLayout root=new LinearLayout(a);int[] ids={0x7f0a07d1,0x7f0a07d2,0x7f0a1980,0x7f0a0f73};for(int id:ids)item(root,id);for(int m=0;m<4;m++){flag(KEYS[12],(m&1)!=0);flag(KEYS[13],(m&2)!=0);DoubanLayoutPurifier.purgeViews(root);for(int i=0;i<4;i++)visible(root.getChildAt(i),true);}
 PagerSlidingTabStrip top=new PagerSlidingTabStrip(a);top.setId(0x7f0a14c1);LinearLayout tabs=new LinearLayout(a);top.addView(tabs);tabs.addView(new TextView(a));tabs.addView(new TextView(a));for(boolean b:new boolean[]{true,false}){flag(KEYS[8],b);DoubanLayoutPurifier.purgeStrip(top);visible(tabs.getChildAt(0),!b);for(int i=0;i<3;i++){flag(KEYS[9+i],b);View v=item(new LinearLayout(a),new int[]{0x7f0a0d75,0x7f0a08cc,0x7f0a02e3}[i]);DoubanLayoutPurifier.purgeViews(v);visible(v,!b);}}}
 // Capture registered callbacks; do not load LSPosed or call a real SDK.
 @Test public void adCallbacksRespectOnlyTheirOwner(){InstrumentationRegistry.getInstrumentation().runOnMainSync(this::adCallbacksBody);}
 void adCallbacksBody(){setup();PureSettings.setFallbackContext(a);try{
  Map<java.lang.reflect.Executable,io.github.libxposed.api.XposedInterface.Hooker> registered=new HashMap<>();
  ClassLoader loader=getClass().getClassLoader();Class<?> api=io.github.libxposed.api.XposedInterface.class;
  Object xp=java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{api},(proxy,method,args)->{
   if(!method.getName().equals("hook"))return null;java.lang.reflect.Executable target=(java.lang.reflect.Executable)args[0];
   return java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{method.getReturnType()},(b,m,values)->{if(m.getName().equals("intercept"))registered.put(target,(io.github.libxposed.api.XposedInterface.Hooker)values[0]);return null;});});
  DoubanAdPurifier.install((io.github.libxposed.api.XposedInterface)xp,loader);
  String[][] targets={{"com.qq.e.ads.splash.SplashAD","showAd"},{"com.douban.frodo.update.BetaApkChecker","check"},{"com.douban.frodo.skynet.widget.SkynetRatingDialogFragment","show"},{"com.bytedance.sdk.openadsdk.TTAdNative","loadFeedAd"},{"com.umeng.analytics.MobclickAgent","onPageEnd"}};
  for(int selected=-1;selected<5;selected++){for(int k=0;k<5;k++)flag(KEYS[k],selected==k);for(int k=0;k<5;k++){Class<?> cls=Class.forName(targets[k][0]);java.lang.reflect.Method m=cls.getDeclaredMethod(targets[k][1]);Object receiver=cls.getDeclaredConstructor().newInstance();int[] calls={0};
   io.github.libxposed.api.XposedInterface.Chain chain=(io.github.libxposed.api.XposedInterface.Chain)java.lang.reflect.Proxy.newProxyInstance(loader,new Class[]{io.github.libxposed.api.XposedInterface.Chain.class},(p,op,args)->{if(op.getName().equals("proceed"))calls[0]++;if(op.getName().equals("getThisObject"))return receiver;if(op.getName().equals("getExecutable"))return m;return null;});assertNotNull(m.toString(),registered.get(m));registered.get(m).intercept(chain);assertEquals(m.toString(),k==selected?0:1,calls[0]);}}
  assertFalse(registered.containsKey(com.qq.e.ads.rewardvideo.RewardVideoAD.class.getDeclaredMethod("loadAD")));
 }catch(Throwable t){throw new AssertionError(t);}}
 void measure(View v){v.measure(View.MeasureSpec.makeMeasureSpec(1000,1073741824),View.MeasureSpec.makeMeasureSpec(150,1073741824));v.layout(0,0,1000,150);}
 static class SlideMenuListEntryView extends LinearLayout{SlideMenuListEntryView(Context c){super(c);}}
 @Test public void sidebarSevenIndependentBothWays(){InstrumentationRegistry.getInstrumentation().runOnMainSync(this::sidebarSevenIndependentBothWaysBody);} void sidebarSevenIndependentBothWaysBody(){setup();LinearLayout panel=new com.douban.frodo.view.SlideMenuView(a);String[] names={"未成年人模式","我的背包","草稿箱","签到","帮助与反馈","社区管理中心","服务"};for(int j=0;j<7;j++){flag(KEYS[SIDEBAR_START+j],false);if(j==1)item(panel,0x7f0a0e1a);else if(j==6)item(panel,0x7f0a07f3);else{SlideMenuListEntryView row=new SlideMenuListEntryView(a);TextView label=new TextView(a);label.setText(names[j]);row.addView(label);panel.addView(row);}}
 for(int j=0;j<7;j++)for(boolean b:new boolean[]{true,false}){flag(KEYS[SIDEBAR_START+j],b);DoubanLayoutPurifier.purgeDrawerViews(panel);for(int i=0;i<7;i++)visible(panel.getChildAt(i),i!=j||!b);}}
}
