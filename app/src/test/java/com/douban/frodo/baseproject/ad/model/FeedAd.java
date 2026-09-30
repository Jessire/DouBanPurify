package com.douban.frodo.baseproject.ad.model;
// The live crash proves isValid has an integer contract, not boolean.
public class FeedAd {
    public int isValid() { return 7; }
    public boolean isBlocked() { return false; }
    public boolean getIsBlocked() { return false; }
    public boolean isAd() { return true; }
    public boolean isAvailable() { return true; }
}
