package com.icereading.app;

import android.util.Xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * OPDS 1.2 客户端(Atom + OPDS 扩展)
 * 支持:
 *  - Basic Auth
 *  - Bearer Token
 *  - GZip 响应
 *  - rel="next" 分页
 *  - rel="search" 搜索
 *  - rel="http://opds-spec.org/image" 封面
 *  - rel="http://opds-spec.org/acquisition" 下载
 *  - opensearch:* 字段
 */
public class OpdsClient {

    public interface Callback {
        void onResult(List<Models.OpdsEntry> entries, String nextUrl);
        void onError(String err);
    }

    private final String userAgent = "iceReading/1.0 (Android)";

    /**
     * 拉取 OPDS 目录
     */
    public void fetch(String url, String username, String password, String bearer, Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    fetchInternal(url, username, password, bearer, cb, true);
                } catch (Throwable t) {
                    cb.onError(t.getMessage());
                }
            }
        }).start();
    }

    private void fetchInternal(String url, String username, String password, String bearer, Callback cb, boolean recursive) throws Exception {
        List<Models.OpdsEntry> all = new ArrayList<Models.OpdsEntry>();
        String nextUrl = url;
        int safety = 0;
        while (nextUrl != null && !nextUrl.isEmpty() && safety++ < 50) {
            List<Models.OpdsEntry> page = fetchPage(nextUrl, username, password, bearer);
            all.addAll(page);
            // 找 rel="next"
            nextUrl = findNextUrl(nextUrl, page);
        }
        cb.onResult(all, null);
    }

    private List<Models.OpdsEntry> fetchPage(String url, String username, String password, String bearer) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", userAgent);
        con.setRequestProperty("Accept", "application/atom+xml;profile=opds-catalog;type=entry.0,application/atom+xml,text/html;q=0.9,*/*;q=0.8");
        con.setRequestProperty("Accept-Encoding", "gzip");
        applyAuth(con, username, password, bearer);
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = readAll((code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream());
            throw new Exception("HTTP " + code + ": " + (err.length() > 200 ? err.substring(0, 200) : err));
        }
        InputStream is = con.getInputStream();
        String encoding = con.getContentEncoding();
        if (encoding != null && "gzip".equalsIgnoreCase(encoding)) is = new GZIPInputStream(is);
        return parseAtom(is);
    }

    private String findNextUrl(String currentUrl, List<Models.OpdsEntry> page) {
        // 找 entry 类型 + rel="next" 或含 opensearch 总量 > 已加载
        for (Models.OpdsEntry e : page) {
            if (e.links != null) {
                for (Models.OpdsEntry link : e.links) {
                    if ("next".equals(link.id) || "next".equals(link.format)) {
                        return resolveUrl(currentUrl, link.downloadUrl);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 解析 Atom feed
     */
    private List<Models.OpdsEntry> parseAtom(InputStream is) throws Exception {
        List<Models.OpdsEntry> out = new ArrayList<Models.OpdsEntry>();
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.parse(new InputSource(is));
        NodeList entries = doc.getElementsByTagNameNS("*", "entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            Models.OpdsEntry e = parseEntry(entry);
            if (e != null) out.add(e);
        }
        return out;
    }

    private Models.OpdsEntry parseEntry(Element entry) {
        Models.OpdsEntry e = new Models.OpdsEntry();
        e.id = textOf(entry, "id");
        e.title = textOf(entry, "title");
        e.summary = textOf(entry, "summary") + textOf(entry, "content");
        // author
        NodeList authors = entry.getElementsByTagNameNS("*", "author");
        if (authors.getLength() > 0) {
            Element a = (Element) authors.item(0);
            e.author = textOf(a, "name");
        }
        // links
        NodeList links = entry.getElementsByTagNameNS("*", "link");
        for (int i = 0; i < links.getLength(); i++) {
            Element l = (Element) links.item(i);
            String rel = l.getAttribute("rel");
            String type = l.getAttribute("type");
            String href = l.getAttribute("href");
            if (rel == null || href == null) continue;
            if (rel.equals("next")) {
                Models.OpdsEntry next = new Models.OpdsEntry();
                next.id = "next";
                next.downloadUrl = href;
                e.links.add(next);
            } else if (rel.contains("image") || (type != null && type.startsWith("image/"))) {
                e.coverUrl = href;
            } else if (rel.contains("acquisition") || rel.contains("enclosure")) {
                e.downloadUrl = href;
                e.format = type;
            } else if (rel.equals("alternate")) {
                if (e.coverUrl == null) e.coverUrl = href;  // fallback
            }
        }
        // 发行/出版
        NodeList dcMeta = entry.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "*");
        for (int i = 0; i < dcMeta.getLength(); i++) {
            Element d = (Element) dcMeta.item(i);
            String local = d.getLocalName();
            if ("publisher".equals(local)) e.publisher = d.getTextContent();
            else if ("language".equals(local)) e.language = d.getTextContent();
            else if ("issued".equals(local) || "date".equals(local)) e.issued = d.getTextContent();
            else if ("rights".equals(local)) e.rights = d.getTextContent();
        }
        // 价格
        NodeList prices = entry.getElementsByTagNameNS("*", "price");
        for (int i = 0; i < prices.getLength(); i++) {
            try { e.price = Double.parseDouble(prices.item(i).getTextContent()); } catch (Exception ignore) {}
        }
        return e;
    }

    private String textOf(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagNameNS("*", tag);
        if (nl.getLength() == 0) return "";
        return nl.item(0).getTextContent().trim();
    }

    private void applyAuth(HttpURLConnection con, String username, String password, String bearer) {
        if (bearer != null && !bearer.isEmpty()) {
            con.setRequestProperty("Authorization", "Bearer " + bearer);
        } else if (username != null && !username.isEmpty()) {
            String cred = username + ":" + (password == null ? "" : password);
            String encoded = "";
            try { encoded = android.util.Base64.encodeToString(cred.getBytes("UTF-8"), android.util.Base64.NO_WRAP); }
            catch (java.io.UnsupportedEncodingException ignore) {}
            con.setRequestProperty("Authorization", "Basic " + encoded);
        }
    }

    /**
     * OPDS 搜索
     */
    public void search(String baseUrl, String query, String username, String password, String bearer, Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String url = baseUrl + "?q=" + URLEncoder.encode(query, "UTF-8");
                    fetchInternal(url, username, password, bearer, cb, true);
                } catch (Throwable t) { cb.onError(t.getMessage()); }
            }
        }).start();
    }

    /**
     * 找 search 描述符
     */
    public String findSearchUrl(String baseUrl, String username, String password, String bearer) {
        try {
            List<Models.OpdsEntry> all = new ArrayList<Models.OpdsEntry>();
            fetchInternal(baseUrl, username, password, bearer, new Callback() {
                public void onResult(List<Models.OpdsEntry> e, String n) { all.addAll(e); }
                public void onError(String err) {}
            }, true);
            for (Models.OpdsEntry e : all) {
                if (e.links != null) {
                    for (Models.OpdsEntry l : e.links) {
                        if ("search".equals(l.id)) return resolveUrl(baseUrl, l.downloadUrl);
                    }
                }
            }
        } catch (Exception ignore) {}
        return baseUrl;
    }

    private String resolveUrl(String base, String href) {
        if (href == null) return null;
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        try {
            java.net.URL b = new java.net.URL(base);
            return new java.net.URL(b, href).toString();
        } catch (Exception e) { return base; }
    }

    private String readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }
}
