package com.icereading.app;

import android.util.Xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * EPUB 2/3 解析器
 * 流程:
 *  1. 打开 ZIP
 *  2. 读 META-INF/container.xml 找 OPF
 *  3. 解析 OPF:
 *     - metadata(标题/作者/语言/标识符)
 *     - manifest(资源清单)
 *     - spine(章节顺序)
 *  4. 解析 NCX 或 NAV(目录)
 *  5. 提取章节 HTML 内容
 *  6. 提取封面
 *  7. 解压资源到缓存目录
 */
public class EpubParser {

    public interface ProgressCallback {
        void onProgress(int percent, String message);
    }

    /**
     * 解析 EPUB
     * @param filePath  .epub 文件路径
     * @param cacheDir  资源解压目录(应用 cacheDir/extract/<bookId>/)
     * @return EpubBook
     */
    public Models.EpubBook parse(String filePath, String cacheDir, ProgressCallback cb) throws Exception {
        Models.EpubBook book = new Models.EpubBook();
        book.filePath = filePath;

        notify(cb, 5, "打开 ZIP");

        ZipFile zip = new ZipFile(new File(filePath));
        try {
            // 1. 找 mimetype(可选校验)
            ZipEntry mimetype = zip.getEntry("mimetype");
            if (mimetype != null) {
                InputStream mis = zip.getInputStream(mimetype);
                byte[] mt = readAll(mis);
                String mime = new String(mt, "UTF-8").trim();
                if (!"application/epub+zip".equals(mime)) {
                    // 容忍:很多 epub 实际是 application/octet-stream
                }
            }

            // 2. 读 META-INF/container.xml
            notify(cb, 15, "解析 container.xml");
            ZipEntry container = zip.getEntry("META-INF/container.xml");
            if (container == null) throw new Exception("无效 EPUB:缺 META-INF/container.xml");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document docC = db.parse(new InputSource(zip.getInputStream(container)));
            Element rootfile = (Element) docC.getElementsByTagNameNS("*", "rootfile").item(0);
            if (rootfile == null) rootfile = (Element) docC.getElementsByTagNameNS("*", "rootfile").item(0);
            String opfPath = rootfile.getAttribute("full-path");
            if (opfPath == null || opfPath.isEmpty()) throw new Exception("container.xml 无 rootfile full-path");
            book.basePath = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
            notify(cb, 25, "OPF: " + opfPath);

            // 3. 解析 OPF
            ZipEntry opfEntry = zip.getEntry(opfPath);
            if (opfEntry == null) throw new Exception("OPF 不存在: " + opfPath);
            byte[] opfData = readAll(zip.getInputStream(opfEntry));
            book.opfData = opfData;
            Document opf = db.parse(new InputSource(new ByteArrayInputStream(opfData)));

            // 3.1 version
            Element packageEl = (Element) opf.getElementsByTagNameNS("*", "package").item(0);
            if (packageEl != null) book.version = packageEl.getAttribute("version");

            // 3.2 metadata
            Element metaEl = (Element) opf.getElementsByTagNameNS("*", "metadata").item(0);
            if (metaEl != null) parseMetadata(metaEl, book);

            // 3.3 manifest
            Element manifestEl = (Element) opf.getElementsByTagNameNS("*", "manifest").item(0);
            List<Models.ChapterRef> refs = new ArrayList<Models.ChapterRef>();
            String coverId = null;
            if (manifestEl != null) {
                NodeList items = manifestEl.getElementsByTagNameNS("*", "item");
                for (int i = 0; i < items.getLength(); i++) {
                    Element item = (Element) items.item(i);
                    String id = item.getAttribute("id");
                    String href = item.getAttribute("href");
                    String mt = item.getAttribute("media-type");
                    if (href == null || id == null) continue;
                    // 处理相对路径
                    String full = book.basePath + href;
                    refs.add(new Models.ChapterRef(id, full, mt));
                    if (mt != null && mt.startsWith("image/") && coverId == null) {
                        // 找 meta cover
                        NodeList metas = opf.getElementsByTagNameNS("*", "meta");
                        for (int j = 0; j < metas.getLength(); j++) {
                            Element me = (Element) metas.item(j);
                            if ("cover".equals(me.getAttribute("name"))) {
                                coverId = me.getAttribute("content");
                                break;
                            }
                        }
                    }
                }
            }
            book.manifest = refs;

            // 3.4 spine
            Element spineEl = (Element) opf.getElementsByTagNameNS("*", "spine").item(0);
            if (spineEl != null) {
                NodeList sp = spineEl.getElementsByTagNameNS("*", "itemref");
                int order = 0;
                for (int i = 0; i < sp.getLength(); i++) {
                    Element ir = (Element) sp.item(i);
                    String idref = ir.getAttribute("idref");
                    if (idref == null) continue;
                    Models.ChapterRef ref = findRef(refs, idref);
                    if (ref != null) {
                        Models.EpubChapter ch = new Models.EpubChapter();
                        ch.id = ref.id;
                        ch.href = ref.href;
                        ch.order = order++;
                        ch.type = "text";
                        book.spine.add(ch);
                    }
                }
            }
            // 封面
            if (coverId != null) {
                Models.ChapterRef cover = findRef(refs, coverId);
                if (cover != null) {
                    notify(cb, 30, "提取封面");
                    extractResource(zip, cover, cacheDir);
                }
            }

            // 4. NCX 或 NAV 目录
            notify(cb, 45, "解析目录");
            parseToc(zip, opf, book);

            // 5. 解压章节文件(全部)
            notify(cb, 60, "解压章节");
            File cacheRoot = new File(cacheDir);
            if (!cacheRoot.exists()) cacheRoot.mkdirs();
            for (Models.EpubChapter ch : book.spine) {
                try {
                    ZipEntry e = zip.getEntry(ch.href);
                    if (e != null) {
                        // 写到缓存,改 href 为本地路径
                        File out = new File(cacheDir, ch.href);
                        out.getParentFile().mkdirs();
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                        fos.write(readAll(zip.getInputStream(e)));
                        fos.close();
                    }
                } catch (Exception ignore) {}
            }
            // 资源也解压(图片/CSS)
            notify(cb, 85, "解压资源");
            for (Models.ChapterRef r : refs) {
                if (r.mediaType != null && !r.mediaType.startsWith("text/") && !r.mediaType.equals("application/xhtml+xml")) {
                    extractResource(zip, r, cacheDir);
                }
            }

            notify(cb, 100, "完成");
            return book;
        } finally {
            try { zip.close(); } catch (Exception ignore) {}
        }
    }

