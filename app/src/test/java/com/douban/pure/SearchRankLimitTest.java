package com.douban.pure;
import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
public class SearchRankLimitTest {
 @Test public void nativeOrderIdentityAndBoundaries(){
  for(int size: new int[]{0,2,3,4,5,9,10,11,40}){
   List<Object> data=new ArrayList<>(); for(int i=0;i<size;i++) data.add(new Object());
   for(int limit:new int[]{3,Integer.MAX_VALUE}){
    List<?> nativeRows=data.subList(0,Math.min(size,limit));
    Collection<?> out=SearchRankLimit.rows(data,nativeRows);
    assertEquals(data.subList(0,Math.min(size,10)),new ArrayList<>(out));
    if(out.size()==nativeRows.size()) assertSame(nativeRows,out);
    assertEquals(size,data.size());
   }
  }
 }
}
