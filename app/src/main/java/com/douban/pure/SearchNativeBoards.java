package com.douban.pure;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import java.io.File;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.util.*;
import io.github.libxposed.api.XposedInterface;

/** Extend only the existing search pager's native page list. UI state stays on the main thread. */
final class SearchNativeBoards {
    static final String PAGER="com.douban.frodo.search.adapter.TrendSubjectPagerAdapter";
    static final String SUBJECT_ADAPTER="com.douban.frodo.search.adapter.SearchTrendSubjectsAdapter";
    private static final WeakHashMap<Object,Boolean> managed=new WeakHashMap<>();
    private static final ThreadLocal<State> binding=new ThreadLocal<>();
    private static final WeakHashMap<ViewGroup,WeakReference<State>> landings=new WeakHashMap<>();
    private static final class State implements View.OnAttachStateChangeListener {
        final ViewGroup pager;
        Object adapter;
        View first;
        List<View> originalSides;
        boolean replaced, awaitingDisk, failed;
        SearchBoardStore.Snapshot rendered;
        Object rejectedAdapter;
        SearchBoardStore.Snapshot rejectedSnapshot;
        long rejectedGate;
        boolean rejectedFetchFailure;

        State(ViewGroup pager) {this.pager=pager;}
        public void onViewAttachedToWindow(View view) { update(pager); }
        public void onViewDetachedFromWindow(View view) { }
    }
    private static SearchBoardStore store;
    private static Context appContext;
    private static Handler main;
    private static boolean enabled;
    private static long gateGeneration;
    private static Host host;

    static void install(XposedInterface api,ClassLoader cl) {
        try {
            Host resolved=new Host(cl);
            Class<?> pagerAdapter=cl.loadClass("androidx.viewpager.widget.PagerAdapter");
            Method position=pagerAdapter.getMethod("getItemPosition",Object.class);
            Method notify=pagerAdapter.getMethod("notifyDataSetChanged");
            Class<?> fragment=cl.loadClass("com.douban.frodo.search.fragment.SearchTrendsFragment");
            Method bind=fragment.getDeclaredMethod("bindData",resolved.model,boolean.class);
            Method bindBridge=fragment.getDeclaredMethod("p",fragment,resolved.model,boolean.class);
            Method rebuild=resolved.pager.getMethod("t",resolved.model);
            Field fragmentPager=fragment.getDeclaredField("vpSubject");
            fragmentPager.setAccessible(true);
            if (bind.getReturnType()!=void.class || bindBridge.getReturnType()!=void.class
                    || !Modifier.isStatic(bindBridge.getModifiers()) || rebuild.getReturnType()!=void.class
                    || notify.getReturnType()!=void.class || position.getReturnType()!=int.class)
                throw new NoSuchMethodException("Unexpected search pager notification signatures");
            Method getAdapter=fragmentPager.getType().getMethod("getAdapter");
            api.deoptimize(rebuild); // Keep the call to PagerAdapter.notifyDataSetChanged hookable.
            api.deoptimize(bind);
            api.deoptimize(bindBridge); // 367 cached SearchHots callback invokes the private bind through p().

            // ViewPager's default POSITION_UNCHANGED leaves removed pages attached on host rebind.
            api.hook(position).intercept(chain->{
                Object adapter=chain.getThisObject();
                if(!Boolean.TRUE.equals(managed.get(adapter))) return chain.proceed();
                int index=resolved.pages(adapter).indexOf(chain.getArg(0));
                return index<0 ? -2 : index;
            });
            api.hook(notify).intercept(chain->{
                State scope=binding.get();
                Object adapter=chain.getThisObject();
                if(scope!=null && adapter==scope.adapter) {
                    try {
                        List<View> pages=resolved.pages(adapter);
                        capture(scope,pages);
                        apply(scope,pages,false); // Same native notification publishes both side cards.
                    } catch(ReflectiveOperationException|RuntimeException|LinkageError e) {
                        Log.w("DoubanPure","could not build native search side cards",e);
                    }
                }
                return chain.proceed();
            });
            api.hook(bind).intercept(chain->{
                ViewGroup pager=null;
                Object adapter=null;
                try {
                    Object view=fragmentPager.get(chain.getThisObject());
                    if(view instanceof ViewGroup && SearchLanding.isRankPager((View)view)) {
                        pager=(ViewGroup)view;
                        Object candidate=getAdapter.invoke(pager);
                        if(candidate!=null && candidate.getClass()==resolved.pager && !resolved.young()) adapter=candidate;
                    }
                } catch(ReflectiveOperationException|RuntimeException|LinkageError e) {
                    Log.w("DoubanPure","could not scope native search bind",e);
                }
                State prior=binding.get();
                State scope=null;
                if(pager!=null) {
                    syncGate(pager.getContext());
                    if(enabled) store.warm(false);
                }
                if(adapter!=null) {
                    scope=track(pager);
                    scope.adapter=adapter;
                }
                if(scope==null) binding.remove(); else binding.set(scope);
                Object result;
                try { result=chain.proceed(); }
                finally { if(prior==null) binding.remove(); else binding.set(prior); }
                if(pager!=null) {
                    update(pager);
                    SearchLanding.applyBoundVisibility(pager);
                    SearchPagerHeight.watch(pager);
                }
                return result;
            });
            host=resolved;
            Log.i("DoubanPure","search native board hooks registered (bind, notify, position)");
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e){Log.w("DoubanPure","native side boards unavailable",e);}
    }
    private static boolean allowed(Context context) {
        try {return PureSettings.shouldReplaceSearchBoards(context) && !host.young();}
        catch(ReflectiveOperationException|RuntimeException|LinkageError e) {return false;}
    }