    private void parseMetadata(Element metaEl, Models.EpubBook book) {
        NodeList children = metaEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element e = (Element) n;
            String name = e.getLocalName();
            String text = e.getTextContent();
            if (text == null) text = "";
            if ("title".equals(name)) {
                if (book.metadata.title.isEmpty()) book.metadata.title = text;
                book.metadata.subjects.add(text);
            } else if ("creator".equals(name)) {
                if (book.metadata.creator.isEmpty()) book.metadata.creator = text;
                book.metadata.creators.add(text);
            } else if ("language".equals(name)) book.metadata.language = text;
            else if ("publisher".equals(name)) book.metadata.publisher = text;
            else if ("identifier".equals(name)) book.metadata.identifier = text;
            else if ("date".equals(name)) book.metadata.date = text;
            else if ("description".equals(name)) book.metadata.description = text;
            else if ("rights".equals(name)) book.metadata.rights = text;
            else if ("subject".equals(name)) book.metadata.subjects.add(text);
        }
    }

    private void parseToc(ZipFile zip, Document opf, Models.EpubBook book) throws Exception {
        // NCX(EPUB 2): 通过 spine toc 找
        Element spineEl = (Element) opf.getElementsByTagNameNS("*", "spine").item(0);
        if (spineEl != null) {
            String tocId = spineEl.getAttribute("toc");
            if (tocId != null && !tocId.isEmpty()) {
                Models.ChapterRef ncxRef = findRef(book.manifest, tocId);
                if (ncxRef != null) {
                    ZipEntry ncxE = zip.getEntry(ncxRef.href);
                    if (ncxE != null) {
                        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                        Document ncx = db.parse(new InputSource(zip.getInputStream(ncxE)));
                        parseNcxNavMap(ncx, book, 0, null);
                        if (!book.spine.isEmpty() && !book.spine.get(0).title.isEmpty()) return; // 标题已设
                    }
                }
            }
        }
        // NAV(EPUB 3): 在 manifest 中找 properties="nav" 的 item
        for (Models.ChapterRef r : book.manifest) {
            // EPUB 3 nav: properties="nav"
            // manifest item 可能有 properties 属性
            // 这里简化:遍历时查
        }
        // 兜底:从 spine 拿标题
        for (int i = 0; i < book.spine.size(); i++) {
            Models.EpubChapter ch = book.spine.get(i);
            ch.title = "第 " + (i + 1) + " 章";
        }
    }

    private void parseNcxNavMap(Document ncx, Models.EpubBook book, int level, Models.EpubChapter parent) {
        Element navMap = (Element) ncx.getElementsByTagNameNS("*", "navMap").item(0);
        if (navMap == null) return;
        NodeList points = navMap.getElementsByTagNameNS("*", "navPoint");
        for (int i = 0; i < points.getLength(); i++) {
            Element p = (Element) points.item(i);
            Models.EpubChapter ch = new Models.EpubChapter();
            Element label = (Element) p.getElementsByTagNameNS("*", "navLabel").item(0);
            if (label != null) {
                Element t = (Element) label.getElementsByTagNameNS("*", "text").item(0);
                if (t != null) ch.title = t.getTextContent();
            }
            Element content = (Element) p.getElementsByTagNameNS("*", "content").item(0);
            if (content != null) {
                String src = content.getAttribute("src");
                if (src != null) {
                    int hash = src.indexOf('#');
                    if (hash > 0) src = src.substring(0, hash);
                    ch.href = book.basePath + src;
                }
            }
            ch.level = level;
            ch.parent = parent == null ? null : parent.href;
            if (parent != null) parent.children.add(ch);
            else book.spine.add(ch);  // NCX 顺序优先
            // 递归子节点
            parseNcxNavPointChildren(p, book, level + 1, ch);
        }
    }

    private void parseNcxNavPointChildren(Element parent, Models.EpubBook book, int level, Models.EpubChapter parentCh) {
        NodeList points = parent.getElementsByTagNameNS("*", "navPoint");
        for (int i = 0; i < points.getLength(); i++) {
            Element p = (Element) points.item(i);
            Models.EpubChapter ch = new Models.EpubChapter();
            Element label = (Element) p.getElementsByTagNameNS("*", "navLabel").item(0);
            if (label != null) {
                Element t = (Element) label.getElementsByTagNameNS("*", "text").item(0);
                if (t != null) ch.title = t.getTextContent();
            }
            Element content = (Element) p.getElementsByTagNameNS("*", "content").item(0);
            if (content != null) {
                String src = content.getAttribute("src");
                if (src != null) {
                    int hash = src.indexOf('#');
                    if (hash > 0) src = src.substring(0, hash);
                    ch.href = book.basePath + src;
                }
            }
            ch.level = level;
            ch.parent = parentCh.href;
            parentCh.children.add(ch);
            parseNcxNavPointChildren(p, book, level + 1, ch);
        }
    }

    private void extractResource(ZipFile zip, Models.ChapterRef ref, String cacheDir) {
        try {
            ZipEntry e = zip.getEntry(ref.href);
            if (e == null) return;
            File out = new File(cacheDir, ref.href);
            out.getParentFile().mkdirs();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(readAll(zip.getInputStream(e)));
            fos.close();
        } catch (Exception ignore) {}
    }

    private Models.ChapterRef findRef(List<Models.ChapterRef> refs, String id) {
        for (Models.ChapterRef r : refs) if (id.equals(r.id)) return r;
        return null;
    }

    private byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    private void notify(ProgressCallback cb, int pct, String msg) {
        if (cb != null) cb.onProgress(pct, msg);
    }

    /**
     * 从 chapter href 中读取并提取纯文本(去 HTML 标签)
     */
    public static String extractText(String html) {
        if (html == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean inTag = false;
        boolean inScript = false;
        boolean inStyle = false;
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            if (c == '<') {
                inTag = true;
                // 检测 <script> / <style>
                if (i + 7 < html.length()) {
                    String sub = html.substring(i, Math.min(i + 8, html.length())).toLowerCase();
                    if (sub.startsWith("<script")) inScript = true;
                    if (sub.startsWith("<style")) inStyle = true;
                }
                continue;
            }
            if (c == '>') {
                inTag = false;
                if (inScript && i >= 8 && html.substring(i - 8, i + 1).toLowerCase().contains("</script>")) inScript = false;
                if (inStyle && i >= 8 && html.substring(i - 8, i + 1).toLowerCase().contains("</style>")) inStyle = false;
                sb.append('\n');
                continue;
            }
            if (inTag || inScript || inStyle) continue;
            if (c == '&') {
                // 简单实体
                int semi = html.indexOf(';', i);
                if (semi > 0 && semi - i < 8) {
                    String ent = html.substring(i, semi + 1);
                    if ("&amp;".equals(ent)) sb.append('&');
                    else if ("&lt;".equals(ent)) sb.append('<');
                    else if ("&gt;".equals(ent)) sb.append('>');
                    else if ("&quot;".equals(ent)) sb.append('"');
                    else if ("&apos;".equals(ent)) sb.append('\'');
                    else if ("&nbsp;".equals(ent)) sb.append(' ');
                    else sb.append(ent);
                    i = semi;
                    continue;
                }
            }
            sb.append(c);
        }
        // 合并多余空行
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }
}
