package com.douban.pure;

import android.app.Activity;
import android.content.Intent;
import android.os.Message;
import com.douban.frodo.activity.SplashActivity;
import io.github.libxposed.api.XposedInterface;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class AdHookAuditTest {
 final Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();
 SplashActivity home; Object pass=new Object();
 @Before public void setup(){home=Robolectric.buildActivity(SplashActivity.class).setup().get(); PureSettings.setFallbackContext(home);
 XposedInterface api=mock(XposedInterface.class); when(api.hook(any(Executable.class))).thenAnswer(a->{Executable m=a.getArgument(0);XposedInterface.HookBuilder b=mock(XposedInterface.HookBuilder.class);when(b.intercept(any())).thenAnswer(h->{hooks.put(m,h.getArgument(0));return null;});return b;});DoubanAdPurifier.install(api,getClass().getClassLoader());}
 XposedInterface.Chain chain(Object obj, Method m) throws Throwable {XposedInterface.Chain c=mock(XposedInterface.Chain.class);when(c.getThisObject()).thenReturn(obj);when(c.getExecutable()).thenReturn(m);when(c.proceed()).thenReturn(pass);return c;}
 void flags(boolean on){for(String key:new String[]{PureSettings.KEY_SKIP_SPLASH,PureSettings.KEY_BLOCK_RATING_DIALOG,PureSettings.KEY_BLOCK_BETA_UPDATE,PureSettings.KEY_BLOCK_UMENG,PureSettings.KEY_BLOCK_FEED_AD}) PureSettings.setBoolean(home,key,on);}
 @Test public void hotResumeOnlyPopsOverlayWhenEnabled() {
   Activity hot=Robolectric.buildActivity(com.douban.frodo.splash.SplashActivityHot.class).setup().get();
   flags(false); assertFalse(DoubanPureHook.finishHotSplashIfEnabled(hot)); assertFalse(hot.isFinishing());
   flags(true); assertFalse(DoubanPureHook.finishHotSplashIfEnabled(home)); assertTrue(DoubanPureHook.finishHotSplashIfEnabled(hot));
   assertTrue(hot.isFinishing()); assertFalse(home.isFinishing()); assertEquals(0,home.redirects);
 }
 @Test public void syntheticTargetsBothSettings() throws Throwable {
 for(boolean on:new boolean[]{false,true}){flags(on);int n=0;try(BufferedReader reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/ad-audit.tsv")))){String line;while((line=reader.readLine())!=null){n++;String[] v=line.split("\t"); Class<?> cls=Class.forName(v[0]); Method m=Arrays.stream(cls.getDeclaredMethods()).filter(x->x.getName().equals(v[1])).findFirst().get();Object obj=cls==SplashActivity.class?home:Activity.class.isAssignableFrom(cls)?Robolectric.buildActivity(cls.asSubclass(Activity.class)).get():cls.getDeclaredConstructor().newInstance(); XposedInterface.Hooker h=hooks.get(m);if(v[2].equals("unowned")){assertNull(line,h);continue;}assertNotNull(line,h);XposedInterface.Chain c=chain(obj,m);Message msg=Message.obtain();msg.what=1;msg.arg1=17;
 if(v[2].equals("message"))when(c.getArg(0)).thenReturn(msg);if(v[2].equals("argctx"))when(c.getArg(0)).thenReturn(home);
 Object got=h.intercept(c);boolean blocked=on;
 if(v[2].equals("message")){assertEquals(line,on?0:17,msg.arg1);assertSame(pass,got);msg.what=2;msg.arg1=17;h.intercept(c);assertEquals(17,msg.arg1);continue;}
 if(v[2].equals("durationArg")&&on){verify(c).proceed(org.mockito.AdditionalMatchers.aryEq(new Object[]{0}));continue;}
 if(v[2].equals("finish"))assertEquals(line,on,((Activity)obj).isFinishing());
 if(v[2].equals("redirect"))assertEquals(line,on?1:0,home.redirects);
 if(!blocked){assertSame(line,pass,got);verify(c).proceed();}else{Class<?> type=m.getReturnType();Object empty=type.isPrimitive()&&type!=void.class?Array.get(Array.newInstance(type,1),0):null;assertEquals(line,empty,got);verify(c,never()).proceed();}
 }}assertEquals(127,n);}}
 @Test public void recoveryOnlyAfterEightSecondsForFocusedEmptyHome() throws Exception {
   for(boolean skip:new boolean[]{true,false})for(boolean focused:new boolean[]{false,true})for(boolean empty:new boolean[]{false,true}){
    SplashActivity a=Robolectric.buildActivity(SplashActivity.class).setup().get(); a.focus=focused;
    android.widget.LinearLayout pager=new android.widget.LinearLayout(a){public android.content.res.Resources getResources(){return fake==null?super.getResources():fake;}};
    fake=new android.content.res.Resources(a.getAssets(),a.getResources().getDisplayMetrics(),a.getResources().getConfiguration()){public String getResourceEntryName(int id){return id==871?"view_pager":super.getResourceEntryName(id);}}; pager.setId(871);
    if(!empty)pager.addView(new android.view.View(a));a.setContentView(pager);PureSettings.setBoolean(a,PureSettings.KEY_SKIP_SPLASH,skip);
    DoubanAdPurifier.maybeRecoverBlankHome(a);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(7999));assertEquals(0,a.redirects);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1));assertEquals(!skip&&focused&&empty?1:0,a.redirects);fake=null;
   }
 }
 android.content.res.Resources fake;
 @Test public void failedRedirectPassesOriginal() throws Throwable {flags(true);home.failRedirect=true;Method m=SplashActivity.class.getMethod("initSplashFragment");assertSame(pass,hooks.get(m).intercept(chain(home,m)));}
 @Test public void explicitImplicitAndUnrelatedLaunchesStayIndependent() throws Throwable {Method m=Activity.class.getMethod("startActivity",Intent.class);for(boolean beta:new boolean[]{false,true})for(boolean splash:new boolean[]{false,true}){PureSettings.setBoolean(home,PureSettings.KEY_BLOCK_BETA_UPDATE,beta);PureSettings.setBoolean(home,PureSettings.KEY_SKIP_SPLASH,splash);for(String name:new String[]{"BetaApkDialogActivity","UpgradeActivity","SplashActivityHot","openadsdk.Ad","TTDelegateActivity","RegularActivity","implicit","null"}){Intent intent=name.equals("null")?null:new Intent();if(intent!=null&&!name.equals("implicit"))intent.setClassName("fixture",name);XposedInterface.Chain c=chain(home,m);when(c.getArg(0)).thenReturn(intent);boolean block=beta&&(name.startsWith("Beta")||name.startsWith("Upgrade"));assertSame(name,block?null:pass,hooks.get(m).intercept(c));}}}
}
