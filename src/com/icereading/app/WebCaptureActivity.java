package com.icereading.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 在线小说章节抓取 — 用 WebView 打开小说站
 * 用户读到想存的章节时,点"📥 抓"→ JS 桥提取正文 + 标题 → 保存到本地
 * 实际不依赖任何特定小说站(用通用选择器 + 后备规则)
 */
public class WebCaptureActivity extends Activity {

    private WebView web;
    private EditText etUrl;
    private TextView tvStatus;
    private Button btnGo, btnCapture, btnClose;
    private String currentUrl = "";
    private String bookTitle = "";
    private int chapterIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.web_capture);
        web = (WebView) findViewById(R.id.web);
        etUrl = (EditText) findViewById(R.id.etUrl);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        btnGo = (Button) findViewById(R.id.btnGo);
        btnCapture = (Button) findViewById(R.id.btnCapture);
        btnClose = (Button) findViewById(R.id.btnClose);

        String initialUrl = getIntent().getStringExtra("url");
        bookTitle = getIntent().getStringExtra("bookTitle");
        if (bookTitle == null) bookTitle = "在线书";
        if (initialUrl != null) {
            etUrl.setText(initialUrl);
            currentUrl = initialUrl;
        }

        setupWeb();
        btnGo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { go(); } });
        etUrl.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) { go(); return true; }
        });
        btnCapture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // JS 提取页面正文
                web.evaluateJavascript(EXTRACT_JS, null);
            }
        });
        btnClose.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });

        if (initialUrl != null) {
            web.loadUrl(initialUrl);
        } else {
            tvStatus.setText("请输入小说章节 URL\n例:https://fanqienovel.com/reader/100320422/1\n     https://www.biququ.com/book/xxx/1.html");
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWeb() {
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setAllowFileAccess(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.addJavascriptInterface(this, "NLAndroid");
        web.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                currentUrl = url;
                etUrl.setText(url);
                injectExtractor();
                updateStatus("已加载: " + url);
            }
        });
    }

    private void go() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) { Toast.makeText(this, "请输入 URL", Toast.LENGTH_SHORT).show(); return; }
        if (!url.startsWith("http")) url = "https://" + url;
        web.loadUrl(url);
    }

    /**
     * 注入通用正文提取器
     * 智能选择器:找最长文本区域,排除脚本/样式
     */
    private void injectExtractor() {
        web.evaluateJavascript(INJECT_JS, null);
    }

    private static final String INJECT_JS =
        "(function() {" +
        "  if (window.__NL_INJECTED) return; window.__NL_INJECTED = true;" +
        "  document.addEventListener('DOMContentLoaded', function() {});" +
        "})();";

    /**
     * JS 提取的 fallback 脚本(在页面 evaluateJavascript 调用)
     * 找正文容器(多种选择器)
     */
    private static final String EXTRACT_JS =
        "(function() {" +
        "  try {" +
        "    var sels = ['#content','#chapter-content','.content','.chapter-content','.read-content','#j_content','#txtContent','.text-content','.novel-content','.book-content','article','.article','main','.main-content'];" +
        "    var best = null, bestLen = 0;" +
        "    for (var i = 0; i < sels.length; i++) {" +
        "      var el = document.querySelector(sels[i]);" +
        "      if (el) {" +
        "        var t = (el.innerText || el.textContent || '').trim();" +
        "        if (t.length > bestLen) { bestLen = t.length; best = el; }" +
        "      }" +
        "    }" +
        "    if (!best) {" +
        "      // 启发式:找含最多 p 标签的 div" +
        "      var divs = document.querySelectorAll('div, section');" +
        "      for (var i = 0; i < divs.length; i++) {" +
        "        var ps = divs[i].querySelectorAll('p');" +
        "        if (ps.length < 3) continue;" +
        "        var t = (divs[i].innerText || '').trim();" +
        "        if (t.length > bestLen && t.length < 500000) { bestLen = t.length; best = divs[i]; }" +
        "      }" +
        "    }" +
        "    if (!best) { window.NLAndroid.onExtract('NO_CONTENT', '','未找到正文区域'); return; }" +
        "    var title = '';" +
        "    var tEl = document.querySelector('h1, h2, h3, .chapter-title, .title');" +
        "    if (tEl) title = (tEl.innerText || '').trim();" +
        "    if (!title) title = document.title;" +
        "    var text = (best.innerText || '').trim();" +
        "    text = text.replace(/\\u00A0/g, ' ').replace(/\\r/g, '');" +
        "    var lines = text.split('\\n');" +
        "    var clean = [];" +
        "    for (var i = 0; i < lines.length; i++) {" +
        "      var l = lines[i].trim();" +
        "      if (!l) continue;" +
        "      if (l.length < 2) continue;" +
        "      if (/^(第\\s*\\S+\\s*章|第\\s*\\d+\\s*章|Chapter|chapter)/i.test(l)) continue;" +
        "      clean.push(l);" +
        "    }" +
        "    var body = clean.join('\\n\\n');" +
        "    window.NLAndroid.onExtract(title, body, location.href);" +
        "  } catch(e) { window.NLAndroid.onExtract('ERR', '', e.message); }" +
        "})();";

    @JavascriptInterface
    public void onExtract(final String title, final String body, final String loc) {
        runOnUiThread(new Runnable() { public void run() {
            if ("NO_CONTENT".equals(title)) {
                updateStatus("未找到正文 — 试试滚到正文区再点抓取");
                return;
            }
            if ("ERR".equals(title)) {
                updateStatus("提取错误: " + body);
                return;
            }
            if (body == null || body.isEmpty()) {
                updateStatus("提取到空内容");
                return;
            }
            String safeTitle = title.replaceAll("[\\\\\\\\/:*?\\\"<>|]", "_").trim();
            if (safeTitle.isEmpty()) safeTitle = "Chapter " + (++chapterIndex);
            String fileName = bookTitle + "_" + safeTitle + ".txt";
            try {
                File dir = new File(getExternalFilesDir(null), "online-books");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(out);
                String content = title + "\n\n" + body + "\n\n[来源: " + loc + "]\n[时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date()) + "]\n";
                fos.write(content.getBytes("UTF-8"));
                fos.close();
                // 写入 SQLite
                Models.BookRecord b = new Models.BookRecord();
                b.filePath = out.getAbsolutePath();
                b.title = bookTitle + " - " + safeTitle;
                b.author = "在线抓取";
                b.chapters = 1;
                b.fileSize = out.length();
                b.addedTime = System.currentTimeMillis();
                b.lastReadTime = System.currentTimeMillis();
                b.progress = 0;
                b.description = "来源: " + loc;
                BookRepository.get(WebCaptureActivity.this).addBook(b);
                Toast.makeText(WebCaptureActivity.this, "✅ 已保存到书架\n" + fileName, Toast.LENGTH_LONG).show();
                updateStatus("✅ 保存成功: " + fileName + " (" + (body.length() / 1024) + " KB)");
            } catch (Throwable t) {
                Toast.makeText(WebCaptureActivity.this, "❌ 保存失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }});
    }

    private void updateStatus(String s) {
        tvStatus.setText(s);
    }
}
