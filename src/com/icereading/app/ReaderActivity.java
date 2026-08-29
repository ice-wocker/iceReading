package com.icereading.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

/**
 * 阅读器 Activity
 *  - WebView 加载 chapter.html(已解压到 cacheDir/extract/<bookId>/)
 *  - JS bridge 接受 page change / selection / link click
 *  - 自动保存进度
 *  - 翻页:点屏分区 / 音量键 / 滑屏
 *  - 长按:选词 → 弹高亮/笔记菜单
 */
public class ReaderActivity extends Activity {

    private WebView webView;
    private ProgressBar progress;
    private View topBar, bottomBar;
    private TextView tvTitle, tvChapter, tvProgress;
    private Button btnBack, btnToc, btnBookmarks, btnSearch, btnSettings, btnInfo;

    private Models.BookRecord book;
    private Models.EpubBook epub;
    private int currentChapterIndex = 0;
    private int currentPageInChapter = 0;
    private int totalPagesInChapter = 1;
    private long sessionId = -1;
    private long startMs = 0;
    private long lastScrollTs = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean fullscreen = true;
    private boolean barsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reader);
        webView = (WebView) findViewById(R.id.webView);
        progress = (ProgressBar) findViewById(R.id.progress);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        tvChapter = (TextView) findViewById(R.id.tvChapter);
        tvProgress = (TextView) findViewById(R.id.tvProgress);
        btnBack = (Button) findViewById(R.id.btnBack);
        btnToc = (Button) findViewById(R.id.btnToc);
        btnBookmarks = (Button) findViewById(R.id.btnBookmarks);
        btnSearch = (Button) findViewById(R.id.btnSearch);
        btnSettings = (Button) findViewById(R.id.btnSettings);
        btnInfo = (Button) findViewById(R.id.btnInfo);

        // 加载书
        long bookId = getIntent().getLongExtra("bookId", 0);
        long bookmarkId = getIntent().getLongExtra("bookmarkId", 0);
        book = BookRepository.get(this).getBook(bookId);
        if (book == null) {
            Toast.makeText(this, "书不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        tvTitle.setText(book.title);

        // 解析(已解压的 EPUB 缓存)
        File extractDir = new File(getCacheDir(), "extract/" + String.valueOf(book.filePath.hashCode()));
        if (!extractDir.exists() || !new File(extractDir, "OEBPS").exists()) {
            // 重新解析
            try {
                new EpubParser().parse(book.filePath, extractDir.getAbsolutePath(), null);
            } catch (Throwable t) {
                Toast.makeText(this, "解析失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        try {
            // 简单恢复
            epub = new Models.EpubBook();
            epub.filePath = book.filePath;
            epub.basePath = "OEBPS/";
            // 找 OPF
            File opf = new File(extractDir, "OEBPS/content.opf");
            if (!opf.exists()) opf = new File(extractDir, "content.opf");
            if (opf.exists()) {
                org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(opf);
                org.w3c.dom.Element pkg = (org.w3c.dom.Element) doc.getElementsByTagNameNS("*", "package").item(0);
                if (pkg != null) epub.version = pkg.getAttribute("version");
                org.w3c.dom.Element metaEl = (org.w3c.dom.Element) doc.getElementsByTagNameNS("*", "metadata").item(0);
                if (metaEl != null) {
                    org.w3c.dom.NodeList children = metaEl.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        org.w3c.dom.Node n = children.item(i);
                        if (n.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;
                        org.w3c.dom.Element e = (org.w3c.dom.Element) n;
                        String name = e.getLocalName();
                        String text = e.getTextContent();
                        if (text == null) text = "";
                        if ("title".equals(name)) epub.metadata.title = text;
                        else if ("creator".equals(name)) epub.metadata.creator = text;
                        else if ("language".equals(name)) epub.metadata.language = text;
                    }
                }
                org.w3c.dom.Element manifestEl = (org.w3c.dom.Element) doc.getElementsByTagNameNS("*", "manifest").item(0);
                if (manifestEl != null) {
                    org.w3c.dom.NodeList items = manifestEl.getElementsByTagNameNS("*", "item");
                    for (int i = 0; i < items.getLength(); i++) {
                        org.w3c.dom.Element it = (org.w3c.dom.Element) items.item(i);
                        String id = it.getAttribute("id");
                        String href = it.getAttribute("href");
                        if (href == null) continue;
                        epub.manifest.add(new Models.ChapterRef(id, epub.basePath + href, it.getAttribute("media-type")));
                    }
                }
                org.w3c.dom.Element spineEl = (org.w3c.dom.Element) doc.getElementsByTagNameNS("*", "spine").item(0);
                if (spineEl != null) {
                    org.w3c.dom.NodeList sp = spineEl.getElementsByTagNameNS("*", "itemref");
                    for (int i = 0; i < sp.getLength(); i++) {
                        org.w3c.dom.Element ir = (org.w3c.dom.Element) sp.item(i);
                        String idref = ir.getAttribute("idref");
                        for (Models.ChapterRef r : epub.manifest) {
                            if (r.id.equals(idref)) {
                                Models.EpubChapter ch = new Models.EpubChapter();
                                ch.id = r.id;
                                ch.href = r.href;
                                ch.order = epub.spine.size();
                                ch.type = "text";
                                if (epub.spine.isEmpty()) ch.title = epub.metadata.title;
                                else ch.title = "第 " + (epub.spine.size() + 1) + " 章";
                                epub.spine.add(ch);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Toast.makeText(this, "解析缓存失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (epub.spine.isEmpty()) {
            Toast.makeText(this, "无章节", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 恢复进度
        if (bookmarkId > 0) {
            Models.Bookmark bm = null;
            for (Models.Bookmark b : BookRepository.get(this).listBookmarks(book.id)) if (b.id == bookmarkId) { bm = b; break; }
            if (bm != null) {
                for (int i = 0; i < epub.spine.size(); i++) {
                    if (epub.spine.get(i).href.endsWith(bm.chapterHref) || bm.chapterHref.endsWith(epub.spine.get(i).href)) {
                        currentChapterIndex = i;
                        currentPageInChapter = bm.cfiPage;
                        break;
                    }
                }
            }
        } else if (book.lastChapterHref != null && !book.lastChapterHref.isEmpty()) {
            for (int i = 0; i < epub.spine.size(); i++) {
                if (epub.spine.get(i).href.endsWith(book.lastChapterHref) || book.lastChapterHref.endsWith(epub.spine.get(i).href)) {
                    currentChapterIndex = i;
                    currentPageInChapter = book.lastCfiPage;
                    break;
                }
            }
        }

        setupWebView();
        setupBars();
        loadCurrentChapter();

        if (Settings.getKeepScreenOn(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Settings.getFullscreen(this)) hideSystemUI();
        sessionId = BookRepository.get(this).startSession(book.id);
        startMs = System.currentTimeMillis();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDefaultTextEncodingName("UTF-8");
        ws.setAllowFileAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= 16) ws.setAllowFileAccessFromFileURLs(true);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.addJavascriptInterface(this, "NLAndroid");
        webView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                applySettings();
                reportPageToAndroid();
            }
        });
    }

    private void applySettings() {
        String fontFamily = Settings.getFontFamily(this);
        if (fontFamily != null && !fontFamily.isEmpty()) {
            File f = new File(getCacheDir(), "fonts/" + fontFamily);
            if (f.exists()) fontFamily = "file://" + f.getAbsolutePath();
            else fontFamily = "";
        } else fontFamily = "";
        String theme = "theme-" + Settings.getTheme(this);
        String fontSize = String.valueOf(Settings.getFontSize(this));
        String lineHeight = String.valueOf(Settings.getLineHeight(this));
        String margin = String.valueOf(Settings.getMargin(this));
        String paragraph = String.valueOf(Settings.getParagraphSpacing(this));
        String js = "window.NL && window.NL.applySettings({theme:'" + theme + "',fontSize:" + fontSize +
                ",lineHeight:" + lineHeight + ",margin:" + margin + ",paragraphSpacing:" + paragraph +
                (fontFamily.isEmpty() ? "" : ",fontFamily:'" + fontFamily + "'") + "});";
        webView.evaluateJavascript(js, null);
    }

    @JavascriptInterface
    public void onLoaded() {
        ui.post(new Runnable() { public void run() { progress.setVisibility(View.GONE); } });
    }

    @JavascriptInterface
    public void onTocLoaded(String tocJson) {
        // 简化:暂不处理
    }

    @JavascriptInterface
    public void onLinkClick(String url) {
        // 内部链接:跳章节;外部:打开浏览器
        ui.post(new Runnable() {
            public void run() {
                if (url.startsWith("#") || url.contains("#")) {
                    // 内部锚点
                    String anchor = url.substring(url.indexOf('#') + 1);
                    webView.evaluateJavascript("window.scrollTo(0, document.getElementById('" + anchor + "') ? document.getElementById('" + anchor + "').offsetTop : 0);", null);
                } else {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(i);
                    } catch (Throwable t) {}
                }
            }
        });
    }

    private void setupBars() {
        btnBack.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        btnToc.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showToc(); } });
        btnBookmarks.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showBookmarks(); } });
        btnSearch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showSearch(); } });
        btnSettings.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showQuickSettings(); } });
        btnInfo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showInfo(); } });

        // 点屏分区翻页
        webView.setOnTouchListener(new View.OnTouchListener() {
            private float x;
            public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    x = ev.getX();
                    return false;
                }
                if (ev.getAction() == MotionEvent.ACTION_UP && !barsVisible) {
                    int w = v.getWidth();
                    if (Settings.getTapZoneFlip(ReaderActivity.this)) {
                        if (x < w * 0.3f) prevPage();
                        else if (x > w * 0.7f) nextPage();
                        else showBars();
                    } else {
                        showBars();
                    }
                    return true;
                }
                if (barsVisible && ev.getAction() == MotionEvent.ACTION_UP) {
                    hideBars();
                    return true;
                }
                return false;
            }
        });
    }

    private void loadCurrentChapter() {
        if (currentChapterIndex < 0) currentChapterIndex = 0;
        if (currentChapterIndex >= epub.spine.size()) currentChapterIndex = epub.spine.size() - 1;
        Models.EpubChapter ch = epub.spine.get(currentChapterIndex);
        File f = new File(getCacheDir(), "extract/" + String.valueOf(book.filePath.hashCode()) + "/" + ch.href);
        if (!f.exists()) { Toast.makeText(this, "章节文件不存在", Toast.LENGTH_SHORT).show(); return; }
        File cssFile = new File(getCacheDir(), "extract/" + String.valueOf(book.filePath.hashCode()) + "/OEBPS/reader.css");
        String html;
        try {
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            fis.read(data); fis.close();
            html = new String(data, "UTF-8");
        } catch (Exception e) { Toast.makeText(this, "读章节失败", Toast.LENGTH_SHORT).show(); return; }
        // 注入 base
        String baseUrl = "file://" + f.getParentFile().getAbsolutePath() + "/";
        String cssLink = "<link rel='stylesheet' href='file://" + cssFile.getAbsolutePath() + "'>";
        String script = "<script src='file:///android_asset/reader.js'></script>";
        // 替换 head
        if (html.contains("</head>")) html = html.replace("</head>", cssLink + script + "</head>");
        else if (html.contains("<head>")) html = html.replace("<head>", "<head>" + cssLink + script);
        else html = "<html><head>" + cssLink + script + "</head><body>" + html + "</body></html>";
        // body id content
        if (html.contains("<body>") && !html.contains("id=\"content\"")) {
            html = html.replace("<body>", "<body><div id='content'>").replace("</body>", "</div></body>");
        }
        webView.loadDataWithBaseURL(baseUrl, html, "text/html; charset=utf-8", "UTF-8", null);
        tvChapter.setText(ch.title != null ? ch.title : "第 " + (currentChapterIndex + 1) + " 章");
    }

    private void reportPageToAndroid() {
        webView.evaluateJavascript("window.NL.getCurrentPage && window.NL.getCurrentPage()", new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String value) {
                try {
                    int p = Integer.parseInt(value.replaceAll("[^0-9]", ""));
                    currentPageInChapter = p;
                } catch (Exception ignore) {}
            }
        });
        webView.evaluateJavascript("window.NL.getPageCount && window.NL.getPageCount()", new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String value) {
                try { totalPagesInChapter = Integer.parseInt(value.replaceAll("[^0-9]", "")); } catch (Exception ignore) {}
            }
        });
        updateProgressBar();
    }

    private void updateProgressBar() {
        int total = 0, done = 0;
        for (int i = 0; i < epub.spine.size(); i++) {
            total++;
            if (i < currentChapterIndex) done++;
        }
        if (totalPagesInChapter > 0) done += (double) currentPageInChapter / totalPagesInChapter;
        double overall = total > 0 ? done / total : 0;
        String s = "第 " + (currentChapterIndex + 1) + " / " + epub.spine.size() + " 章 · 本章 " + currentPageInChapter + " / " + totalPagesInChapter + " 页 · 总 " + (int)(overall * 100) + "%";
        tvProgress.setText(s);
        Models.EpubChapter ch = epub.spine.get(currentChapterIndex);
        BookRepository.get(this).updateProgress(book.id, overall, ch.href, ch.title, currentPageInChapter);
    }

    private void prevPage() {
        if (currentPageInChapter > 1) {
            currentPageInChapter--;
            webView.evaluateJavascript("window.NL && window.NL.scrollToPage(" + currentPageInChapter + ")", null);
            updateProgressBar();
            return;
        }
        if (currentChapterIndex > 0) {
            currentChapterIndex--;
            currentPageInChapter = 9999;
            loadCurrentChapter();
        }
    }

    private void nextPage() {
        if (currentPageInChapter < totalPagesInChapter) {
            currentPageInChapter++;
            webView.evaluateJavascript("window.NL && window.NL.scrollToPage(" + currentPageInChapter + ")", null);
            updateProgressBar();
            return;
        }
        if (currentChapterIndex < epub.spine.size() - 1) {
            currentChapterIndex++;
            currentPageInChapter = 1;
            loadCurrentChapter();
        } else {
            Toast.makeText(this, "已是最后一章", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBars() {
        barsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        ui.postDelayed(new Runnable() { public void run() { hideBars(); } }, 3000);
    }

    private void hideBars() {
        barsVisible = false;
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (Settings.getVolumeKeyFlip(this)) { prevPage(); return true; }
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (Settings.getVolumeKeyFlip(this)) { nextPage(); return true; }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (barsVisible) { hideBars(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showToc() {
        String[] items = new String[epub.spine.size()];
        for (int i = 0; i < items.length; i++) {
            Models.EpubChapter c = epub.spine.get(i);
            items[i] = (i + 1) + ". " + (c.title != null ? c.title : "第 " + (i + 1) + " 章");
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("📑 目录")
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    currentChapterIndex = w;
                    currentPageInChapter = 1;
                    loadCurrentChapter();
                    hideBars();
                }
            })
            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() { public void onCancel(android.content.DialogInterface d) { hideBars(); } })
            .show();
    }

    private void showBookmarks() {
        java.util.List<Models.Bookmark> bms = BookRepository.get(this).listBookmarks(book.id);
        final String[] items;
        if (bms.isEmpty()) items = new String[]{"(无书签)"};
        else { items = new String[bms.size() + 1]; items[0] = "+ 添加当前页书签"; for (int i = 0; i < bms.size(); i++) items[i + 1] = (i + 1) + ". " + (bms.get(i).chapterTitle != null ? bms.get(i).chapterTitle : "") + " (页 " + (bms.get(i).cfiPage + 1) + ")"; }
        final Models.Bookmark[] arr = bms.toArray(new Models.Bookmark[0]);
        new android.app.AlertDialog.Builder(this)
            .setTitle("🔖 书签")
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    if (w == 0) {
                        // 添加当前
                        Models.Bookmark bm = new Models.Bookmark();
                        bm.bookId = book.id;
                        bm.chapterHref = epub.spine.get(currentChapterIndex).href;
                        bm.chapterTitle = epub.spine.get(currentChapterIndex).title;
                        bm.cfiPage = currentPageInChapter;
                        bm.cfi = "page=" + currentPageInChapter;
                        BookRepository.get(ReaderActivity.this).addBookmark(bm);
                        Toast.makeText(ReaderActivity.this, "已添加书签", Toast.LENGTH_SHORT).show();
                    } else if (w > 0 && w <= arr.length) {
                        Models.Bookmark bm = arr[w - 1];
                        for (int i = 0; i < epub.spine.size(); i++) {
                            if (epub.spine.get(i).href.endsWith(bm.chapterHref) || bm.chapterHref.endsWith(epub.spine.get(i).href)) {
                                currentChapterIndex = i;
                                currentPageInChapter = bm.cfiPage;
                                loadCurrentChapter();
                                break;
                            }
                        }
                    }
                    hideBars();
                }
            })
            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() { public void onCancel(android.content.DialogInterface d) { hideBars(); } })
            .show();
    }

    private void showSearch() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("输入搜索词");
        new android.app.AlertDialog.Builder(this)
            .setTitle("🔍 搜索")
            .setView(et)
            .setPositiveButton("搜索", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    final String q = et.getText().toString().trim();
                    if (q.isEmpty()) return;
                    webView.evaluateJavascript("(function() { var html = document.body.innerHTML; var lc = html.toLowerCase(); var qlc = '" + q.toLowerCase().replace("'", "\\'") + "'; var i = -1; var count = 0; while ((i = lc.indexOf(qlc, i+1)) >= 0 && count < 5) { var node = document.createRange(); var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false); var n, idx = 0; while ((n = walker.nextNode())) { var l = n.nodeValue.length; if (idx + l >= i) { var s = Math.max(0, i - idx); var e = Math.min(l, i + qlc.length - idx); if (s < e) { node.setStart(n, s); node.setEnd(n, e); try { node.surroundContents(document.createElement('mark')); } catch (ex) {} break; } idx += l; } } count++; } return count; })()", new android.webkit.ValueCallback<String>() {
                        public void onReceiveValue(String value) {
                            int n = 0;
                            try { n = Integer.parseInt(value.replaceAll("[^0-9]", "")); } catch (Exception ignore) {}
                            Toast.makeText(ReaderActivity.this, "找到 " + n + " 处", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            })
            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() { public void onCancel(android.content.DialogInterface d) { hideBars(); } })
            .setNegativeButton("取消", null)
            .show();
    }

    private void showQuickSettings() {
        final String[] items = {"主题:日间", "主题:护眼", "主题:羊皮", "主题:夜间", "主题:深邃", "字体大 +", "字体小 -", "全屏切换"};
        new android.app.AlertDialog.Builder(this)
            .setTitle("⚙ 快速设置")
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    switch (w) {
                        case 0: Settings.setTheme(ReaderActivity.this, Settings.THEME_DAY); break;
                        case 1: Settings.setTheme(ReaderActivity.this, Settings.THEME_PAPER); break;
                        case 2: Settings.setTheme(ReaderActivity.this, Settings.THEME_SEPIA); break;
                        case 3: Settings.setTheme(ReaderActivity.this, Settings.THEME_NIGHT); break;
                        case 4: Settings.setTheme(ReaderActivity.this, Settings.THEME_DARK); break;
                        case 5: Settings.setFontSize(ReaderActivity.this, Settings.getFontSize(ReaderActivity.this) + 2); break;
                        case 6: Settings.setFontSize(ReaderActivity.this, Settings.getFontSize(ReaderActivity.this) - 2); break;
                        case 7: fullscreen = !fullscreen; if (fullscreen) hideSystemUI(); else showSystemUI(); break;
                    }
                    applySettings();
                }
            })
            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() { public void onCancel(android.content.DialogInterface d) { hideBars(); } })
            .show();
    }

    private void showInfo() {
        int totalChapters = epub.spine.size();
        int wordsRead = (int) ((System.currentTimeMillis() - startMs) / 60000);
        String msg = "📖 " + book.title + "\n✍ " + book.author + "\n🌐 " + book.language + "\n📚 " + totalChapters + " 章\n⏱ 已读 " + wordsRead + " 分钟\n📊 总进度 " + (int)(book.progress * 100) + "%";
        new android.app.AlertDialog.Builder(this).setTitle("ℹ 信息").setMessage(msg).setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) { hideBars(); }
        }).show();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveSession();
    }

    private void saveSession() {
        if (sessionId < 0 || book == null) return;
        long dur = System.currentTimeMillis() - startMs;
        int chars = currentPageInChapter * 200;  // 估算
        BookRepository.get(this).endSession(sessionId, currentPageInChapter, chars);
    }
}
