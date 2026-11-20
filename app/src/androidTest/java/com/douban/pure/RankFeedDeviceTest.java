package com.douban.pure;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class RankFeedDeviceTest {
 @Test public void anonymousSubject() throws Exception {verify(RankFeed.Board.SUBJECT);}
 @Test public void anonymousMovie() throws Exception {verify(RankFeed.Board.MOVIE);}
 @Test public void anonymousTv() throws Exception {verify(RankFeed.Board.TV);}
 private void verify(RankFeed.Board board) throws Exception {
   java.util.List<RankFeed.Row> rows=RankFeed.fetch(board);
   assertEquals(10,rows.size());for(int i=0;i<10;i++){RankFeed.Row row=rows.get(i);assertEquals(i+1,row.rank);assertTrue(row.cover.startsWith("https://"));assertFalse(row.title.isEmpty());}
 }
}
