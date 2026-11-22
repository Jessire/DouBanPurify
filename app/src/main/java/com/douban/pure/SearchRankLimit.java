package com.douban.pure;

import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import io.github.libxposed.api.XposedInterface;

/** Change only the data handed to a 367 native row adapter during its own pager bind. */
final class SearchRankLimit {
    private static final ThreadLocal<Scope> active = new ThreadLocal<>();
    private static final class Scope {
        final Class<?> adapter;
        final List<?> rows;
        Scope(Class<?> adapter, List<?> rows) { this.adapter = adapter; this.rows = rows; }
    }
    static Collection<?> rows(List<?> source, Collection<?> nativeRows) {
        int n = Math.min(10, source.size());
        return n == nativeRows.size() ? nativeRows : new ArrayList<>(source.subList(0, n));
    }
    static void install(XposedInterface api, ClassLoader cl) {
        String pkg = "com.douban.frodo.search.";
        try {
            Class<?> pager = cl.loadClass(pkg + "adapter.TrendSubjectPagerAdapter");
            Class<?> model = cl.loadClass(pkg + "model.SearchHots");
            Class<?> view = cl.loadClass(pkg + "view.SearchTrendSubjectView");
            // Resolve the host class by identity. R8 can fold a class-name comparison
            // against the module's own, shrinkable Kotlin runtime.
            Class<?> hostUnit = cl.loadClass("kotlin.Unit");
            Class<?> base = cl.loadClass("com.douban.frodo.baseproject.view.newrecylview.RecyclerArrayAdapter");
            Method add = base.getDeclaredMethod("addAll$default", base, Collection.class, boolean.class, int.class, Object.class);
            if (!Modifier.isStatic(add.getModifiers()) || add.getReturnType() != void.class) return;
            api.hook(add).intercept(chain -> {
                Scope scope = active.get();
                Object adapter = chain.getArg(0), original = chain.getArg(1);
                if (scope != null && adapter != null && adapter.getClass() == scope.adapter && original instanceof Collection) {
                    Collection<?> replacement = rows(scope.rows, (Collection<?>)original);
                    if (replacement != original) { Object[] args = chain.getArgs().toArray(); args[1] = replacement; return chain.proceed(args); }
                }
                return chain.proceed();
            });
            for (String[] spec : new String[][]{{"g", "subjects", "SearchTrendSubjectsAdapter"}, {"h", "searchBoards", "SearchHotBoardAdapter"}}) {
                Class<?> adapter = cl.loadClass(pkg + "adapter." + spec[2]);
                Field field = model.getField(spec[1]);
                Method bind = pager.getDeclaredMethod(spec[0], pager, model, view);
                if (field.getType() != List.class || !Modifier.isStatic(bind.getModifiers()) || bind.getReturnType() != hostUnit) continue;
                api.deoptimize(bind);
                api.hook(bind).intercept(chain -> {
                    Scope prior = active.get();
                    Object data = chain.getArg(1);
                    Object list = data == null ? null : field.get(data);
                    try {
                        if (list instanceof List) active.set(new Scope(adapter, (List<?>)list)); else active.remove();
                        return chain.proceed();
                    } finally { if (prior == null) active.remove(); else active.set(prior); }
                });
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) { Log.w("DoubanPure", "native rank binding unavailable", e); }
    }
}