    private static void syncGate(Context context) {
        if(host==null) return;
        if(store==null) {
            appContext=context.getApplicationContext();
            if(appContext==null) return;
            main=new Handler(Looper.getMainLooper());
            store=new SearchBoardStore(()->new File(appContext.getCacheDir(),"dbp-public-search-boards-v1"),
                    main,()->PureSettings.shouldReplaceSearchBoards(appContext),SearchNativeBoards::refreshTracked);
        }
        setGate(allowed(context));
    }

    private static void setGate(boolean value) {
        if(enabled!=value) gateGeneration++;
        enabled=value;
        if(store!=null) store.setEnabled(value);
    }

    static void prefetch(Context context) {
        syncGate(context);
        if(enabled) store.warm(false);
    }

    static void onForeground(Context context) {
        prefetch(context);
        refreshTracked();
    }

    static void onSettingsChanged(Context context,Intent intent) {
        String key=intent.getStringExtra("key");
        if(!PureSettings.KEY_REPLACE_SEARCH_BOARDS.equals(key) && !PureSettings.KEY_HIDE_SEARCH_TRENDS.equals(key)) return;
        // Honor an intervening disable even if a newer enable was already persisted.
        boolean disabling=PureSettings.KEY_HIDE_SEARCH_TRENDS.equals(key)
                ? intent.getBooleanExtra("value",false) : !intent.getBooleanExtra("value",true);
        if(disabling) setGate(false);
        syncGate(context);
        if(enabled) store.warm(false);
        refreshTracked();
    }

    private static State track(ViewGroup pager) {
        WeakReference<State> reference=landings.get(pager);
        State state=reference==null?null:reference.get();
        if(state==null) {
            state=new State(pager);
            pager.addOnAttachStateChangeListener(state);
            landings.put(pager,new WeakReference<>(state));
        }
        return state;
    }

    private static void capture(State state,List<View> pages) {
        state.first=pages.isEmpty()?null:pages.get(0);
        state.originalSides=pages.isEmpty()?Collections.emptyList():new ArrayList<>(pages.subList(1,pages.size()));
        state.replaced=false; state.awaitingDisk=false; state.rendered=null; state.failed=false;
    }

    private static final Runnable expiry=SearchNativeBoards::refreshTracked;

    static void refreshTracked() {
        if(host==null || store==null) return;
        // Also handles a worker detecting a persisted disable before the broadcast arrives.
        if(enabled && !allowed(appContext)) setGate(false);
        for(ViewGroup pager:new ArrayList<>(landings.keySet())) if(pager!=null && pager.isAttachedToWindow()) {
            update(pager);
            SearchLanding.applyBoundVisibility(pager);
        }
        main.removeCallbacks(expiry);
        SearchBoardStore.Snapshot ready=enabled ? store.current() : null;
        if(ready!=null) main.postDelayed(expiry,Math.max(1,SearchBoardStore.MAX_AGE-ready.age(SystemClock.elapsedRealtime())));
    }

    static void update(ViewGroup pager) {
        if(host==null || !SearchLanding.isRankPager(pager)) return;
        WeakReference<State> reference=landings.get(pager);
        State state=reference==null?null:reference.get();
        if(state==null || state.adapter==null || state.first==null) return;
        try {
            if(pager.getClass().getMethod("getAdapter").invoke(pager)!=state.adapter) return;
            List<View> pages=host.pages(state.adapter);
            if(pages.isEmpty() || pages.get(0)!=state.first) return; // A different host bind owns these pages.
            if(enabled && pager.isShown() && pager.getWindowVisibility()==View.VISIBLE) store.warm(false);
            apply(state,pages,true);
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) {
            Log.w("DoubanPure","could not apply native search side cards",e);
        }
    }

