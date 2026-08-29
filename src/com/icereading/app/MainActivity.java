package com.icereading.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 冰读主 Activity - 4 tab 切换(书架/发现/下载/设置)
 * 单 Activity 多 fragment-style view(为最小 APK,不用 Fragment)
 */
public class MainActivity extends Activity {

    private static final String P = "icereading_main";
    private FrameLayout container;
    private View tabBookshelf, tabDiscover, tabDownload, tabSettings;
    private Button btnTabBookshelf, btnTabDiscover, btnTabDownload, btnTabSettings;

    private BookshelfView bookshelfView;
    private DiscoverView discoverView;
    private DownloadView downloadView;
    private SettingsView settingsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        container = (FrameLayout) findViewById(R.id.fragmentContainer);
        btnTabBookshelf = (Button) findViewById(R.id.tabBookshelf);
        btnTabDiscover = (Button) findViewById(R.id.tabDiscover);
        btnTabDownload = (Button) findViewById(R.id.tabDownload);
        btnTabSettings = (Button) findViewById(R.id.tabSettings);

        // 初始化各 tab view(懒加载)
        bookshelfView = new BookshelfView(this);
        discoverView = new DiscoverView(this);
        downloadView = new DownloadView(this);
        settingsView = new SettingsView(this);

        btnTabBookshelf.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(0); }
        });
        btnTabDiscover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(1); }
        });
        btnTabDownload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(2); }
        });
        btnTabSettings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showTab(3); }
        });

        showTab(0);

        // 处理 Intent(EPUB 打开)
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null) return;
            String mime = intent.getType();
            if (mime != null && (mime.contains("epub") || mime.contains("zip"))) {
                // 导入 EPUB
                importEpub(uri);
            }
        }
    }

    private void importEpub(Uri uri) {
        try {
            String name = "book-" + System.currentTimeMillis() + ".epub";
            File f = new File(getExternalFilesDir(null), name);
            InputStream is = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(f);
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();
            Toast.makeText(this, "已导入: " + name, Toast.LENGTH_SHORT).show();
            bookshelfView.refresh();
        } catch (Throwable t) {
            Toast.makeText(this, "导入失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showTab(int idx) {
        View v;
        switch (idx) {
            case 0: v = bookshelfView.getView(); break;
            case 1: v = discoverView.getView(); break;
            case 2: v = downloadView.getView(); break;
            case 3: v = settingsView.getView(); break;
            default: v = bookshelfView.getView(); break;
        }
        container.removeAllViews();
        container.addView(v);
        // 切换高亮
        int colorActive = 0xFFFF6B35;
        int colorInactive = 0xFF888888;
        btnTabBookshelf.setTextColor(idx == 0 ? colorActive : colorInactive);
        btnTabDiscover.setTextColor(idx == 1 ? colorActive : colorInactive);
        btnTabDownload.setTextColor(idx == 2 ? colorActive : colorInactive);
        btnTabSettings.setTextColor(idx == 3 ? colorActive : colorInactive);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bookshelfView.refresh();
        downloadView.refresh();
    }
}
