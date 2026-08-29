package com.icereading.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 书籍/书签/笔记/进度的 SQLite 持久化
 * 使用原生 SQLiteOpenHelper,无 Room/Realm 依赖
 * 表:books / bookmarks / highlights / sessions / reading_stats / opds_sources / opds_cache / download_tasks
 */
public class BookRepository extends SQLiteOpenHelper {

    private static final String DB_NAME = "icereading.db";
    private static final int DB_VERSION = 1;

    private static BookRepository instance;

    public static synchronized BookRepository get(Context ctx) {
        if (instance == null) instance = new BookRepository(ctx.getApplicationContext());
        return instance;
    }

    private BookRepository(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE books (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "file_path TEXT UNIQUE NOT NULL," +
                "title TEXT," +
                "author TEXT," +
                "cover_path TEXT," +
                "language TEXT," +
                "chapters INTEGER DEFAULT 0," +
                "file_size INTEGER DEFAULT 0," +
                "added_time INTEGER," +
                "last_read_time INTEGER DEFAULT 0," +
                "progress REAL DEFAULT 0," +
                "last_chapter_href TEXT," +
                "last_chapter_title TEXT," +
                "last_cfi_page INTEGER DEFAULT 0," +
                "description TEXT," +
                "subjects TEXT," +
                "publisher TEXT," +
                "date TEXT," +
                "tag TEXT DEFAULT ''" +
                ")");
        db.execSQL("CREATE INDEX idx_books_added ON books(added_time DESC)");
        db.execSQL("CREATE INDEX idx_books_last_read ON books(last_read_time DESC)");
        db.execSQL("CREATE INDEX idx_books_title ON books(title)");
        db.execSQL("CREATE INDEX idx_books_author ON books(author)");

        db.execSQL("CREATE TABLE bookmarks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "book_id INTEGER NOT NULL," +
                "chapter_href TEXT," +
                "chapter_title TEXT," +
                "cfi_page INTEGER DEFAULT 0," +
                "cfi TEXT," +
                "note TEXT," +
                "created_time INTEGER" +
                ")");
        db.execSQL("CREATE INDEX idx_bookmarks_book ON bookmarks(book_id, created_time DESC)");

        db.execSQL("CREATE TABLE highlights (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "book_id INTEGER NOT NULL," +
                "chapter_href TEXT," +
                "start_offset INTEGER," +
                "end_offset INTEGER," +
                "text TEXT," +
                "color TEXT DEFAULT 'yellow'," +
                "note TEXT," +
                "created_time INTEGER" +
                ")");
        db.execSQL("CREATE INDEX idx_highlights_book ON highlights(book_id, created_time DESC)");

        db.execSQL("CREATE TABLE sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "book_id INTEGER NOT NULL," +
                "start_time INTEGER," +
                "end_time INTEGER," +
                "duration INTEGER," +
                "pages_read INTEGER DEFAULT 0," +
                "chars_read INTEGER DEFAULT 0" +
                ")");
        db.execSQL("CREATE INDEX idx_sessions_book ON sessions(book_id, start_time DESC)");
        db.execSQL("CREATE INDEX idx_sessions_start ON sessions(start_time DESC)");

        db.execSQL("CREATE TABLE opds_sources (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "url TEXT NOT NULL," +
                "username TEXT," +
                "password TEXT," +
                "bearer_token TEXT," +
                "enabled INTEGER DEFAULT 1," +
                "added_time INTEGER," +
                "last_result TEXT," +
                "last_sync INTEGER DEFAULT 0" +
                ")");

        db.execSQL("CREATE TABLE opds_cache (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source_id INTEGER NOT NULL," +
                "entry_id TEXT NOT NULL," +
                "title TEXT," +
                "author TEXT," +
                "summary TEXT," +
                "cover_url TEXT," +
                "download_url TEXT," +
                "format TEXT," +
                "language TEXT," +
                "issued TEXT," +
                "publisher TEXT," +
                "price REAL DEFAULT 0," +
                "fetched_time INTEGER" +
                ")");
        db.execSQL("CREATE UNIQUE INDEX idx_opds_cache ON opds_cache(source_id, entry_id)");

        db.execSQL("CREATE TABLE download_tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "url TEXT NOT NULL," +
                "title TEXT," +
                "file_path TEXT," +
                "total_bytes INTEGER DEFAULT 0," +
                "downloaded_bytes INTEGER DEFAULT 0," +
                "status INTEGER DEFAULT 0," +
                "progress INTEGER DEFAULT 0," +
                "error_message TEXT," +
                "created_time INTEGER," +
                "completed_time INTEGER" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS books");
        db.execSQL("DROP TABLE IF EXISTS books_fts");
        db.execSQL("DROP TABLE IF EXISTS bookmarks");
        db.execSQL("DROP TABLE IF EXISTS highlights");
        db.execSQL("DROP TABLE IF EXISTS sessions");
        db.execSQL("DROP TABLE IF EXISTS opds_sources");
        db.execSQL("DROP TABLE IF EXISTS opds_cache");
        db.execSQL("DROP TABLE IF EXISTS download_tasks");
        onCreate(db);
    }

    // ============= books =============
    public long addBook(Models.BookRecord b) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("file_path", b.filePath);
        cv.put("title", b.title);
        cv.put("author", b.author);
        cv.put("cover_path", b.coverPath);
        cv.put("language", b.language);
        cv.put("chapters", b.chapters);
        cv.put("file_size", b.fileSize);
        cv.put("added_time", System.currentTimeMillis());
        cv.put("last_read_time", b.lastReadTime);
        cv.put("progress", b.progress);
        cv.put("last_chapter_href", b.lastChapterHref);
        cv.put("last_chapter_title", b.lastChapterTitle);
        cv.put("last_cfi_page", b.lastCfiPage);
        cv.put("description", b.description);
        cv.put("subjects", joinList(b.subjects));
        cv.put("publisher", b.publisher);
        cv.put("date", b.date);
        long id = db.insertWithOnConflict("books", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        if (id < 0) {
            // 已存在(可能重复导入),更新除 added_time 外的字段
            cv.remove("added_time");
            cv.put("title", b.title);
            cv.put("author", b.author);
            int n = db.update("books", cv, "file_path=?", new String[]{b.filePath});
            if (n > 0) {
                Cursor c = db.rawQuery("SELECT id FROM books WHERE file_path=?", new String[]{b.filePath});
                id = c.moveToFirst() ? c.getLong(0) : -1;
                c.close();
            }
        }
        return id;
    }

    public boolean updateBook(Models.BookRecord b) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        if (b.title != null) cv.put("title", b.title);
        if (b.author != null) cv.put("author", b.author);
        if (b.coverPath != null) cv.put("cover_path", b.coverPath);
        if (b.language != null) cv.put("language", b.language);
        cv.put("chapters", b.chapters);
        cv.put("file_size", b.fileSize);
        cv.put("last_read_time", b.lastReadTime);
        cv.put("progress", b.progress);
        if (b.lastChapterHref != null) cv.put("last_chapter_href", b.lastChapterHref);
        if (b.lastChapterTitle != null) cv.put("last_chapter_title", b.lastChapterTitle);
        cv.put("last_cfi_page", b.lastCfiPage);
        cv.put("description", b.description);
        if (b.subjects != null) cv.put("subjects", joinList(b.subjects));
        if (b.publisher != null) cv.put("publisher", b.publisher);
        if (b.date != null) cv.put("date", b.date);
        int n = db.update("books", cv, "id=?", new String[]{String.valueOf(b.id)});
        return n > 0;
    }

