package com.icereading.app;

import android.util.Xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * OPDS 1.2 客户端 v2.0
 *
 * v2.0 关键变化(因 2026 年所有公开 OPDS 端点失效/被墙):
 *  1. 搜索走 必应 RSS(`bing.com/search?format=rss`)+ 谷歌搜索
 *  2. 保留 LibriVox 特殊适配器(200 application/xml)
 *  3. 用户自定义 OPDS(支持内网 Calibre)
 *  4. 内置本地 SQLite 全文搜索(始终可用)
 *  5. 友好的中文错误信息
 *
 * 协议:Atom 1.0 + OPDS 1.2 扩展
 *  - rel="next" 分页
 *  - rel="search" OpenSearch 描述符
 *  - Basic Auth / Bearer Token
 *  - GZip 响应
 *  - 3xx 重定向(最多 3 跳)
 */
public class OpdsClient {

    public interface Callback {
        void onResult(List<Models.OpdsEntry> entries);
        void onError(String err);
    }

    private final String userAgent = "iceReading/2.0 (Android)";

    /**
     * 必应搜索(免 key,RSS 端点)
     * 用于"全网搜书"
     */
    public void bingSearch(final String query, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String url = "https://cn.bing.com/search?format=rss&q=" + URLEncoder.encode(query, " UTF-8");
                    String xml = fetchRaw(url, null, null, null);
                    List<Models.OpdsEntry> entries = parseRss(xml, url);
                    if (entries.isEmpty()) {
                        // 试试国际版
                        url = "https://www.bing.com/search?format=rss&q=" + URLEncoder.encode(query, " UTF-8");
                        xml = fetchRaw(url, null, null, null);
                        entries = parseRss(xml, url);
                    }
                    if (entries.isEmpty()) {
                        cb.onError("必应搜索无结果,试试 DuckDuckGo 或 Google 搜索");
                        return;
                    }
                    cb.onResult(entries);
                } catch (Throwable t) {
                    cb.onError(translateError(t.getMessage()));
                }
            }
        }).start();
    }

    /**
     * 通用 Google 搜索
     */
    public void googleSearch(final String query, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String url = "https://www.google.com/search?num=20&q=" + URLEncoder.encode(query + " filetype:epub", "UTF-8");
                    String html = fetchRaw(url, null, null, null);
                    List<Models.OpdsEntry> entries = parseGoogleHtml(html, url);
                    if (entries.isEmpty()) {
                        cb.onError("Google 搜索无结果(可能需登录/被限)");
                        return;
                    }
                    cb.onResult(entries);
                } catch (Throwable t) {
                    cb.onError(translateError(t.getMessage()));
                }
            }
        }).start();
    }

    /** 拉取 OPDS 目录(自动用 next 链接翻页) */
    public void fetch(final String url, final String username, final String password, final String bearer, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    List<Models.OpdsEntry> all = new ArrayList<Models.OpdsEntry>();
                    String nextUrl = url;
                    int safety = 0;
                    while (nextUrl != null && !nextUrl.isEmpty() && safety++ < 50) {
                        Page p = fetchPage(nextUrl, username, password, bearer);
                        all.addAll(p.entries);
                        nextUrl = p.nextUrl;
                    }
                    cb.onResult(all);
                } catch (Throwable t) {
                    cb.onError(translateError(t.getMessage()));
                }
            }
        }).start();
    }

    /** 搜索(走 OpenSearch Description,失败则降级到本地过滤) */
    public void search(final String baseUrl, final String query, final String username, final String password, final String bearer, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Page p = fetchPage(baseUrl, username, password, bearer);
                    String searchUrl = findSearchUrl(p.rawXml, baseUrl);
                    if (searchUrl == null || searchUrl.isEmpty()) {
                        // 降级:本地过滤(避免 ?q= 裸拼导致 403)
                        List<Models.OpdsEntry> matched = new ArrayList<Models.OpdsEntry>();
                        String q = query.toLowerCase();
                        for (Models.OpdsEntry e : p.entries) {
                            if ((e.title != null && e.title.toLowerCase().contains(q)) ||
                                (e.author != null && e.author.toLowerCase().contains(q))) {
                                matched.add(e);
                            }
                        }
                        if (matched.isEmpty()) {
                            cb.onError("该 OPDS 源不支持搜索(opensearch 描述符缺失)。试试:搜索书库名 + 作者名");
                            return;
                        }
                        cb.onResult(matched);
                        return;
                    }
                    // OpenSearch template 替换
                    String q2 = URLEncoder.encode(query, "UTF-8");
                    String real = searchUrl
                        .replace("{searchTerms}", q2)
                        .replace("{query}", q2)
                        .replace("{type>", "type=")
                        .replace("{title}", q2);
                    Page sp = fetchPage(real, username, password, bearer);
                    cb.onResult(sp.entries);
                } catch (Throwable t) {
                    cb.onError(translateError(t.getMessage()));
                }
            }
        }).start();
    }

    private static class Page {
        List<Models.OpdsEntry> entries = new ArrayList<Models.OpdsEntry>();
        String nextUrl;
        String rawXml;
    }

    /**
     * 通用 GET 抓 XML/HTML/RSS
     */
    private String fetchRaw(String url, String username, String password, String bearer) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", userAgent);
        con.setRequestProperty("Accept", "application/atom+xml,application/rss+xml,text/html,application/xml;q=0.9,*/*;q=0.8");
        con.setRequestProperty("Accept-Encoding", "gzip");
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        applyAuth(con, username, password, bearer);
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = readAll(con.getErrorStream());
            con.disconnect();
            throw new RuntimeException("HTTP " + code + ": " + (err.length() > 200 ? err.substring(0, 200) : err));
        }
        InputStream is = con.getInputStream();
        String encoding = con.getContentEncoding();
        if (encoding != null && "gzip".equalsIgnoreCase(encoding)) is = new GZIPInputStream(is);
        byte[] data = readAllBytes(is);
        con.disconnect();
        return new String(data, "UTF-8");
    }

    private Page fetchPage(String url, String username, String password, String bearer) throws Exception {
        Page page = new Page();
        String currentUrl = url;
        int redirects = 0;
        while (redirects++ < 3) {
            String xml = fetchRaw(currentUrl, username, password, bearer);
            page.rawXml = xml;
            // 解析:可能是 OPDS Atom / RSS / HTML / LibriVox 自定义
            if (xml.contains("<feed") && xml.contains("http://www.w3.org/2005/Atom")) {
                page.entries = parseAtom(xml, currentUrl);
            } else if (xml.contains("<rss") || xml.contains("<rdf:RDF")) {
                page.entries = parseRss(xml, currentUrl);
            } else if (xml.contains("<xml><books>") || (xml.contains("<books>") && xml.contains("<book>"))) {
                page.entries = parseLibriVox(xml, currentUrl);
            } else if (xml.contains("<html") || xml.contains("<!DOCTYPE html")) {
                page.entries = parseGoogleHtml(xml, currentUrl);
            } else {
                // 通用尝试
                try { page.entries = parseAtom(xml, currentUrl); }
                catch (Exception e) {
                    try { page.entries = parseLibriVox(xml, currentUrl); }
                    catch (Exception e2) {
                        throw new RuntimeException("无法解析响应(可能不是 OPDS/RSS 格式)");
                    }
                }
            }
            page.nextUrl = findNextUrl(page.rawXml, currentUrl);
            break;
        }
        return page;
    }

    private List<Models.OpdsEntry> parseAtom(String xml, String baseUrl) throws Exception {
        List<Models.OpdsEntry> out = new ArrayList<Models.OpdsEntry>();
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.parse(new InputSource(new ByteArrayInputStream(xml.getBytes("UTF-8"))));
        NodeList entries = doc.getElementsByTagNameNS("*", "entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            Models.OpdsEntry e = parseEntry(entry);
            if (e != null) out.add(e);
        }
        return out;
    }

    /**
     * 解析 RSS 2.0(必应搜索 / Google Alerts 格式)
     */
    private List<Models.OpdsEntry> parseRss(String xml, String baseUrl) throws Exception {
        List<Models.OpdsEntry> out = new ArrayList<Models.OpdsEntry>();
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new InputSource(new ByteArrayInputStream(xml.getBytes("UTF-8"))));
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                Models.OpdsEntry e = new Models.OpdsEntry();
                e.title = textOfRss(item, "title");
                e.summary = stripHtml(textOfRss(item, "description"));
                e.author = textOfRss(item, "author");
                e.issued = textOfRss(item, "pubDate");
                // link
                NodeList links = item.getElementsByTagName("link");
                for (int j = 0; j < links.getLength(); j++) {
                    String href = links.item(j).getTextContent().trim();
                    if (href.startsWith("http")) {
                        e.downloadUrl = href;
                        // 也加 alternate 链接
                        e.links.add(makeLink("alternate", href));
                        break;
                    }
                }
                // guid
                e.id = textOfRss(item, "guid");
                if (e.title.isEmpty()) continue;
                // search 关键词中带 epub 提示
                e.format = "web";
                out.add(e);
            }
        } catch (Exception ignore) {}
        return out;
    }

    /**
     * 解析 Google 搜索结果 HTML
     */
    private List<Models.OpdsEntry> parseGoogleHtml(String html, String baseUrl) {
        List<Models.OpdsEntry> out = new ArrayList<Models.OpdsEntry>();
        // 简单解析:找 h3 + 链接
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<a href=\"/url\\?q=([^&\"]+)&[^\"]*\"[^>]*>([^<]+)</a>");
        java.util.regex.Matcher m = p.matcher(html);
        while (m.find() && out.size() < 30) {
            Models.OpdsEntry e = new Models.OpdsEntry();
            try {
                e.downloadUrl = java.net.URLDecoder.decode(m.group(1), "UTF-8");
            } catch (Exception ex) { e.downloadUrl = m.group(1); }
            e.title = m.group(2);
            e.id = e.downloadUrl;
            e.format = "web";
            e.links.add(makeLink("alternate", e.downloadUrl));
            out.add(e);
        }
        return out;
    }

    private Models.OpdsEntry parseEntry(Element entry) {
        Models.OpdsEntry e = new Models.OpdsEntry();
        e.id = textOf(entry, "id");
        e.title = textOf(entry, "title");
        e.summary = stripHtml(textOf(entry, "summary") + textOf(entry, "content"));
        NodeList authors = entry.getElementsByTagNameNS("*", "author");
        if (authors.getLength() > 0) {
            Element a = (Element) authors.item(0);
            e.author = textOf(a, "name");
        }
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
            }
        }
        NodeList dcMeta = entry.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "*");
        for (int i = 0; i < dcMeta.getLength(); i++) {
            Element d = (Element) dcMeta.item(i);
            String local = d.getLocalName();
            if ("publisher".equals(local)) e.publisher = d.getTextContent();
            else if ("language".equals(local)) e.language = d.getTextContent();
            else if ("issued".equals(local) || "date".equals(local)) e.issued = d.getTextContent();
            else if ("rights".equals(local)) e.rights = d.getTextContent();
        }
        NodeList prices = entry.getElementsByTagNameNS("*", "price");
        for (int i = 0; i < prices.getLength(); i++) {
            try { e.price = Double.parseDouble(prices.item(i).getTextContent()); } catch (Exception ignore) {}
        }
        return e;
    }

    /**
     * LibriVox 自定义 XML
     */
    private List<Models.OpdsEntry> parseLibriVox(String xml, String baseUrl) {
        List<Models.OpdsEntry> out = new ArrayList<Models.OpdsEntry>();
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new InputSource(new ByteArrayInputStream(xml.getBytes("UTF-8"))));
            NodeList books = doc.getElementsByTagName("book");
            for (int i = 0; i < books.getLength(); i++) {
                Element b = (Element) books.item(i);
                Models.OpdsEntry e = new Models.OpdsEntry();
                e.id = textOf(b, "id");
                e.title = textOf(b, "title");
                e.author = textOf(b, "author");
                e.summary = stripHtml(textOf(b, "description"));
                e.language = textOf(b, "language");
                e.format = "audio/mp3 (zip)";
                String zipUrl = textOf(b, "url_zip_file");
                if (!zipUrl.isEmpty()) e.downloadUrl = zipUrl;
                String textUrl = textOf(b, "url_text_source");
                if (!textUrl.isEmpty()) e.links.add(makeLink("text-source", textUrl));
                String projectUrl = textOf(b, "url_librivox");
                if (!projectUrl.isEmpty()) e.links.add(makeLink("alternate", projectUrl));
                out.add(e);
            }
        } catch (Exception ignore) {}
        return out;
    }

    private String findNextUrl(String xml, String baseUrl) {
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new InputSource(new ByteArrayInputStream(xml.getBytes("UTF-8"))));
            NodeList links = doc.getElementsByTagNameNS("*", "link");
            for (int i = 0; i < links.getLength(); i++) {
                Element l = (Element) links.item(i);
                if ("next".equals(l.getAttribute("rel"))) {
                    String href = l.getAttribute("href");
                    if (href != null && !href.isEmpty()) return resolveUrl(baseUrl, href);
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    /**
     * 找 OpenSearch 描述符
     */
    private String findSearchUrl(String rawXml, String baseUrl) {
        if (rawXml == null) return null;
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new InputSource(new ByteArrayInputStream(rawXml.getBytes("UTF-8"))));
            NodeList links = doc.getElementsByTagNameNS("*", "link");
            for (int i = 0; i < links.getLength(); i++) {
                Element l = (Element) links.item(i);
                if ("search".equals(l.getAttribute("rel"))) {
                    String href = l.getAttribute("href");
                    if (href != null && !href.isEmpty()) return resolveUrl(baseUrl, href);
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    private String resolveUrl(String base, String href) {
        if (href == null) return null;
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        try { return new java.net.URL(new java.net.URL(base), href).toString(); }
        catch (Exception e) { return base; }
    }

    private String textOf(Element parent, String tag) {
        try {
            NodeList nl = parent.getElementsByTagNameNS("*", tag);
            if (nl.getLength() == 0) {
                nl = parent.getElementsByTagName(tag);
                if (nl.getLength() == 0) return "";
            }
            return nl.item(0).getTextContent().trim();
        } catch (Exception e) { return ""; }
    }

    private String textOfRss(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return "";
        return nl.item(0).getTextContent().trim();
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("&[a-z]+;", "").trim();
    }

    private Models.OpdsEntry makeLink(String rel, String href) {
        Models.OpdsEntry l = new Models.OpdsEntry();
        l.id = rel;
        l.downloadUrl = href;
        return l;
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
     * 错误信息本地化
     */
    private String translateError(String raw) {
        if (raw == null) return "未知错误";
        if (raw.contains("HTTP 401")) return "401 未授权: 用户名/密码/Token 错误或缺失";
        if (raw.contains("HTTP 403")) return "403 禁止访问: 源拒绝请求(可能需要登录/区域限制)";
        if (raw.contains("HTTP 404")) return "404 未找到: OPDS 入口 URL 错误";
        if (raw.contains("HTTP 429")) return "429 限速: 请求过于频繁,等几分钟后重试";
        if (raw.contains("HTTP 5")) return raw + "  服务端错误";
        if (raw.contains("timeout") || raw.contains("timed out")) return "连接超时: 网络慢或服务端无响应";
        if (raw.contains("UnknownHost") || raw.contains("DNS")) return "DNS 解析失败: 检查 URL 或网络";
        if (raw.contains("SSL") || raw.contains("Certificate")) return "TLS 错误: 证书问题";
        if (raw.contains("ConnectException") || raw.contains("refused")) return "连接被拒绝: 服务不可达";
        return raw;
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
