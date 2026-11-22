package com.douban.pure;

import android.content.Context;
import android.os.Parcel;
import android.content.Intent;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.douban.frodo.activity.SplashActivity;
import com.douban.frodo.search.view.SearchTrendSubjectView;
import com.douban.frodo.search.view.TouchableNestedScrollView;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class SearchNativeBoardsTest {
    private static Map<Executable,XposedInterface.Hooker> installFixture() {
        Map<Executable,XposedInterface.Hooker> hooks=new HashMap<>();
        XposedInterface api=mock(XposedInterface.class);
        when(api.hook(any(Executable.class))).thenAnswer(call->{
            XposedInterface.HookBuilder builder=mock(XposedInterface.HookBuilder.class);
            when(builder.intercept(any())).thenAnswer(h->{hooks.put(call.getArgument(0),h.getArgument(0));return null;});
            return builder;
        });
        ClassLoader loader=new ClassLoader(SearchNativeBoardsTest.class.getClassLoader()) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                switch(name) {
                    case SearchNativeBoards.PAGER: return NativeAdapter.class;
                    case "androidx.viewpager.widget.PagerAdapter": return PagerAdapter.class;
                    case "com.douban.frodo.search.fragment.SearchTrendsFragment": return Fragment.class;
                    case "com.douban.frodo.search.model.SearchHots": return Hots.class;
                    case "com.douban.frodo.search.model.SearchSubject": return Subject.class;
                    case "com.douban.frodo.baseproject.young.YoungHelper": return Young.class;
                    case "vwc": return Nav.class;
                    default: return super.loadClass(name);
                }
            }
        };
        SearchNativeBoards.install(api,loader);
        return hooks;
    }

    @Test public void pagerObserverNeverReceivesDiscussionDuringScopedHostBind() throws Throwable {
        Map<Executable,XposedInterface.Hooker> hooks=installFixture();
        XposedInterface.Hooker bind=hooks.get(Fragment.class.getDeclaredMethod("bindData",Hots.class,boolean.class));
        XposedInterface.Hooker notify=hooks.get(PagerAdapter.class.getMethod("notifyDataSetChanged"));
        XposedInterface.Hooker position=hooks.get(PagerAdapter.class.getMethod("getItemPosition",Object.class));
        assertNotNull(bind);assertNotNull(notify);assertNotNull(position);

        SplashActivity c=Robolectric.buildActivity(SplashActivity.class).setup().get();
        Fragment fragment=new Fragment(c);
        LinearLayout container=new LinearLayout(c);container.setId(0x7f0a04a8);container.addView(fragment.vpSubject);
        fragment.vpSubject.setId(0x7f0a1980);
        NativeAdapter adapter=fragment.vpSubject.adapter;
        View discussion=new View(c);
        // An identical shared pager outside of the search landing is not modified.
        scopedBind(bind,notify,fragment,new SearchTrendSubjectView(c),discussion,null);
        new TouchableNestedScrollView(c).addView(container);
        try {
            Young.a.enabled=true;
            scopedBind(bind,notify,fragment,new SearchTrendSubjectView(c),discussion,null);
        } finally {Young.a.enabled=false;}
        Queue<Runnable> disk=new ArrayDeque<>(), network=new ArrayDeque<>(), main=new ArrayDeque<>();
        AtomicBoolean failTv=new AtomicBoolean(true);
        File cache=new File(c.getCacheDir(),"test-dual-search-snapshot");
        SearchBoardStore store=new SearchBoardStore(cache,(Executor)disk::add,network::add,main::add,
                (board,request)->{
                    if(board==RankFeed.Board.TV && failTv.get()) throw new java.io.IOException("fixture failure");
                    return SearchBoardStoreTest.json(board);
                },()->PureSettings.shouldReplaceSearchBoards(c),SearchNativeBoards::refreshTracked);
        field("store").set(null,store);
        field("main").set(null,new android.os.Handler(android.os.Looper.getMainLooper()));
        field("appContext").set(null,c.getApplicationContext());

        View first=new SearchTrendSubjectView(c);
        scopedBind(bind,notify,fragment,first,discussion,"disk-pending");
        View reboundFirst=new SearchTrendSubjectView(c);
        scopedBind(bind,notify,fragment,reboundFirst,discussion,"disk-pending");
        XposedInterface.Chain stale=chain(adapter);
        when(stale.getArg(0)).thenReturn(first);
        assertEquals(-2,position.intercept(stale));
        when(stale.getArg(0)).thenReturn(reboundFirst);
        assertEquals(0,position.intercept(stale));

        c.setContentView((View)container.getParent());
        SearchNativeBoards.onForeground(c);
        assertEquals(1,disk.size());
        assertTrue(network.isEmpty());
        disk.remove().run(); main.remove().run();
        assertEquals(2,network.size());
        network.remove().run(); main.remove().run();
        assertSide(adapter.k.get(1),RankFeed.Board.MOVIE,"加载中",0);
        network.remove().run(); main.remove().run();
        assertSide(adapter.k.get(1),RankFeed.Board.MOVIE,"重试",0);
        assertSide(adapter.k.get(2),RankFeed.Board.TV,"重试",0);
        assertSame(reboundFirst,adapter.k.get(0));

        failTv.set(false);
        assertTrue(((SearchTrendSubjectView)adapter.k.get(2)).getTvHeader().performClick());
        assertEquals(2,network.size());
        assertSide(adapter.k.get(1),RankFeed.Board.MOVIE,"加载中",0);
        network.remove().run(); network.remove().run();
        set(c,PureSettings.KEY_REPLACE_SEARCH_BOARDS,false);
        assertEquals(Arrays.asList(reboundFirst,discussion),adapter.k);
        assertSame(discussion,adapter.notificationPages.get(1));
        SearchNativeBoards.onForeground(c);
        assertTrue(network.isEmpty());
        set(c,PureSettings.KEY_HIDE_SEARCH_TRENDS,true);
        set(c,PureSettings.KEY_REPLACE_SEARCH_BOARDS,true);
        assertEquals(Arrays.asList(reboundFirst,discussion),adapter.k);
        assertTrue(network.isEmpty());
        set(c,PureSettings.KEY_HIDE_SEARCH_TRENDS,false);
        assertEquals(2,network.size());
        main.remove().run(); main.remove().run(); // Completed old batch, queued before disable.
        assertNull(store.current());
        assertSide(adapter.k.get(1),RankFeed.Board.MOVIE,"加载中",0);
        network.remove().run(); network.remove().run(); main.remove().run(); main.remove().run();
        assertSide(adapter.k.get(1),RankFeed.Board.MOVIE,"Top1",1);
        assertSide(adapter.k.get(2),RankFeed.Board.TV,"Top1",1);
        int publishes=adapter.notifications;
        SearchNativeBoards.update(fragment.vpSubject);
        assertEquals(publishes,adapter.notifications);
        assertSame(reboundFirst,adapter.k.get(0));
        assertTrue(((SearchTrendSubjectView)adapter.k.get(1)).getTvHeader().performClick());
        assertEquals(RankFeed.Board.MOVIE.uri(),Nav.lastUri);

        View newest=new SearchTrendSubjectView(c), newDiscussion=new View(c);
        scopedBind(bind,notify,fragment,newest,newDiscussion,"Top1");
        set(c,PureSettings.KEY_REPLACE_SEARCH_BOARDS,false);
        assertEquals(Arrays.asList(newest,newDiscussion),adapter.k);
        View disabledFirst=new SearchTrendSubjectView(c);
        scopedBind(bind,notify,fragment,disabledFirst,newDiscussion,null);
    }

    @Test public void homePrefetchAndDiskRaceNeverNotifyEmptyLoadingCardsOverAValidPair() throws Throwable {
        Map<Executable,XposedInterface.Hooker> hooks=installFixture();
        XposedInterface.Hooker bind=hooks.get(Fragment.class.getDeclaredMethod("bindData",Hots.class,boolean.class));
        XposedInterface.Hooker notify=hooks.get(PagerAdapter.class.getMethod("notifyDataSetChanged"));
        SplashActivity home=Robolectric.buildActivity(SplashActivity.class).setup().get();
        Queue<Runnable> disk=new ArrayDeque<>(), network=new ArrayDeque<>(), main=new ArrayDeque<>();
        File cache=new File(home.getCacheDir(),"warm-dual-search-snapshot");
        long now=System.currentTimeMillis();
        RankFeed.Board movie=RankFeed.Board.MOVIE,tv=RankFeed.Board.TV;
        SearchBoardStore.write(new android.util.AtomicFile(cache),new SearchBoardStore.Snapshot(
                new SearchBoardStore.Feed(SearchBoardStoreTest.json(movie),movie),
                new SearchBoardStore.Feed(SearchBoardStoreTest.json(tv),tv),
                now-SearchBoardStore.REFRESH-1000,now,android.os.SystemClock.elapsedRealtime()));
        SearchBoardStore store=new SearchBoardStore(cache,disk::add,network::add,main::add,
                (board,request)->SearchBoardStoreTest.json(board),()->PureSettings.shouldReplaceSearchBoards(home),
                SearchNativeBoards::refreshTracked);
        field("store").set(null,store);
        field("main").set(null,new android.os.Handler(android.os.Looper.getMainLooper()));
        field("appContext").set(null,home.getApplicationContext());
        SearchNativeBoards.prefetch(home);
        SearchNativeBoards.onForeground(home);
        assertEquals("Home starts one read without a search pager",1,disk.size());
        assertTrue(network.isEmpty());
        disk.remove().run(); // Disk result exists, but is still queued behind the first host bind.

        LinearLayout container=new LinearLayout(home);container.setId(0x7f0a04a8);
        TouchableNestedScrollView root=new TouchableNestedScrollView(home);root.addView(container);
        Fragment racing=new Fragment(home);racing.vpSubject.setId(0x7f0a1980);container.addView(racing.vpSubject);
        home.setContentView(root);
        View first=new SearchTrendSubjectView(home),discussion=new View(home);
        scopedBind(bind,notify,racing,first,discussion,"disk-pending");
        main.remove().run();
        assertEquals(2,network.size()); // Stale-but-valid pair refreshes asynchronously.
        assertSame(first,racing.vpSubject.adapter.k.get(0));
        assertSide(racing.vpSubject.adapter.k.get(1),movie,"Top1",1);
        assertSide(racing.vpSubject.adapter.k.get(2),tv,"Top1",1);

        Fragment warm=new Fragment(home);warm.vpSubject.setId(0x7f0a1980);container.addView(warm.vpSubject);
        View warmFirst=new SearchTrendSubjectView(home),warmDiscussion=new View(home);
        scopedBind(bind,notify,warm,warmFirst,warmDiscussion,"Top1");
        assertEquals(0,warm.vpSubject.adapter.notifications); // No second notify to correct a loading page.
        set(home,PureSettings.KEY_REPLACE_SEARCH_BOARDS,false);
        assertEquals(Arrays.asList(warmFirst,warmDiscussion),warm.vpSubject.adapter.k);
        assertEquals(Arrays.asList(first,discussion),racing.vpSubject.adapter.k);
    }

    @Test public void rejectedNativeBindRestoresDiscussionAndDoesNotLoopOnLayoutOrRebind() throws Throwable {
        Map<Executable,XposedInterface.Hooker> hooks=installFixture();
        XposedInterface.Hooker bind=hooks.get(Fragment.class.getDeclaredMethod("bindData",Hots.class,boolean.class));
        XposedInterface.Hooker notify=hooks.get(PagerAdapter.class.getMethod("notifyDataSetChanged"));
        SplashActivity home=Robolectric.buildActivity(SplashActivity.class).setup().get();
        Queue<Runnable> disk=new ArrayDeque<>(), network=new ArrayDeque<>(), main=new ArrayDeque<>();
        File cache=new File(home.getCacheDir(),"rejected-native-search-snapshot");
        SearchBoardStore store=new SearchBoardStore(cache,disk::add,network::add,main::add,
                (board,request)->SearchBoardStoreTest.json(board),()->PureSettings.shouldReplaceSearchBoards(home),
                SearchNativeBoards::refreshTracked);
        field("store").set(null,store);
        field("main").set(null,new android.os.Handler(android.os.Looper.getMainLooper()));
        field("appContext").set(null,home.getApplicationContext());
        Fragment fragment=new Fragment(home);fragment.vpSubject.setId(0x7f0a1980);
        LinearLayout container=new LinearLayout(home);container.setId(0x7f0a04a8);container.addView(fragment.vpSubject);
        TouchableNestedScrollView root=new TouchableNestedScrollView(home);root.addView(container);home.setContentView(root);
        View first=new SearchTrendSubjectView(home),discussion=new View(home);
        NativeAdapter adapter=fragment.vpSubject.adapter;
        scopedBind(bind,notify,fragment,first,discussion,"disk-pending");
        disk.remove().run(); main.remove().run();
        assertSide(adapter.k.get(1),RankFeed.Board.MOVIE,"加载中",0);
        adapter.dropNonEmpty=true;
        network.remove().run(); network.remove().run(); main.remove().run(); main.remove().run();
        assertEquals(Arrays.asList(first,discussion),adapter.k);
        int attempts=adapter.inflations;
        SearchNativeBoards.update(fragment.vpSubject);
        SearchNativeBoards.refreshTracked();
        View rebound=new SearchTrendSubjectView(home),reboundDiscussion=new View(home);
        scopedBind(bind,notify,fragment,rebound,reboundDiscussion,null);
        assertEquals(attempts,adapter.inflations);
        adapter.dropNonEmpty=false;
        set(home,PureSettings.KEY_REPLACE_SEARCH_BOARDS,false);
        set(home,PureSettings.KEY_REPLACE_SEARCH_BOARDS,true);
        assertSame(rebound,adapter.k.get(0));
        assertSide(adapter.k.get(1),RankFeed.Board.MOVIE,"Top1",1);
        assertSide(adapter.k.get(2),RankFeed.Board.TV,"Top1",1);
        assertEquals(attempts+2,adapter.inflations);
    }

    private static Field field(String name) throws Exception {
        Field f=SearchNativeBoards.class.getDeclaredField(name);f.setAccessible(true);return f;
    }

    private static void set(Context c,String key,boolean value) {
        PureSettings.setBoolean(c,key,value);
        SearchNativeBoards.onSettingsChanged(c,new Intent(PureSettings.ACTION_SETTINGS_CHANGED)
                .putExtra("key",key).putExtra("value",value));
    }

    private static void assertSide(View view,RankFeed.Board board,String status,int rows) {
        SearchTrendSubjectView side=(SearchTrendSubjectView)view;
        String title=side.getTvHeader().getText().toString();
        assertTrue(title,title.contains(board.title));
        assertTrue(title,title.contains(status));
        assertEquals(rows,side.rv.getAdapter().getItemCount());
    }

    private static void scopedBind(XposedInterface.Hooker bind,XposedInterface.Hooker notify,
                                   Fragment fragment,View first,View discussion,String status) throws Throwable {
        NativeAdapter adapter=fragment.vpSubject.adapter;
        XposedInterface.Chain binding=chain(fragment), notification=chain(adapter);
        when(notification.proceed()).thenAnswer(call->{
            assertSame(first,adapter.k.get(0));
            if(status==null) assertEquals(Arrays.asList(first,discussion),adapter.k);
            else if(status.equals("disk-pending")) assertEquals(Collections.singletonList(first),adapter.k);
            else {
                assertEquals(3,adapter.k.size());
                assertSide(adapter.k.get(1),RankFeed.Board.MOVIE,status,status.equals("Top1")?1:0);
                assertSide(adapter.k.get(2),RankFeed.Board.TV,status,status.equals("Top1")?1:0);
                assertFalse(adapter.k.contains(discussion));
            }
            return null;
        });
        when(binding.proceed()).thenAnswer(call->{adapter.k.clear();adapter.k.add(first);adapter.k.add(discussion);return notify.intercept(notification);});
        bind.intercept(binding);verify(notification).proceed();
    }

    private static XposedInterface.Chain chain(Object receiver) {
        XposedInterface.Chain chain=mock(XposedInterface.Chain.class);
        when(chain.getThisObject()).thenReturn(receiver);
        return chain;
    }

    public static class Fragment {
        final Pager vpSubject;
        Fragment(Context context){vpSubject=new Pager(context);}
        public void bindData(Hots hots,boolean fromCache) {}
        public static void p(Fragment fragment,Hots hots,boolean fromCache) {fragment.bindData(hots,fromCache);}
    }
    public static class Pager extends FrameLayout {
        final NativeAdapter adapter;
        Pager(Context context){super(context);adapter=new NativeAdapter(context);}
        public PagerAdapter getAdapter(){return adapter;}
        @Override public int getWindowVisibility(){return View.VISIBLE;}
    }
    public static class PagerAdapter {
        int notifications;
        List<View> notificationPages;
        public void notifyDataSetChanged() {notifications++;notificationPages=new ArrayList<>(((NativeAdapter)this).k);}
        public int getItemPosition(Object item){return -1;}
    }
    public static class NativeAdapter extends PagerAdapter {
        private final List<View> k=new ArrayList<>();
        public void t(Hots hots) {}
        boolean dropNonEmpty;
        int inflations;
        private final Context context;
        NativeAdapter(Context context){this.context=context;}
        public View r(Hots hots){SearchTrendSubjectView page=new SearchTrendSubjectView(context);inflations++;page.rv.adapter.count=dropNonEmpty ? 0 : hots.subjects.size();return page;}
        public String q(String title,String count){return title+" "+count;}
    }
    public static class Hots {
        public List<Subject> subjects;
        public Hots(Parcel parcel) {}
    }
    public static class Subject {
        public String id,type,title,uri,coverUrl,cardSubtitle,noRatingReason,rankValue;
        public boolean isNew,trendUp,trendEqual,trendDown;
        public Rating rating;
    }
    public static class Rating {public float value,starCount;public int count,max;}
    public static class Young {
        public static final Young a=new Young();
        boolean enabled;
        public boolean A(){return enabled;}
    }
    public static class Nav {
        static String lastUri;
        public static boolean r(Context context,String uri){lastUri=uri;return true;}
    }
}
