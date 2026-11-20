package com.douban.pure;

import android.os.Handler;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** UI-thread coordinator. Disk and the two anonymous HTTP requests never run on the UI thread. */
final class SearchBoardStore {
    static final long REFRESH = 10 * 60_000L, MAX_AGE = 6 * 60 * 60_000L, RETRY = 60_000L;
    private static final int MAGIC = 0x44425001;

    static final class Feed {
        final String json;
        final List<RankFeed.Row> rows;
        Feed(String json, RankFeed.Board board) throws Exception {
            this.json=json;
            rows=RankFeed.parse(json,board);
        }
    }

    static final class Snapshot {
        final Feed movie, tv;
        final long savedAt, ageAtLoad, loadedAt;
        Snapshot(Feed movie, Feed tv, long savedAt, long now, long elapsed) {
            this.movie=movie; this.tv=tv; this.savedAt=savedAt; loadedAt=elapsed;
            ageAtLoad=savedAt<=0 || savedAt>now ? MAX_AGE : now-savedAt;
        }
        long age(long elapsed) { return ageAtLoad+Math.max(0,elapsed-loadedAt); }
    }

    interface Source { String fetch(RankFeed.Board board, RankFeed.Request request) throws Exception; }

    private static final class Batch {
        final RankFeed.Request movie, tv;
        Batch(BooleanSupplier allowed) {
            movie=new RankFeed.Request(allowed); tv=new RankFeed.Request(allowed);
        }
        volatile boolean cancelled;
        Feed movieFeed, tvFeed;
        void cancel() { cancelled=true; movie.cancel(); tv.cancel(); }
    }

    private final Supplier<File> path;
    private final Executor disk, network, main;
    private final Source source;
    private final BooleanSupplier allowed;
    private final Runnable changed;
    private boolean enabled, reading, diskChecked, failed;
    private long generation, tryAfter;
    private Batch batch;
    private Snapshot cached;

    SearchBoardStore(Supplier<File> path, Handler main, BooleanSupplier allowed, Runnable changed) {
        this(path, Executors.newSingleThreadExecutor(), Executors.newFixedThreadPool(2), main::post,
                RankFeed::fetchJson, allowed, changed);
    }

    SearchBoardStore(File file, Executor disk, Executor network, Executor main, Source source,
                     BooleanSupplier allowed, Runnable changed) {
        this(()->file,disk,network,main,source,allowed,changed);
    }

    private SearchBoardStore(Supplier<File> path, Executor disk, Executor network, Executor main,
                             Source source, BooleanSupplier allowed, Runnable changed) {
        this.path=path; this.disk=disk; this.network=network; this.main=main;
        this.source=source; this.allowed=allowed; this.changed=changed;
    }

    void setEnabled(boolean value) {
        if(value==enabled) return;
        enabled=value;
        generation++;
        if(!enabled) {
            if(batch!=null) batch.cancel();
            batch=null; reading=false; failed=false; tryAfter=0;
        }
    }

    Snapshot current() {
        if(cached==null) return null;
        if(cached.age(SystemClock.elapsedRealtime())>=MAX_AGE) cached=null;
        return cached;
    }

    boolean checkingDisk() { return enabled && !diskChecked; }

    boolean failed() { return failed && !reading && batch==null; }

    void warm(boolean retry) {
        if(!enabled) return;
        if(retry) {tryAfter=0; failed=false;}
        if(!diskChecked) {
            if(!reading) readDisk();
            // A disk hit younger than REFRESH needs no request; never wait for it on UI.
            return;
        }
        Snapshot ready=current();
        long now=SystemClock.elapsedRealtime();
        if(batch!=null || now<tryAfter || (ready!=null && ready.age(now)<REFRESH)) return;
        failed=false;
        Batch work=new Batch(allowed); batch=work;
        network.execute(()->fetch(work,RankFeed.Board.MOVIE,work.movie));
        network.execute(()->fetch(work,RankFeed.Board.TV,work.tv));
    }

