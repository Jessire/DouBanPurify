package com.douban.frodo.activity;
public class SplashActivity extends FixtureActivity {
 public void showSplashAdContainer() {}
 public void initSplashHotControl() {}
 public void showSplash() {}
 public void showSplashAd() {}
 public void initSplashFragment() {}
 public boolean focus; @Override public boolean hasWindowFocus(){return focus;}
public int redirects; public boolean failRedirect; public void gotoMainActivity(){if(failRedirect)throw new IllegalStateException(); redirects++;}
}
