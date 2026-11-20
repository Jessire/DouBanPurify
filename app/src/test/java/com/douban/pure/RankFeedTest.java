package com.douban.pure;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class RankFeedTest {
 private static final String JSON="{\"start\":0,\"count\":1,\"subject_collection\":{\"id\":\"movie_real_time_hotest\"},\"subject_collection_items\":[{\"id\":\"123\",\"type\":\"movie\",\"type_name\":\"电影\",\"title\":\"Fixture\",\"rank\":1,\"uri\":\"douban://douban.com/movie/123\",\"pic\":{\"normal\":\"https://img9.doubanio.com/p.jpg\"},\"rating\":{\"value\":8.9,\"max\":10,\"star_count\":4.5,\"count\":32}}]}";
 @Test public void preservesNativeBinderInputs() throws Exception {
  RankFeed.Row r=RankFeed.parse(JSON,RankFeed.Board.MOVIE).get(0);
  assertEquals("123",r.id);assertEquals("douban://douban.com/movie/123",r.uri);assertEquals("https://img9.doubanio.com/p.jpg",r.cover);
  assertEquals(8.9f,r.rating,0f);assertEquals(4.5f,r.stars,0f);assertEquals(32,r.votes);assertEquals("电影",r.subtitle);
 }
 @Test public void rejectsWrongCollectionBadRankAndForeignUrl(){
  assertThrows(JSONException.class,()->RankFeed.parse(JSON,RankFeed.Board.TV));
  assertThrows(JSONException.class,()->RankFeed.parse(JSON.replace("\"rank\":1","\"rank\":2"),RankFeed.Board.MOVIE));
  assertThrows(JSONException.class,()->RankFeed.parse(JSON.replace("img9.doubanio.com","example.org"),RankFeed.Board.MOVIE));
 }
 @Test public void keepFirstPageIsSafeForEmptyAndSinglePageLists() {
  List<Object> pages=new ArrayList<>();SearchNativeBoards.keepFirstPage(pages);assertTrue(pages.isEmpty());
  Object first=new Object();pages.add(first);SearchNativeBoards.keepFirstPage(pages);
  assertEquals(Collections.singletonList(first),pages);
 }
 @Test public void boundsHttpFraming() throws Exception {
  assertEquals("{}",read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n"));
  assertThrows(java.io.IOException.class,()->read("HTTP/1.1 302 Found\r\n\r\n"));
  assertThrows(java.io.IOException.class,()->read("HTTP/1.1 200 OK\r\nContent-Length: 1048577\r\n\r\n"));
 }
 private static String read(String x) throws Exception {return RankFeed.response(new ByteArrayInputStream(x.getBytes(StandardCharsets.UTF_8)));}
}
