package com.icereading.app;

import android.content.Context;

import java.io.File;

/**
 * EPUB 导入器 — 调用 EpubParser + 写 DB + 提取封面
 */
public class BookImporter {

    /**
     * 完整导入流程:解析 EPUB → 提取封面 → 写 DB → 返回 bookId
     * @return bookId,失败返回 -1
     */
    public static long doImport(Context ctx, File epubFile) throws Exception {
        String cacheKey = String.valueOf(epubFile.getAbsolutePath().hashCode());
        File extractDir = new File(ctx.getCacheDir(), "extract/" + cacheKey);
        if (!extractDir.exists()) extractDir.mkdirs();

        Models.EpubBook book = new EpubParser().parse(epubFile.getAbsolutePath(), extractDir.getAbsolutePath(), null);

        // 写 DB
        Models.BookRecord b = new Models.BookRecord();
        b.filePath = epubFile.getAbsolutePath();
        b.title = book.metadata.title;
        b.author = book.metadata.creator;
        b.language = book.metadata.language;
        b.chapters = book.spine.size();
        b.fileSize = epubFile.length();
        b.addedTime = System.currentTimeMillis();
        b.publisher = book.metadata.publisher;
        b.date = book.metadata.date;
        b.description = book.metadata.description;
        b.subjects = book.metadata.subjects;
        // 封面
        for (Models.ChapterRef r : book.manifest) {
            if (r.mediaType != null && r.mediaType.startsWith("image/")) {
                File cover = new File(extractDir, r.href);
                if (cover.exists()) {
                    b.coverPath = cover.getAbsolutePath();
                    break;
                }
            }
        }
        return BookRepository.get(ctx).addBook(b);
    }
}
