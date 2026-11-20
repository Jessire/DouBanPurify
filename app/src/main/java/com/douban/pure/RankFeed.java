package com.douban.pure;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.net.ssl.*;
import org.json.*;

/** Anonymous HTTPS: no host HTTP client, cookie jar, auth, or redirects. */
final class RankFeed {
    enum Board {
        SUBJECT("subject", "实时热门书影音"), MOVIE("movie", "实时热门电影"), TV("tv", "实时热门电视");
        final String type, title, collection;
        Board(String type, String title) { this.type=type; this.title=title; collection=type+"_real_time_hotest"; }
        String uri() { return "douban://douban.com/subject_collection/"+collection; }
    }
    static final class Row {
        final String id, type, title, cover, uri, subtitle, noRating;
        final boolean isNew, up, equal, down;
        final int rank, votes, max;
        final float rating, stars;
        Row(JSONObject j, int rank, Board board) throws JSONException {
            this.rank=rank; id=j.getString("id"); type=j.getString("type"); title=j.getString("title"); uri=j.getString("uri");
            if (!id.matches("[0-9]{1,20}") || !type.matches("movie|tv|book|music|game") ||
                    (board!=Board.SUBJECT && !board.type.equals(type)) || !uri.equals("douban://douban.com/"+type+"/"+id) || title.trim().isEmpty())
                throw new JSONException("Invalid ranked subject");
            JSONObject pic=j.optJSONObject("pic"), image=j.optJSONObject("cover");
            String url=pic==null ? "" : pic.optString("normal");
            if (url.isEmpty() && image!=null) url=image.optString("url");
            URI u;
            try { u=URI.create(url); } catch (IllegalArgumentException e) { throw new JSONException("Invalid cover"); }
            if (!"https".equals(u.getScheme()) || u.getHost()==null || !u.getHost().endsWith(".doubanio.com") || u.getUserInfo()!=null || u.getPort()!=-1)
                throw new JSONException("Invalid cover host");
            cover=url;
            subtitle=j.optString("type_name",board.title); noRating=j.optString("null_rating_reason", "");
            isNew=j.optBoolean("is_new"); up=j.optBoolean("trend_up"); equal=j.optBoolean("trend_equal"); down=j.optBoolean("trend_down");
            JSONObject r=j.optJSONObject("rating"); rating=r==null?0:(float)r.optDouble("value",0); stars=r==null?0:(float)r.optDouble("star_count",0);
            votes=r==null?0:r.optInt("count",0); max=r==null?10:r.optInt("max",10);
            if (!(rating>=0 && rating<=max && stars>=0 && stars<=5) || max!=10 || votes<0) throw new JSONException("Invalid rating");
        }
    }
    static List<Row> parse(String json, Board board) throws JSONException {
        JSONObject root=new JSONObject(json);
        if (!board.collection.equals(root.getJSONObject("subject_collection").getString("id")) || root.getInt("start")!=0) throw new JSONException("Wrong collection");
        JSONArray items=root.getJSONArray("subject_collection_items");
        if (items.length()==0 || items.length()>10 || root.getInt("count")!=items.length()) throw new JSONException("Invalid count");
        List<Row> rows=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.getJSONObject(i);
            if (item.getInt("rank")!=i+1) throw new JSONException("Out-of-order rank");
            Row row=new Row(item,i+1,board); if(!ids.add(row.uri)) throw new JSONException("Duplicate rank subject"); rows.add(row);
        }
        return Collections.unmodifiableList(rows);
    }
    static final int LIMIT=1024*1024;
    static List<Row> fetch(Board board) throws Exception {
        return parse(fetchJson(board,new Request()),board);
    }

    /* Closing the registered socket also interrupts connect, TLS and body reads. */
    static final class Request {
        private final BooleanSupplier allowed;
        private volatile boolean cancelled;
        private Socket socket;
        Request() {this(()->true);}
        Request(BooleanSupplier allowed) {this.allowed=allowed;}
        synchronized void attach(Socket socket) throws IOException {
            check();
            this.socket=socket;
        }
        void check() throws IOException { if(cancelled) throw new InterruptedIOException("Rank request cancelled"); }
        void checkBeforeSending() throws IOException {
            check();
            if(!allowed.getAsBoolean()) throw new InterruptedIOException("Rank replacement disabled");
            check();
        }
        synchronized void cancel() {
            cancelled=true;
            if(socket!=null) try {socket.close();} catch(IOException ignored) {}
        }
    }

    static String fetchJson(Board board,Request requestState) throws Exception {
        final String host="m.douban.com", path="/subject_collection/"+board.collection;
        SSLContext context=SSLContext.getInstance("TLS"); context.init(null,null,null);
        try (Socket tcp=new Socket(Proxy.NO_PROXY)) {
            requestState.attach(tcp);
            tcp.connect(new InetSocketAddress(host,443),5000);tcp.setSoTimeout(6500);
            try (SSLSocket socket=(SSLSocket)context.getSocketFactory().createSocket(tcp,host,443,true)) {
                SSLParameters parameters=socket.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(parameters);
                socket.setSoTimeout(6500);socket.startHandshake();
                String request="GET /rexxar/api/v2"+path+"/items?type="+board.type+"&start=0&count=10 HTTP/1.1\r\nHost: "+host+
                    "\r\nReferer: https://"+host+path+"\r\nAccept: application/json\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
                requestState.checkBeforeSending();
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
                return response(new BufferedInputStream(socket.getInputStream()));
            }
        }
    }
    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        for(int b;(b=in.read())!=-1 && bytes.size()<8192;) {
            if(b=='\r') {if(in.read()=='\n') return bytes.toString("US-ASCII");break;} bytes.write(b);
        }
        throw new IOException("Invalid HTTP line");
    }
    static String response(InputStream in) throws IOException {
        String status=line(in);
        if (!status.matches("HTTP/1\\.[01] 200( .*)?")) throw new IOException("Unexpected rank HTTP status");
        Map<String,String> headers=new HashMap<>();
        for (int i=0;;i++) {
            String l=line(in);if(l.isEmpty()) break;
            int p=l.indexOf(':'); if(p<1 || i>=80) throw new IOException("Invalid HTTP headers");
            headers.put(l.substring(0,p).toLowerCase(Locale.ROOT),l.substring(p+1).trim());
        }
        if(!headers.getOrDefault("content-encoding","identity").equalsIgnoreCase("identity")) throw new IOException("Unexpected encoding");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];
        try {
            if("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
                for(;;) {int n=Integer.parseInt(line(in).split(";",2)[0],16);if(n==0) break;copy(in,out,buf,n);if(!line(in).isEmpty()) throw new IOException("Bad chunk");}
            } else if(headers.containsKey("content-length")) copy(in,out,buf,Integer.parseInt(headers.get("content-length")));
            else for (int n;(n=in.read(buf))!=-1;) { if(n>LIMIT-out.size()) throw new IOException("Rank response too large");out.write(buf,0,n); }
        } catch(NumberFormatException e) {throw new IOException("Bad response size",e);}
        return out.toString("UTF-8");
    }
    private static void copy(InputStream in, ByteArrayOutputStream out, byte[] buf, int size) throws IOException {
        if(size<0 || size>LIMIT-out.size()) throw new IOException("Rank response too large");
        while(size>0){int n=in.read(buf,0,Math.min(size,buf.length));if(n<0)throw new EOFException();size-=n;out.write(buf,0,n);}
    }
}
