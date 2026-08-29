package com.icereading.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP 下载器(支持断点续传 / 进度回调 / 队列)
 * 单线程 + 顺序执行,简单可靠
 */
public class Downloader {

    public interface ProgressListener {
        void onProgress(long id, long downloaded, long total, int percent, int status);
    }

    private final Context ctx;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ProgressListener listener;
    private volatile boolean cancelled;

    public Downloader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public void setListener(ProgressListener l) { this.listener = l; }

    public void cancel() { cancelled = true; }

    /**
     * 下载到 filePath,断点续传支持 Range
     */
    public void download(final Models.DownloadTask task) {
        exec.execute(new Runnable() {
            public void run() {
                cancelled = false;
                File f = new File(task.filePath);
                File parent = f.getParentFile();
                if (parent != null) parent.mkdirs();
                long downloaded = 0;
                if (f.exists()) downloaded = f.length();
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(task.url).openConnection();
                    con.setRequestProperty("User-Agent", "iceReading/1.0");
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(60000);
                    if (downloaded > 0) {
                        con.setRequestProperty("Range", "bytes=" + downloaded + "-");
                    }
                    int code = con.getResponseCode();
                    long total = task.totalBytes;
                    if (code == 200) {
                        // 服务器不支持 Range,从头开始
                        downloaded = 0;
                        total = con.getContentLengthLong();
                    } else if (code == 206) {
                        // 部分内容
                        String range = con.getHeaderField("Content-Range");
                        if (range != null && range.contains("/")) {
                            try { total = Long.parseLong(range.substring(range.indexOf("/") + 1)); } catch (Exception ignore) {}
                        }
                    } else {
                        throw new RuntimeException("HTTP " + code);
                    }
                    if (total <= 0) total = con.getContentLengthLong();
                    task.totalBytes = total;
                    task.downloadedBytes = downloaded;
                    task.status = 1;
                    notifyProgress(task);
                    RandomAccessFile raf = new RandomAccessFile(f, "rw");
                    raf.seek(downloaded);
                    InputStream is = con.getInputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    long lastUpdate = 0;
                    while ((n = is.read(buf)) > 0 && !cancelled) {
                        raf.write(buf, 0, n);
                        downloaded += n;
                        task.downloadedBytes = downloaded;
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 500) {
                            lastUpdate = now;
                            notifyProgress(task);
                        }
                    }
                    raf.close();
                    is.close();
                    if (cancelled) {
                        task.status = 2;
                        task.errorMessage = "已取消";
                    } else {
                        task.status = 3;
                        task.progress = 100;
                    }
                    notifyProgress(task);
                } catch (Throwable t) {
                    task.status = 4;
                    task.errorMessage = t.getMessage();
                    notifyProgress(task);
                }
            }
        });
    }

    private void notifyProgress(final Models.DownloadTask t) {
        int pct = t.totalBytes > 0 ? (int)(t.downloadedBytes * 100 / t.totalBytes) : 0;
        if (listener != null) {
            final int percent = pct;
            ui.post(new Runnable() { public void run() { listener.onProgress(t.id, t.downloadedBytes, t.totalBytes, percent, t.status); } });
        }
        // 持久化
        BookRepository.get(ctx).updateDownload(t);
    }

    public void queue(List<Models.DownloadTask> tasks) {
        for (Models.DownloadTask t : tasks) download(t);
    }
}