    private static void apply(State state,List<View> pages,boolean notify) throws ReflectiveOperationException {
        if(state.first==null || !host.isSubjectPage(state.first)) return;
        if(!enabled) {
            if(state.replaced) publish(state,pages,state.originalSides,notify);
            state.replaced=false; state.awaitingDisk=false; state.rendered=null;
            return;
        }
        SearchBoardStore.Snapshot ready=store.current();
        if(ready==null && store.checkingDisk()) {
            // A valid disk pair can be in flight. Publish the untouched first page,
            // not loading cards or old discussion; the disk callback resolves the side cards.
            if(!state.awaitingDisk) publish(state,pages,Collections.emptyList(),notify);
            state.replaced=true; state.awaitingDisk=true;
            return;
        }
        boolean failed=ready==null && store.failed();
        if(state.replaced && !state.awaitingDisk && state.rendered==ready && state.failed==failed) return;
        if(state.rejectedAdapter==state.adapter && state.rejectedGate==gateGeneration
                && state.rejectedSnapshot==ready && state.rejectedFetchFailure==failed) return;
        state.rejectedAdapter=null; state.rejectedSnapshot=null;
        List<RankFeed.Row> movie=ready==null?Collections.emptyList():ready.movie.rows;
        List<RankFeed.Row> tv=ready==null?Collections.emptyList():ready.tv.rows;
        View moviePage,tvPage;
        try {
            moviePage=host.build(state.adapter,RankFeed.Board.MOVIE,movie,failed,()->retry(state));
            tvPage=host.build(state.adapter,RankFeed.Board.TV,tv,failed,()->retry(state));
        } catch(ReflectiveOperationException|RuntimeException|LinkageError e) {
            // Do not rebuild or log on every layout, foreground event or host rebind.
            state.rejectedAdapter=state.adapter; state.rejectedSnapshot=ready;
            state.rejectedGate=gateGeneration; state.rejectedFetchFailure=failed;
            Log.w("DoubanPure","native side card bind rejected; keeping original discussion",e);
            if(state.replaced) publish(state,pages,state.originalSides,notify);
            state.replaced=false; state.awaitingDisk=false; state.rendered=null;
            return;
        }
        publish(state,pages,Arrays.asList(moviePage,tvPage),notify);
        state.replaced=true; state.awaitingDisk=false; state.rendered=ready; state.failed=failed;
    }

    private static void retry(State state) {
        if(!state.replaced || !state.failed || !state.pager.isAttachedToWindow()
                || !state.pager.isShown() || !SearchLanding.isRankPager(state.pager)) return;
        try {
            if(state.pager.getClass().getMethod("getAdapter").invoke(state.pager)!=state.adapter) return;
        } catch(ReflectiveOperationException|RuntimeException e) {return;}
        syncGate(state.pager.getContext());
        if(enabled) store.warm(true);
        refreshTracked();
    }

    private static void publish(State state,List<View> pages,List<View> sides,boolean notify) throws ReflectiveOperationException {
        List<View> before=new ArrayList<>(pages);
        keepFirstPage(pages);
        pages.addAll(sides);
        // getItemPosition must also run when restoring host objects into previously managed slots.
        managed.put(state.adapter,true);
        if(!notify) return;
        try {host.notifyChanged.invoke(state.adapter);}
        catch(ReflectiveOperationException|RuntimeException e) {
            pages.clear(); pages.addAll(before);
            host.notifyChanged.invoke(state.adapter);
            throw e;
        }
        View indicator=SearchLanding.sibling(state.pager,"pageIndicatorView");
        if(indicator!=null) {indicator.requestLayout(); indicator.invalidate();}
        state.pager.requestLayout();
    }

    static <T> void keepFirstPage(List<T> pages) {if(pages.size()>1) pages.subList(1,pages.size()).clear();}

