package com.douban.pure;

import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class SearchBoardStoreTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final Queue<Runnable> disk=new ArrayDeque<>(), network=new ArrayDeque<>(), main=new ArrayDeque<>();
    private final AtomicBoolean allowed=new AtomicBoolean(true);
    private final AtomicInteger requests=new AtomicInteger();

    static String json(RankFeed.Board board) throws Exception {
        return new JSONObject().put("start",0).put("count",1)
                .put("subject_collection",new JSONObject().put("id",board.collection))
                .put("subject_collection_items",new JSONArray().put(new JSONObject().put("rank",1)
                        .put("id","123").put("type",board.type).put("title",board.title)
                        .put("uri","douban://douban.com/"+board.type+"/123")
                        .put("pic",new JSONObject().put("normal","https://img9.doubanio.com/p.jpg")))).toString();
    }

    private SearchBoardStore.Snapshot snapshot(long now,long age) throws Exception {
        RankFeed.Board movie=RankFeed.Board.MOVIE,tv=RankFeed.Board.TV;
        return new SearchBoardStore.Snapshot(new SearchBoardStore.Feed(json(movie),movie),
                new SearchBoardStore.Feed(json(tv),tv),now-age,now,SystemClock.elapsedRealtime());
    }

    private SearchBoardStore store(File file,boolean failTv) {
        return new SearchBoardStore(file,disk::add,network::add,main::add,(board,request)->{
            requests.incrementAndGet();
            if(failTv && board==RankFeed.Board.TV) throw new IOException("fixture failure");
            return json(board);
        },allowed::get,()->{});
    }

    private void load(SearchBoardStore store) {
        store.setEnabled(true); store.warm(false);
        assertTrue(network.isEmpty());
        disk.remove().run(); main.remove().run();
    }

    @Test public void freshDiskPairPublishedBeforeAnyRequestAndHardExpiryChecked() throws Exception {
        File file=new File(temp.getRoot(),"pair"); AtomicFile atomic=new AtomicFile(file);
        long now=System.currentTimeMillis();
        SearchBoardStore.write(atomic,snapshot(now,1000));
        SearchBoardStore store=store(file,false);
        load(store);
        assertEquals("movie",store.current().movie.rows.get(0).type);
        assertEquals("tv",store.current().tv.rows.get(0).type);
        assertTrue(network.isEmpty());
        assertNull(SearchBoardStore.read(atomic,now+SearchBoardStore.MAX_AGE,0));
        assertNull(SearchBoardStore.read(atomic,now-1001,0));
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file,true)) {out.write(0);}
        assertThrows(IOException.class,()->SearchBoardStore.read(atomic,now,0));
    }

    @Test public void stalePairSurvivesPartialNetworkFailureAndRetryBackoff() throws Exception {
        File file=new File(temp.getRoot(),"pair"); AtomicFile atomic=new AtomicFile(file);
        long now=System.currentTimeMillis();
        SearchBoardStore.write(atomic,snapshot(now,SearchBoardStore.REFRESH+1000));
        SearchBoardStore store=store(file,true);
        load(store);
        SearchBoardStore.Snapshot stale=store.current();
        assertNotNull(stale);
        assertEquals(2,network.size());
        network.remove().run(); main.remove().run();
        assertSame(stale,store.current()); assertTrue(disk.isEmpty());
        network.remove().run(); main.remove().run();
        assertSame(stale,store.current()); assertTrue(disk.isEmpty());
        assertEquals(stale.savedAt,SearchBoardStore.read(atomic,now,0).savedAt);
        assertTrue(store.failed());
        store.warm(false); assertTrue(network.isEmpty());
        store.warm(true); assertEquals(2,network.size());
    }

    @Test public void offBeforeDispatchDoesNotFetchAndObsoleteDiskCompletionCannotLaunchRequests() {
        SearchBoardStore store=store(new File(temp.getRoot(),"missing"),false);
        store.warm(false); assertTrue(disk.isEmpty());
        store.setEnabled(true); store.warm(false);
        store.setEnabled(false); store.setEnabled(true); store.warm(false);
        disk.remove().run(); main.remove().run();
        assertTrue(network.isEmpty());
        disk.remove().run(); main.remove().run();
        assertEquals(2,network.size());
        allowed.set(false); // persisted switch, before broadcast delivery
        network.remove().run(); network.remove().run(); main.remove().run(); main.remove().run();
        assertEquals(0,requests.get()); assertNull(store.current());
        assertTrue(disk.isEmpty());
    }

    @Test public void coldPairRequiresBothBoardsAndWritesSingleSnapshotOffMain() throws Exception {
        File file=new File(temp.getRoot(),"pair");
        SearchBoardStore store=store(file,false);
        load(store); assertEquals(2,network.size());
        network.remove().run(); main.remove().run();
        assertNull(store.current()); assertFalse(file.exists()); assertTrue(disk.isEmpty());
        network.remove().run(); main.remove().run();
        assertNotNull(store.current()); assertFalse(file.exists()); assertEquals(1,disk.size());
        disk.remove().run();
        assertNotNull(SearchBoardStore.read(new AtomicFile(file),System.currentTimeMillis(),0));
    }
}
