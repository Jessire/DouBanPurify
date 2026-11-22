package com.douban.pure;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.*;
import kotlin.Unit;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SearchRankHostHooksTest {
    public static class Base {
        public static void addAll$default(Base adapter, Collection<?> rows, boolean notify, int mask, Object marker) {}
    }
    public static class Subjects extends Base {}
    public static class Hot extends Base {}
    public static class Card {}
    public static class Hots {
        public List<Integer> subjects = Arrays.asList(1,2,3,4,5,6,7,8,9,10,11);
        public List<Integer> searchBoards = new ArrayList<>(subjects);
    }
    public static class Pager {
        public static Unit g(Pager pager, Hots data, Card card) { return Unit.INSTANCE; }
        public static Unit h(Pager pager, Hots data, Card card) { return Unit.INSTANCE; }
    }

    @Test public void resolvesHostUnitAndReplacesThreeWithTenOnlyInsideEachNativeBind() throws Throwable {
        Map<Executable,XposedInterface.Hooker> hooks = new HashMap<>();
        XposedInterface api = mock(XposedInterface.class);
        when(api.hook(any(Executable.class))).thenAnswer(call -> {
            XposedInterface.HookBuilder builder = mock(XposedInterface.HookBuilder.class);
            when(builder.intercept(any())).thenAnswer(h -> { hooks.put(call.getArgument(0),h.getArgument(0)); return null; });
            return builder;
        });
        List<String> requested = new ArrayList<>();
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                requested.add(name);
                switch(name) {
                    case SearchNativeBoards.PAGER: return Pager.class;
                    case "com.douban.frodo.search.model.SearchHots": return Hots.class;
                    case "com.douban.frodo.search.view.SearchTrendSubjectView": return Card.class;
                    case "com.douban.frodo.baseproject.view.newrecylview.RecyclerArrayAdapter": return Base.class;
                    case SearchNativeBoards.SUBJECT_ADAPTER: return Subjects.class;
                    case "com.douban.frodo.search.adapter.SearchHotBoardAdapter": return Hot.class;
                    default: return super.loadClass(name);
                }
            }
        };
        SearchRankLimit.install(api,loader);
        assertTrue(requested.contains("kotlin.Unit"));
        XposedInterface.Hooker add = hooks.get(Base.class.getMethod("addAll$default",Base.class,Collection.class,boolean.class,int.class,Object.class));
        assertNotNull(add);
        for(String name : Arrays.asList("g","h")) {
            Method method = Pager.class.getMethod(name,Pager.class,Hots.class,Card.class);
            verify(api).deoptimize(method);
            assertNotNull(hooks.get(method));
            Hots hots = new Hots();
            List<Integer> source = name.equals("g") ? hots.subjects : hots.searchBoards;
            List<Integer> nativeThree = source.subList(0,3);
            Base target = name.equals("g") ? new Subjects() : new Hot();
            Base unrelated = name.equals("g") ? new Hot() : new Subjects();
            XposedInterface.Chain insert = insertion(target,nativeThree);
            XposedInterface.Chain other = insertion(unrelated,nativeThree);
            XposedInterface.Chain bind = mock(XposedInterface.Chain.class);
            when(bind.getArg(1)).thenReturn(hots);
            when(bind.proceed()).thenAnswer(call -> {
                add.intercept(other);
                add.intercept(insert);
                return Unit.INSTANCE;
            });
            assertSame(Unit.INSTANCE,hooks.get(method).intercept(bind));
            verify(insert).proceed(argThat(args -> args[0]==target && args[1].equals(source.subList(0,10))
                    && args[2].equals(false) && args[3].equals(2) && args[4]==null));
            verify(other).proceed();
            add.intercept(insert);
            verify(insert).proceed(); // Scope is gone after host binding.
            assertEquals(11,source.size());
        }
    }

    private static XposedInterface.Chain insertion(Base target, List<?> nativeRows) {
        XposedInterface.Chain chain = mock(XposedInterface.Chain.class);
        when(chain.getArg(0)).thenReturn(target);
        when(chain.getArg(1)).thenReturn(nativeRows);
        when(chain.getArgs()).thenReturn(Arrays.asList(target,nativeRows,false,2,null));
        return chain;
    }
}