    public int updateProgress(long bookId, double progress, String chapterHref, String chapterTitle, int page) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("progress", progress);
        cv.put("last_chapter_href", chapterHref);
        cv.put("last_chapter_title", chapterTitle);
        cv.put("last_cfi_page", page);
        cv.put("last_read_time", System.currentTimeMillis());
        return db.update("books", cv, "id=?", new String[]{String.valueOf(bookId)});
    }

    public boolean deleteBook(long bookId) {
        SQLiteDatabase db = getWritableDatabase();
        int n = db.delete("books", "id=?", new String[]{String.valueOf(bookId)});
        db.delete("bookmarks", "book_id=?", new String[]{String.valueOf(bookId)});
        db.delete("highlights", "book_id=?", new String[]{String.valueOf(bookId)});
        db.delete("sessions", "book_id=?", new String[]{String.valueOf(bookId)});
        return n > 0;
    }

    public Models.BookRecord getBook(long bookId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM books WHERE id=?", new String[]{String.valueOf(bookId)});
        Models.BookRecord b = null;
        if (c.moveToFirst()) b = cursorToBook(c);
        c.close();
        return b;
    }

    public Models.BookRecord getBookByPath(String path) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM books WHERE file_path=?", new String[]{path});
        Models.BookRecord b = null;
        if (c.moveToFirst()) b = cursorToBook(c);
        c.close();
        return b;
    }

    public List<Models.BookRecord> listBooks(String orderBy) {
        List<Models.BookRecord> out = new ArrayList<Models.BookRecord>();
        SQLiteDatabase db = getReadableDatabase();
        String ord = orderBy == null || orderBy.isEmpty() ? "last_read_time DESC, added_time DESC" : orderBy;
        Cursor c = db.rawQuery("SELECT * FROM books ORDER BY " + ord, null);
        while (c.moveToNext()) out.add(cursorToBook(c));
        c.close();
        return out;
    }

    public List<Models.BookRecord> searchBooks(String q) {
        List<Models.BookRecord> out = new ArrayList<Models.BookRecord>();
        if (q == null || q.trim().isEmpty()) return listBooks(null);
        SQLiteDatabase db = getReadableDatabase();
        // 用 LIKE 跨多字段搜索(Android SQLite 无 fts5 模块)
        Cursor c = db.rawQuery(
            "SELECT * FROM books WHERE " +
            "title LIKE ? OR author LIKE ? OR description LIKE ? OR subjects LIKE ? OR publisher LIKE ? " +
            "ORDER BY last_read_time DESC",
            new String[]{"%" + q + "%", "%" + q + "%", "%" + q + "%", "%" + q + "%", "%" + q + "%"});
        while (c.moveToNext()) out.add(cursorToBook(c));
        c.close();
        return out;
    }

    private Models.BookRecord cursorToBook(Cursor c) {
        Models.BookRecord b = new Models.BookRecord();
        b.id = c.getLong(c.getColumnIndexOrThrow("id"));
        b.filePath = c.getString(c.getColumnIndexOrThrow("file_path"));
        b.title = c.getString(c.getColumnIndexOrThrow("title"));
        b.author = c.getString(c.getColumnIndexOrThrow("author"));
        b.coverPath = c.getString(c.getColumnIndexOrThrow("cover_path"));
        b.language = c.getString(c.getColumnIndexOrThrow("language"));
        b.chapters = c.getInt(c.getColumnIndexOrThrow("chapters"));
        b.fileSize = c.getLong(c.getColumnIndexOrThrow("file_size"));
        b.addedTime = c.getLong(c.getColumnIndexOrThrow("added_time"));
        b.lastReadTime = c.getLong(c.getColumnIndexOrThrow("last_read_time"));
        b.progress = c.getDouble(c.getColumnIndexOrThrow("progress"));
        b.lastChapterHref = c.getString(c.getColumnIndexOrThrow("last_chapter_href"));
        b.lastChapterTitle = c.getString(c.getColumnIndexOrThrow("last_chapter_title"));
        b.lastCfiPage = c.getInt(c.getColumnIndexOrThrow("last_cfi_page"));
        b.description = c.getString(c.getColumnIndexOrThrow("description"));
        b.publisher = c.getString(c.getColumnIndexOrThrow("publisher"));
        b.date = c.getString(c.getColumnIndexOrThrow("date"));
        String subs = c.getString(c.getColumnIndexOrThrow("subjects"));
        b.subjects = splitList(subs);
        return b;
    }

    // ============= bookmarks =============
    public long addBookmark(Models.Bookmark b) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("book_id", b.bookId);
        cv.put("chapter_href", b.chapterHref);
        cv.put("chapter_title", b.chapterTitle);
        cv.put("cfi_page", b.cfiPage);
        cv.put("cfi", b.cfi);
        cv.put("note", b.note);
        cv.put("created_time", System.currentTimeMillis());
        return db.insert("bookmarks", null, cv);
    }

    public boolean deleteBookmark(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("bookmarks", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public List<Models.Bookmark> listBookmarks(long bookId) {
        List<Models.Bookmark> out = new ArrayList<Models.Bookmark>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM bookmarks WHERE book_id=? ORDER BY created_time DESC", new String[]{String.valueOf(bookId)});
        while (c.moveToNext()) {
            Models.Bookmark b = new Models.Bookmark();
            b.id = c.getLong(c.getColumnIndexOrThrow("id"));
            b.bookId = c.getLong(c.getColumnIndexOrThrow("book_id"));
            b.chapterHref = c.getString(c.getColumnIndexOrThrow("chapter_href"));
            b.chapterTitle = c.getString(c.getColumnIndexOrThrow("chapter_title"));
            b.cfiPage = c.getInt(c.getColumnIndexOrThrow("cfi_page"));
            b.cfi = c.getString(c.getColumnIndexOrThrow("cfi"));
            b.note = c.getString(c.getColumnIndexOrThrow("note"));
            b.createdTime = c.getLong(c.getColumnIndexOrThrow("created_time"));
            out.add(b);
        }
        c.close();
        return out;
    }

    // ============= highlights =============
    public long addHighlight(Models.Highlight h) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("book_id", h.bookId);
        cv.put("chapter_href", h.chapterHref);
        cv.put("start_offset", h.startOffset);
        cv.put("end_offset", h.endOffset);
        cv.put("text", h.text);
        cv.put("color", h.color);
        cv.put("note", h.note);
        cv.put("created_time", System.currentTimeMillis());
        return db.insert("highlights", null, cv);
    }

    public boolean deleteHighlight(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("highlights", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public List<Models.Highlight> listHighlights(long bookId) {
        List<Models.Highlight> out = new ArrayList<Models.Highlight>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM highlights WHERE book_id=? ORDER BY created_time DESC", new String[]{String.valueOf(bookId)});
        while (c.moveToNext()) {
            Models.Highlight h = new Models.Highlight();
            h.id = c.getLong(c.getColumnIndexOrThrow("id"));
            h.bookId = c.getLong(c.getColumnIndexOrThrow("book_id"));
            h.chapterHref = c.getString(c.getColumnIndexOrThrow("chapter_href"));
            h.startOffset = c.getInt(c.getColumnIndexOrThrow("start_offset"));
            h.endOffset = c.getInt(c.getColumnIndexOrThrow("end_offset"));
            h.text = c.getString(c.getColumnIndexOrThrow("text"));
            h.color = c.getString(c.getColumnIndexOrThrow("color"));
            h.note = c.getString(c.getColumnIndexOrThrow("note"));
            h.createdTime = c.getLong(c.getColumnIndexOrThrow("created_time"));
            out.add(h);
        }
        c.close();
        return out;
    }

    // ============= sessions =============
    public long startSession(long bookId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("book_id", bookId);
        cv.put("start_time", System.currentTimeMillis());
        return db.insert("sessions", null, cv);
    }

    public void endSession(long sessionId, int pages, int chars) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("end_time", System.currentTimeMillis());
        cv.put("duration", System.currentTimeMillis() - getSessionStart(sessionId));
        cv.put("pages_read", pages);
        cv.put("chars_read", chars);
        db.update("sessions", cv, "id=?", new String[]{String.valueOf(sessionId)});
    }

    private long getSessionStart(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT start_time FROM sessions WHERE id=?", new String[]{String.valueOf(id)});
        long t = 0;
        if (c.moveToFirst()) t = c.getLong(0);
        c.close();
        return t;
    }

    public List<Models.Session> listSessions(long bookId, int limit) {
        List<Models.Session> out = new ArrayList<Models.Session>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM sessions WHERE book_id=? ORDER BY start_time DESC LIMIT ?",
            new String[]{String.valueOf(bookId), String.valueOf(limit)});
        while (c.moveToNext()) {
            Models.Session s = new Models.Session();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.bookId = c.getLong(c.getColumnIndexOrThrow("book_id"));
            s.startTime = c.getLong(c.getColumnIndexOrThrow("start_time"));
            s.endTime = c.getLong(c.getColumnIndexOrThrow("end_time"));
            s.duration = c.getLong(c.getColumnIndexOrThrow("duration"));
            s.pagesRead = c.getInt(c.getColumnIndexOrThrow("pages_read"));
            s.charsRead = c.getInt(c.getColumnIndexOrThrow("chars_read"));
            out.add(s);
        }
        c.close();
        return out;
    }

    // ============= OPDS sources =============
    public long addOpdsSource(Models.OpdsSource s) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", s.name);
        cv.put("url", s.url);
        cv.put("username", s.username);
        cv.put("password", s.password);
        cv.put("bearer_token", s.bearerToken);
        cv.put("enabled", s.enabled);
        cv.put("added_time", System.currentTimeMillis());
        cv.put("last_sync", s.lastSync);
        return db.insert("opds_sources", null, cv);
    }

    public boolean updateOpdsSource(Models.OpdsSource s) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", s.name);
        cv.put("url", s.url);
        cv.put("username", s.username);
        cv.put("password", s.password);
        cv.put("bearer_token", s.bearerToken);
        cv.put("enabled", s.enabled);
        cv.put("last_result", s.lastResult);
        cv.put("last_sync", s.lastSync);
        return db.update("opds_sources", cv, "id=?", new String[]{String.valueOf(s.id)}) > 0;
    }

    public boolean deleteOpdsSource(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete("opds_sources", "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public List<Models.OpdsSource> listOpdsSources() {
        List<Models.OpdsSource> out = new ArrayList<Models.OpdsSource>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM opds_sources ORDER BY added_time DESC", null);
        while (c.moveToNext()) {
            Models.OpdsSource s = new Models.OpdsSource();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.name = c.getString(c.getColumnIndexOrThrow("name"));
            s.url = c.getString(c.getColumnIndexOrThrow("url"));
            s.username = c.getString(c.getColumnIndexOrThrow("username"));
            s.password = c.getString(c.getColumnIndexOrThrow("password"));
            s.bearerToken = c.getString(c.getColumnIndexOrThrow("bearer_token"));
            s.enabled = c.getInt(c.getColumnIndexOrThrow("enabled"));
            s.addedTime = c.getLong(c.getColumnIndexOrThrow("added_time"));
            s.lastResult = c.getString(c.getColumnIndexOrThrow("last_result"));
            s.lastSync = c.getLong(c.getColumnIndexOrThrow("last_sync"));
            out.add(s);
        }
        c.close();
        return out;
    }

    // ============= OPDS cache =============
    public void cacheOpdsEntries(long sourceId, List<Models.OpdsEntry> entries) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Models.OpdsEntry e : entries) {
                ContentValues cv = new ContentValues();
                cv.put("source_id", sourceId);
                cv.put("entry_id", e.id);
                cv.put("title", e.title);
                cv.put("author", e.author);
                cv.put("summary", e.summary);
                cv.put("cover_url", e.coverUrl);
                cv.put("download_url", e.downloadUrl);
                cv.put("format", e.format);
                cv.put("language", e.language);
                cv.put("issued", e.issued);
                cv.put("publisher", e.publisher);
                cv.put("price", e.price);
                cv.put("fetched_time", now);
                db.insertWithOnConflict("opds_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<Models.OpdsEntry> listCachedEntries(long sourceId) {
        List<Models.OpdsEntry> out = new ArrayList<Models.OpdsEntry>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM opds_cache WHERE source_id=? ORDER BY fetched_time DESC LIMIT 200", new String[]{String.valueOf(sourceId)});
        while (c.moveToNext()) {
            Models.OpdsEntry e = new Models.OpdsEntry();
            e.id = c.getString(c.getColumnIndexOrThrow("entry_id"));
            e.title = c.getString(c.getColumnIndexOrThrow("title"));
            e.author = c.getString(c.getColumnIndexOrThrow("author"));
            e.summary = c.getString(c.getColumnIndexOrThrow("summary"));
            e.coverUrl = c.getString(c.getColumnIndexOrThrow("cover_url"));
            e.downloadUrl = c.getString(c.getColumnIndexOrThrow("download_url"));
            e.format = c.getString(c.getColumnIndexOrThrow("format"));
            e.language = c.getString(c.getColumnIndexOrThrow("language"));
            e.issued = c.getString(c.getColumnIndexOrThrow("issued"));
            e.publisher = c.getString(c.getColumnIndexOrThrow("publisher"));
            e.price = c.getDouble(c.getColumnIndexOrThrow("price"));
            out.add(e);
        }
        c.close();
        return out;
    }

    // ============= Download tasks =============
    public long addDownload(Models.DownloadTask t) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("url", t.url);
        cv.put("title", t.title);
        cv.put("file_path", t.filePath);
        cv.put("total_bytes", t.totalBytes);
        cv.put("downloaded_bytes", t.downloadedBytes);
        cv.put("status", t.status);
        cv.put("progress", t.progress);
        cv.put("created_time", System.currentTimeMillis());
        return db.insert("download_tasks", null, cv);
    }

    public void updateDownload(Models.DownloadTask t) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("downloaded_bytes", t.downloadedBytes);
        cv.put("total_bytes", t.totalBytes);
        cv.put("status", t.status);
        cv.put("progress", t.progress);
        cv.put("error_message", t.errorMessage);
        if (t.status == 3 || t.status == 4) cv.put("completed_time", System.currentTimeMillis());
        db.update("download_tasks", cv, "id=?", new String[]{String.valueOf(t.id)});
    }

    public List<Models.DownloadTask> listDownloads(int status) {
        List<Models.DownloadTask> out = new ArrayList<Models.DownloadTask>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        if (status < 0) c = db.rawQuery("SELECT * FROM download_tasks ORDER BY created_time DESC", null);
        else c = db.rawQuery("SELECT * FROM download_tasks WHERE status=? ORDER BY created_time DESC", new String[]{String.valueOf(status)});
        while (c.moveToNext()) {
            Models.DownloadTask t = new Models.DownloadTask();
            t.id = c.getLong(c.getColumnIndexOrThrow("id"));
            t.url = c.getString(c.getColumnIndexOrThrow("url"));
            t.title = c.getString(c.getColumnIndexOrThrow("title"));
            t.filePath = c.getString(c.getColumnIndexOrThrow("file_path"));
            t.totalBytes = c.getLong(c.getColumnIndexOrThrow("total_bytes"));
            t.downloadedBytes = c.getLong(c.getColumnIndexOrThrow("downloaded_bytes"));
            t.status = c.getInt(c.getColumnIndexOrThrow("status"));
            t.progress = c.getInt(c.getColumnIndexOrThrow("progress"));
            t.errorMessage = c.getString(c.getColumnIndexOrThrow("error_message"));
            t.createdTime = c.getLong(c.getColumnIndexOrThrow("created_time"));
            t.completedTime = c.getLong(c.getColumnIndexOrThrow("completed_time"));
            out.add(t);
        }
        c.close();
        return out;
    }

    // ============= utilities =============
    public int getBookCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM books", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public int getTotalChapters() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(chapters) FROM books", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public long getTotalReadingTime() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(duration) FROM sessions", null);
        long t = 0;
        if (c.moveToFirst()) t = c.getLong(0);
        c.close();
        return t;
    }

    public long getTotalCharsRead() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(chars_read) FROM sessions", null);
        long n = 0;
        if (c.moveToFirst()) n = c.getLong(0);
        c.close();
        return n;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("books", null, null);
        db.delete("bookmarks", null, null);
        db.delete("highlights", null, null);
        db.delete("sessions", null, null);
        db.delete("opds_cache", null, null);
    }

    // ============= 备份/恢复 =============
    public String exportToJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("version", 1);
            o.put("timestamp", System.currentTimeMillis());
            JSONArray booksArr = new JSONArray();
            for (Models.BookRecord b : listBooks(null)) {
                JSONObject bo = new JSONObject();
                bo.put("filePath", b.filePath);
                bo.put("title", b.title);
                bo.put("author", b.author);
                bo.put("coverPath", b.coverPath);
                bo.put("language", b.language);
                bo.put("chapters", b.chapters);
                bo.put("fileSize", b.fileSize);
                bo.put("addedTime", b.addedTime);
                bo.put("lastReadTime", b.lastReadTime);
                bo.put("progress", b.progress);
                bo.put("lastChapterHref", b.lastChapterHref);
                bo.put("lastChapterTitle", b.lastChapterTitle);
                bo.put("lastCfiPage", b.lastCfiPage);
                bo.put("description", b.description);
                bo.put("subjects", joinList(b.subjects));
                bo.put("publisher", b.publisher);
                bo.put("date", b.date);
                booksArr.put(bo);
            }
            o.put("books", booksArr);
            JSONArray bmArr = new JSONArray();
            Cursor c = db().rawQuery("SELECT * FROM bookmarks", null);
            while (c.moveToNext()) {
                JSONObject bm = new JSONObject();
                bm.put("bookId", c.getLong(c.getColumnIndexOrThrow("book_id")));
                bm.put("chapterHref", c.getString(c.getColumnIndexOrThrow("chapter_href")));
                bm.put("chapterTitle", c.getString(c.getColumnIndexOrThrow("chapter_title")));
                bm.put("cfiPage", c.getInt(c.getColumnIndexOrThrow("cfi_page")));
                bm.put("cfi", c.getString(c.getColumnIndexOrThrow("cfi")));
                bm.put("note", c.getString(c.getColumnIndexOrThrow("note")));
                bm.put("createdTime", c.getLong(c.getColumnIndexOrThrow("created_time")));
                bmArr.put(bm);
            }
            c.close();
            o.put("bookmarks", bmArr);
            return o.toString(2);
        } catch (Exception e) { return "{}"; }
    }

    private SQLiteDatabase db() { return getReadableDatabase(); }

    public int importFromJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            int n = 0;
            JSONArray booksArr = o.optJSONArray("books");
            if (booksArr != null) for (int i = 0; i < booksArr.length(); i++) {
                JSONObject bo = booksArr.getJSONObject(i);
                Models.BookRecord b = new Models.BookRecord();
                b.filePath = bo.optString("filePath");
                b.title = bo.optString("title");
                b.author = bo.optString("author");
                b.coverPath = bo.optString("coverPath");
                b.language = bo.optString("language");
                b.chapters = bo.optInt("chapters");
                b.fileSize = bo.optLong("fileSize");
                b.addedTime = bo.optLong("addedTime", System.currentTimeMillis());
                b.lastReadTime = bo.optLong("lastReadTime");
                b.progress = bo.optDouble("progress");
                b.lastChapterHref = bo.optString("lastChapterHref");
                b.lastChapterTitle = bo.optString("lastChapterTitle");
                b.lastCfiPage = bo.optInt("lastCfiPage");
                b.description = bo.optString("description");
                b.subjects = splitList(bo.optString("subjects"));
                b.publisher = bo.optString("publisher");
                b.date = bo.optString("date");
                if (addBook(b) > 0) n++;
            }
            return n;
        } catch (Exception e) { return 0; }
    }

    // ============= list utilities =============
    private String joinList(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("\u0001");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private List<String> splitList(String s) {
        List<String> out = new ArrayList<String>();
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split("\u0001")) out.add(p);
        return out;
    }
}
