package com.douban.frodo.search.view;
import android.content.Context;
import android.widget.FrameLayout;
import com.douban.frodo.search.adapter.SearchTrendSubjectsAdapter;
public class SearchTrendSubjectView extends FrameLayout {
 public static class Rows extends FrameLayout { public final SearchTrendSubjectsAdapter adapter=new SearchTrendSubjectsAdapter(); Rows(Context c){super(c);} public SearchTrendSubjectsAdapter getAdapter(){return adapter;} }
 public final Rows rv;
 private final android.widget.TextView header;
 public SearchTrendSubjectView(Context c){super(c); rv=new Rows(c); addView(rv); header=new android.widget.TextView(c); addView(header);}
 public Rows getLvContent(){return rv;}
 public android.widget.TextView getTvHeader(){return header;}
}