    static final class Host {
        final Class<?> pager, subjectView, rowAdapter, model;
        final Constructor<?> item, rating, hots;
        final Field pageList, subjects, ratingField, youngInstance;
        final Method inflate, format, header, content, nav, youngMode, notifyChanged;
        final Field[] fields, ratingFields;
        Host(ClassLoader cl) throws ReflectiveOperationException {
            String search="com.douban.frodo.search.";
            pager=cl.loadClass(PAGER); subjectView=cl.loadClass(search+"view.SearchTrendSubjectView");rowAdapter=cl.loadClass(SUBJECT_ADAPTER);
            model=cl.loadClass(search+"model.SearchHots");
            Class<?> row=cl.loadClass(search+"model.SearchSubject");
            item=row.getConstructor();hots=model.getConstructor(Parcel.class);
            subjects=model.getField("subjects"); ratingField=row.getField("rating");rating=ratingField.getType().getConstructor();
            fields=fields(row,"id","type","title","uri","coverUrl","cardSubtitle","noRatingReason","rankValue","isNew","trendUp","trendEqual","trendDown");
            ratingFields=fields(ratingField.getType(),"value","starCount","count","max");
            pageList=pager.getDeclaredField("k");pageList.setAccessible(true);
            inflate=pager.getMethod("r",model);format=pager.getMethod("q",String.class,String.class);
            header=subjectView.getMethod("getTvHeader");content=subjectView.getMethod("getLvContent");
            nav=cl.loadClass("vwc").getDeclaredMethod("r",Context.class,String.class); nav.setAccessible(true);
            Class<?> young=cl.loadClass("com.douban.frodo.baseproject.young.YoungHelper");
            youngInstance=young.getDeclaredField("a"); youngInstance.setAccessible(true);
            youngMode=young.getDeclaredMethod("A"); youngMode.setAccessible(true);
            notifyChanged=pager.getMethod("notifyDataSetChanged");
            if (inflate.getReturnType()!=View.class || nav.getReturnType()!=boolean.class || youngMode.getReturnType()!=boolean.class || pageList.getType()!=List.class)
                throw new NoSuchMethodException("Unexpected 367 signatures");
        }
        static Field[] fields(Class<?> type,String... names) throws NoSuchFieldException {
            Field[] out=new Field[names.length];for(int i=0;i<names.length;i++)out[i]=type.getField(names[i]);return out;
        }
        @SuppressWarnings("unchecked") List<View> pages(Object adapter) throws IllegalAccessException {return (List<View>)pageList.get(adapter);}
        boolean young() throws ReflectiveOperationException {return (Boolean)youngMode.invoke(youngInstance.get(null));}
        Object listAdapter(View page) throws ReflectiveOperationException {
            View child=SearchPagerHeight.find((ViewGroup)page); if(child==null)return null;
            Object rv=content.invoke(child);return rv.getClass().getMethod("getAdapter").invoke(rv);
        }
        boolean isSubjectPage(View page) throws ReflectiveOperationException {return page instanceof ViewGroup && rowAdapter.isInstance(listAdapter(page));}
        Object map(RankFeed.Row row) throws ReflectiveOperationException {
            Object itemModel=item.newInstance();
            Object[] values={row.id,row.type,row.title,row.uri,row.cover,row.subtitle,row.noRating,Integer.toString(row.rank),row.isNew,row.up,row.equal,row.down};
            for(int i=0;i<fields.length;i++)fields[i].set(itemModel,values[i]);
            if(row.rating>0){
                Object r=rating.newInstance();Object[] rValues={row.rating,row.stars,row.votes,row.max};
                for(int i=0;i<ratingFields.length;i++)ratingFields[i].set(r,rValues[i]);ratingField.set(itemModel,r);
            }
            return itemModel;
        }
        View build(Object adapter,RankFeed.Board board,List<RankFeed.Row> rows,boolean failed,Runnable retry) throws ReflectiveOperationException {
            List<Object> mapped=new ArrayList<>();for(RankFeed.Row row:rows)mapped.add(map(row));
            Parcel p=Parcel.obtain();Object model;
            try {
                // 367 SearchHots(Parcel): 6 typed lists, subtitle, ad, groupSubtitle, 2 typed lists.
                for(int i=0;i<6;i++)p.writeInt(-1);
                p.writeString(null);p.writeParcelable((Parcelable)null,0);p.writeString(null);p.writeInt(-1);p.writeInt(-1);
                p.setDataPosition(0);model=hots.newInstance(p);
            } finally {p.recycle();}
            subjects.set(model,mapped);
            ViewGroup page=(ViewGroup)inflate.invoke(adapter,model);
            Object list=listAdapter(page);
            int retained=rowAdapter.isInstance(list) ? (Integer)rowAdapter.getMethod("getItemCount").invoke(list) : -1;
            if(retained!=mapped.size())
                throw new IllegalStateException("Host native bind did not retain all rows: "+board+
                        " expected="+mapped.size()+" actual="+retained);
            TextView title=(TextView)header.invoke(SearchPagerHeight.find(page));
            if(mapped.isEmpty()) {
                title.setText((CharSequence)format.invoke(adapter,board.title,failed ? "加载失败 · 重试" : "加载中…"));
                title.setOnClickListener(failed ? view->retry.run() : null);
                title.setClickable(failed);
                return page;
            }
            title.setText((CharSequence)format.invoke(adapter,board.title,"Top"+mapped.size()));
            title.setOnClickListener(v->{
                try {nav.invoke(null,v.getContext(),board.uri());}
                catch(ReflectiveOperationException|RuntimeException e){Log.w("DoubanPure","official rank navigation unavailable",e);}
            });
            return page;
        }
    }
}
