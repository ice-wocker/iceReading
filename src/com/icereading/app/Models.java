package com.icereading.app;

import java.util.ArrayList;
import java.util.List;

/**
 * 核心数据模型:EpubBook / EpubChapter / BookRecord / Bookmark / Highlight / Note / OpdsEntry
 * 紧凑、纯 POJO,无外部依赖
 */
public class Models {

    /**
     * 一本已解析的 EPUB
     */
    public static class EpubBook {
        public String filePath;
        public Metadata metadata = new Metadata();
        public List<EpubChapter> spine = new ArrayList<>();  // 顺序的章节
        public List<ChapterRef> manifest = new ArrayList<>();  // 完整清单
        public byte[] opfData;     // 原始 OPF 字节
        public String basePath;    // 章节内容相对路径前缀
        public String version;     // "2.0" / "3.0"

        public EpubChapter findChapter(String href) {
            for (EpubChapter c : spine) if (href.endsWith(c.href) || c.href.endsWith(href)) return c;
            return null;
        }
    }

    public static class Metadata {
        public String title = "";
        public String creator = "";     // 作者(取第一个)
        public String language = "";
        public String publisher = "";
        public String identifier = "";
        public String date = "";
        public String description = "";
        public String rights = "";
        public List<String> subjects = new ArrayList<>();
        public List<String> creators = new ArrayList<>();
    }

    public static class ChapterRef {
        public String id;
        public String href;
        public String mediaType;
        public ChapterRef(String id, String href, String mt) { this.id = id; this.href = href; mediaType = mt; }
    }

    public static class EpubChapter {
        public String id;
        public String title;
        public String href;       // 相对路径
        public int order;
        public int level;        // 嵌套层级(NCX 用)
        public String type = "text";  // text / image / audio / video
        public String parent;
        public List<EpubChapter> children = new ArrayList<>();
    }

    /**
     * 书架中的图书(数据库一行)
     */
    public static class BookRecord {
        public long id;
        public String filePath;
        public String title;
        public String author;
        public String coverPath;     // 封面图(本地缓存)
        public String language;
        public int chapters;
        public long fileSize;
        public long addedTime;
        public long lastReadTime;
        public double progress;       // 0-1
        public String lastChapterHref; // 读到哪
        public String lastChapterTitle;
        public int lastCfiPage;        // 0-based
        public String description;
        public List<String> subjects;
        public String publisher;
        public String date;
    }

    /**
     * 书签
     */
    public static class Bookmark {
        public long id;
        public long bookId;
        public String chapterHref;
        public String chapterTitle;
        public int cfiPage;
        public String cfi;             // CFI 字符串(简化版)
        public String note;
        public long createdTime;
    }

    /**
     * 高亮/批注
     */
    public static class Highlight {
        public long id;
        public long bookId;
        public String chapterHref;
        public int startOffset;
        public int endOffset;
        public String text;
        public String color;            // yellow/green/blue/pink
        public String note;
        public long createdTime;
    }

    /**
     * 阅读会话
     */
    public static class Session {
        public long id;
        public long bookId;
        public long startTime;
        public long endTime;
        public long duration;
        public int pagesRead;
        public int charsRead;
    }

    /**
     * OPDS 源(在线书库)
     */
    public static class OpdsSource {
        public long id;
        public String name;        // 显示名 "古登堡"
        public String url;         // OPDS 入口
        public String username;    // Basic Auth(可选)
        public String password;
        public String bearerToken; // Bearer Auth(可选)
        public int enabled = 1;     // 0/1
        public long addedTime;
        public String lastResult;  // 上次同步结果
        public long lastSync;
    }

    /**
     * OPDS 目录项
     */
    public static class OpdsEntry {
        public String id;
        public String title;
        public String author;
        public String summary;
        public String coverUrl;
        public String downloadUrl;  // EPUB 下载链接
        public String format;        // epub/pdf/mobi
        public String language;
        public String issued;        // 发布日期
        public String publisher;
        public double price;         // 0 表示免费
        public String rights;
        public List<OpdsEntry> links = new ArrayList<>();  // 自引用(next/acquire)
    }

    /**
     * 下载任务
     */
    public static class DownloadTask {
        public long id;
        public String url;
        public String title;
        public String filePath;     // 完成后保存路径
        public long totalBytes;
        public long downloadedBytes;
        public int status;            // 0=等待,1=下载中,2=暂停,3=完成,4=失败
        public int progress;          // 0-100
        public String errorMessage;
        public long createdTime;
        public long completedTime;
    }
}