    private void readDisk() {
        reading=true;
        long ticket=generation;
        disk.execute(()->{
            Snapshot loaded=null;
            try { loaded=read(new AtomicFile(path.get()),System.currentTimeMillis(),SystemClock.elapsedRealtime()); }
            catch(FileNotFoundException ignored) {} // Cold process without a saved pair.
            catch(Exception e) { Log.w("DoubanPure","invalid saved rank snapshot",e); }
            Snapshot done=loaded;
            main.execute(()->{
                if(!enabled || ticket!=generation) return;
                // The receiver may be queued behind us; recheck before consuming the result.
                if(!allowed.getAsBoolean()) {setEnabled(false); changed.run(); return;}
                reading=false; diskChecked=true;
                if(done!=null && current()==null) cached=done;
                warm(false);
                changed.run();
            });
        });
    }

    private void fetch(Batch work, RankFeed.Board board, RankFeed.Request request) {
        if(work.cancelled) return;
        try {
            if(!allowed.getAsBoolean()) {
                main.execute(()->complete(work,board,null));
                return;
            }
            request.check();
            Feed done=new Feed(source.fetch(board,request),board);
            main.execute(()->complete(work,board,done));
        } catch(Exception e) {
            if(!work.cancelled) {
                Log.w("DoubanPure","public rank lookup failed: "+board,e);
                main.execute(()->complete(work,board,null));
            }
        }
    }

    private void complete(Batch work, RankFeed.Board board, Feed result) {
        if(!enabled || batch!=work) return;
        if(!allowed.getAsBoolean()) {setEnabled(false); changed.run(); return;}
        if(result==null) {
            work.cancel(); batch=null; failed=true; tryAfter=SystemClock.elapsedRealtime()+RETRY;
            changed.run();
            return;
        }
        if(board==RankFeed.Board.MOVIE) work.movieFeed=result; else work.tvFeed=result;
        if(work.movieFeed==null || work.tvFeed==null) return;
        batch=null; failed=false; tryAfter=0;
        long now=System.currentTimeMillis();
        Snapshot pair=new Snapshot(work.movieFeed,work.tvFeed,now,now,SystemClock.elapsedRealtime());
        cached=pair;
        disk.execute(()->{
            try { write(new AtomicFile(path.get()),pair); }
            catch(IOException|RuntimeException e) { Log.w("DoubanPure","could not save public rank snapshot",e); }
        });
        changed.run();
    }

    static Snapshot read(AtomicFile file, long now, long elapsed) throws Exception {
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(file.openRead()))) {
            if(in.readInt()!=MAGIC) throw new IOException("Unknown rank snapshot format");
            long recorded=in.readLong();
            if(recorded<=0 || recorded>now || now-recorded>=MAX_AGE) return null;
            Feed movie=new Feed(readJson(in),RankFeed.Board.MOVIE);
            Feed tv=new Feed(readJson(in),RankFeed.Board.TV);
            if(in.read()!=-1) throw new IOException("Trailing rank snapshot data");
            return new Snapshot(movie,tv,recorded,now,elapsed);
        }
    }

    private static String readJson(DataInputStream in) throws IOException {
        int length=in.readInt();
        if(length<=0 || length>RankFeed.LIMIT) throw new IOException("Invalid saved rank length");
        byte[] bytes=new byte[length]; in.readFully(bytes);
        return new String(bytes,StandardCharsets.UTF_8);
    }

    static void write(AtomicFile file,Snapshot snapshot) throws IOException {
        FileOutputStream raw=file.startWrite();
        boolean success=false;
        try {
            DataOutputStream out=new DataOutputStream(raw);
            out.writeInt(MAGIC); out.writeLong(snapshot.savedAt);
            for(Feed feed:new Feed[]{snapshot.movie,snapshot.tv}) {
                byte[] bytes=feed.json.getBytes(StandardCharsets.UTF_8);
                if(bytes.length>RankFeed.LIMIT) throw new IOException("Rank snapshot too large");
                out.writeInt(bytes.length); out.write(bytes);
            }
            out.flush();
            success=true;
        } finally {if(success) file.finishWrite(raw); else file.failWrite(raw);}
    }
}
