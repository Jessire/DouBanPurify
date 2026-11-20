package com.douban.frodo.activity;
import android.app.Activity;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.view.*;
import com.douban.pure.SettingsProvider;
public class SplashActivity extends Activity {
 private final Context sandbox; private ContentResolver resolver;
 public SplashActivity(Context c,String prefs){sandbox=new ContextWrapper(c){@Override public SharedPreferences getSharedPreferences(String k,int m){return c.getSharedPreferences(prefs,m);}}; attachBaseContext(sandbox);SettingsProvider p=new SettingsProvider();ProviderInfo i=new ProviderInfo();i.authority=SettingsProvider.AUTHORITY;p.attachInfo(sandbox,i);resolver=ContentResolver.wrap(p);}
 @Override public android.content.res.Resources.Theme getTheme(){return sandbox.getTheme();}
 private android.content.res.Resources fixtureResources;
 @Override public android.content.res.Resources getResources(){android.content.res.Resources base=super.getResources(); if(fixtureResources==null){try{android.content.res.Resources host=sandbox.getPackageManager().getResourcesForApplication("com.douban.frodo");fixtureResources=new android.content.res.Resources(base.getAssets(),base.getDisplayMetrics(),base.getConfiguration()){@Override public String getResourceEntryName(int id){return host.getResourceEntryName(id);}};}catch(android.content.pm.PackageManager.NameNotFoundException e){throw new AssertionError(e);}}return fixtureResources;}
 @Override public Context getApplicationContext(){return this;}
 @Override public ContentResolver getContentResolver(){return resolver;}
 @Override public void sendBroadcast(Intent i){} // no communication with host
 @Override public Object getSystemService(String n){return sandbox.getSystemService(n);}
 @Override public LayoutInflater getLayoutInflater(){return LayoutInflater.from(sandbox).cloneInContext(this);}
}
