package com.icereading.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 发现 Tab v2.0
 *
 * 数据源类型:
 *  - local: 本地 SQLite 全文搜索(始终可用,无需网络)
 *  - web: 必应/Google 全网搜索(找书)
 *  - librivox: LibriVox 英文免费有声书(2026 仍可访问)
 *  - opds: 用户自定义 OPDS(内网 Calibre 等)
 */
public class DiscoverView {

    private final Activity act;
    private View root;
    private Spinner spSource;
    private Button btnRefresh, btnAddSource, btnSearch;
    private EditText etSearch;
    private ListView lv;
    private TextView tvEmpty;
    private final List<Models.OpdsEntry> entries = new ArrayList<Models.OpdsEntry>();
    private BaseAdapter adapter;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private static final int TYPE_LOCAL = 0;
    private static final int TYPE_WEB = 1;
    private static final int TYPE_LIBRIVOX = 2;
    private static final int TYPE_OPDS = 3;

    public DiscoverView(Activity act) {
        this.act = act;
        root = LayoutInflater.from(act).inflate(R.layout.discover, null);
        spSource = (Spinner) root.findViewById(R.id.spSource);
        btnRefresh = (Button) root.findViewById(R.id.btnRefresh);
        btnAddSource = (Button) root.findViewById(R.id.btnAddSource);
        btnSearch = (Button) root.findViewById(R.id.btnSearch);
        etSearch = (EditText) root.findViewById(R.id.etSearch);
        lv = (ListView) root.findViewById(R.id.lvEntries);
        tvEmpty = (TextView) root.findViewById(R.id.tvEmpty);

        adapter = new BaseAdapter() {
            public int getCount() { return entries.size(); }
            public Object getItem(int i) { return entries.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup parent) {
                if (cv == null) cv = LayoutInflater.from(act).inflate(R.layout.opds_item, parent, false);
                final Models.OpdsEntry e = entries.get(i);
                TextView title = (TextView) cv.findViewById(R.id.tvTitle);
                TextView author = (TextView) cv.findViewById(R.id.tvAuthor);
                TextView summary = (TextView) cv.findViewById(R.id.tvSummary);
                Button btnDl = (Button) cv.findViewById(R.id.btnDownload);
                ImageView iv = (ImageView) cv.findViewById(R.id.ivCover);
                title.setText(e.title != null ? e.title : "(无标题)");
                author.setText(e.author != null ? e.author : "");
                String sm = (e.summary != null ? e.summary : "") + (e.publisher != null ? " · " + e.publisher : "") + (e.issued != null ? " · " + e.issued : "") + (e.format != null ? " · " + e.format : "");
                summary.setText(sm);
                btnDl.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { download(e); } });
                if (e.coverUrl != null && !e.coverUrl.isEmpty()) loadCover(iv, e.coverUrl);
                else iv.setImageResource(android.R.drawable.ic_menu_gallery);
                return cv;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                download(entries.get(pos));
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { refresh(); } });
        btnAddSource.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { act.startActivity(new android.content.Intent(act, OpdsActivity.class)); } });
        btnSearch.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { etSearch.setVisibility(etSearch.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); } });
        etSearch.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                String q = v.getText().toString().trim();
                if (!q.isEmpty()) doSearch(q);
                return true;
            }
        });
        refreshSources();
    }

    public View getView() { return root; }

    private void refreshSources() {
        List<Models.OpdsSource> custom = BookRepository.get(act).listOpdsSources();
        // v2.0 默认 4 个源(local 始终可用 + web 搜索 + LibriVox + 用户 OPDS)
        String[] names = new String[4 + custom.size()];
        int[] types = new int[4 + custom.size()];
        names[0] = "📚 本地书库(本地)"; types[0] = TYPE_LOCAL;
        names[1] = "🌐 全网搜索(必应/Google)"; types[1] = TYPE_WEB;
        names[2] = "🎧 LibriVox 英文有声书"; types[2] = TYPE_LIBRIVOX;
        names[3] = "🔌 自定义 OPDS(我添加的)"; types[3] = TYPE_OPDS;
        for (int i = 0; i < custom.size(); i++) {
            names[4 + i] = "  • " + custom.get(i).name;
            types[4 + i] = TYPE_OPDS;
        }
        // 简易 array adapter(避免依赖 ArrayAdapter)
        final int[] t = types;
        final String[] n = names;
        android.widget.SpinnerAdapter sa = new android.widget.BaseAdapter() {
            public int getCount() { return n.length; }
            public Object getItem(int i) { return n[i]; }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup parent) {
                if (cv == null) cv = new android.widget.TextView(act);
                ((android.widget.TextView) cv).setText(n[i]);
                ((android.widget.TextView) cv).setTextColor(0xFFE8E8E8);
                ((android.widget.TextView) cv).setTextSize(14);
                ((android.widget.TextView) cv).setPadding(20, 16, 20, 16);
                return cv;
            }
            public View getDropDownView(int i, View cv, ViewGroup parent) {
                if (cv == null) cv = new android.widget.TextView(act);
                ((android.widget.TextView) cv).setText(n[i]);
                ((android.widget.TextView) cv).setTextColor(0xFFE8E8E8);
                ((android.widget.TextView) cv).setPadding(20, 20, 20, 20);
                return cv;
            }
        };
        spSource.setAdapter(sa);
    }

    public void refresh() {
        int type = currentType();
        if (type == TYPE_LOCAL) { showLocal(); return; }
        tvEmpty.setText("加载中…");
        tvEmpty.setVisibility(View.VISIBLE);
        lv.setVisibility(View.GONE);
        if (type == TYPE_WEB) {
            // 全网搜索:用必应
            new OpdsClient().bingSearch("epub", new OpdsClient.Callback() {
                public void onResult(List<Models.OpdsEntry> result) {
                    ui.post(new Runnable() { public void run() { showResults(result, "全网搜索:必应(搜\"epub\"相关)"); } });
                }
                public void onError(final String err) {
                    ui.post(new Runnable() { public void run() {
                        tvEmpty.setText("❌ " + err);
                        tvEmpty.setVisibility(View.VISIBLE);
                        lv.setVisibility(View.GONE);
                    }});
                }
            });
        } else if (type == TYPE_LIBRIVOX) {
            fetchLibriVox();
        } else if (type == TYPE_OPDS) {
            fetchCustomOpds();
        }
    }

    private void doSearch(String query) {
        int type = currentType();
        if (type == TYPE_LOCAL) { searchLocal(query); return; }
        if (type == TYPE_WEB) {
            new OpdsClient().bingSearch(query, new OpdsClient.Callback() {
                public void onResult(List<Models.OpdsEntry> result) {
                    ui.post(new Runnable() { public void run() { showResults(result, "搜索: " + query); } });
                }
                public void onError(final String err) {
                    ui.post(new Runnable() { public void run() {
                        tvEmpty.setText("❌ " + err);
                        tvEmpty.setVisibility(View.VISIBLE);
                        lv.setVisibility(View.GONE);
                    }});
                }
            });
        } else if (type == TYPE_LIBRIVOX) {
            new OpdsClient().fetch("https://librivox.org/api/feed/audiobooks", null, null, null, new OpdsClient.Callback() {
                public void onResult(List<Models.OpdsEntry> result) {
                    // 客户端过滤
                    List<Models.OpdsEntry> matched = new ArrayList<Models.OpdsEntry>();
                    String q = query.toLowerCase();
                    for (Models.OpdsEntry e : result) {
                        if ((e.title != null && e.title.toLowerCase().contains(q)) ||
                            (e.author != null && e.author.toLowerCase().contains(q))) matched.add(e);
                    }
                    final List<Models.OpdsEntry> f = matched.isEmpty() ? result : matched;
                    ui.post(new Runnable() { public void run() { showResults(f, "LibriVox 搜索: " + query); } });
                }
                public void onError(final String err) {
                    ui.post(new Runnable() { public void run() {
                        tvEmpty.setText("❌ " + err);
                        tvEmpty.setVisibility(View.VISIBLE);
                        lv.setVisibility(View.GONE);
                    }});
                }
            });
        } else if (type == TYPE_OPDS) {
            fetchCustomOpds();
        }
    }

    private int currentType() {
        int pos = spSource.getSelectedItemPosition();
        int idx = 0;
        if (pos >= 3) {
            // 自定义 OPDS
            return TYPE_OPDS;
        }
        switch (pos) {
            case 0: return TYPE_LOCAL;
            case 1: return TYPE_WEB;
            case 2: return TYPE_LIBRIVOX;
            default: return TYPE_LOCAL;
        }
    }

    private void showLocal() {
        List<Models.BookRecord> books = BookRepository.get(act).listBooks(null);
        entries.clear();
        for (Models.BookRecord b : books) {
            Models.OpdsEntry e = new Models.OpdsEntry();
            e.id = "book-" + b.id;
            e.title = b.title;
            e.author = b.author;
            e.summary = (b.description != null ? b.description : "") + " · " + b.chapters + " 章";
            e.language = b.language;
            e.format = "epub";
            // 本地文件
            e.downloadUrl = "file://" + b.filePath;
            e.links.add(makeLink("local", b.filePath));
            entries.add(e);
        }
        if (entries.isEmpty()) {
            tvEmpty.setText("本地书库为空\n点书架 tab 导入 EPUB 文件");
            tvEmpty.setVisibility(View.VISIBLE);
            lv.setVisibility(View.GONE);
        } else {
            showResults(entries, "本地书库(" + entries.size() + " 本)");
        }
    }

    private void searchLocal(String query) {
        List<Models.BookRecord> books = BookRepository.get(act).searchBooks(query);
        entries.clear();
        for (Models.BookRecord b : books) {
            Models.OpdsEntry e = new Models.OpdsEntry();
            e.id = "book-" + b.id;
            e.title = b.title;
            e.author = b.author;
            e.summary = b.description;
            e.format = "epub";
            e.downloadUrl = "file://" + b.filePath;
            entries.add(e);
        }
        if (entries.isEmpty()) {
            tvEmpty.setText("无匹配: " + query);
            tvEmpty.setVisibility(View.VISIBLE);
            lv.setVisibility(View.GONE);
        } else {
            showResults(entries, "本地搜索: " + query + " (" + entries.size() + " 结果)");
        }
    }

    private void fetchLibriVox() {
        new OpdsClient().fetch("https://librivox.org/api/feed/audiobooks", null, null, null, new OpdsClient.Callback() {
            public void onResult(List<Models.OpdsEntry> result) {
                ui.post(new Runnable() { public void run() { showResults(result, "LibriVox(免费英文有声书," + result.size() + " 项)"); } });
            }
            public void onError(final String err) {
                ui.post(new Runnable() { public void run() {
                    tvEmpty.setText("❌ " + err + "\n(网络问题或被防火墙拦截,试其他源)");
                    tvEmpty.setVisibility(View.VISIBLE);
                    lv.setVisibility(View.GONE);
                }});
            }
        });
    }

    private void fetchCustomOpds() {
        int pos = spSource.getSelectedItemPosition();
        java.util.List<Models.OpdsSource> custom = BookRepository.get(act).listOpdsSources();
        int idx = pos - 4;
        if (idx < 0 || idx >= custom.size()) {
            tvEmpty.setText("请先点 + 添加 OPDS 源");
            tvEmpty.setVisibility(View.VISIBLE);
            lv.setVisibility(View.GONE);
            return;
        }
        Models.OpdsSource src = custom.get(idx);
        new OpdsClient().fetch(src.url, src.username, src.password, src.bearerToken, new OpdsClient.Callback() {
            public void onResult(List<Models.OpdsEntry> result) {
                ui.post(new Runnable() { public void run() { showResults(result, src.name + "(" + result.size() + ")"); } });
            }
            public void onError(final String err) {
                ui.post(new Runnable() { public void run() {
                    tvEmpty.setText("❌ " + err);
                    tvEmpty.setVisibility(View.VISIBLE);
                    lv.setVisibility(View.GONE);
                }});
            }
        });
    }

    private void showResults(List<Models.OpdsEntry> list, String title) {
        entries.clear();
        entries.addAll(list);
        adapter.notifyDataSetChanged();
        tvEmpty.setText(entries.isEmpty() ? "无内容" : title);
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        lv.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void refreshOnResume() {
        refreshSources();
        refresh();
    }

    private void loadCover(final ImageView iv, final String url) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setConnectTimeout(8000); con.setReadTimeout(15000);
                    con.setRequestProperty("User-Agent", "iceReading/2.0");
                    if (con.getResponseCode() == 200) {
                        final android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(con.getInputStream());
                        if (bm != null) ui.post(new Runnable() { public void run() { iv.setImageBitmap(bm); } });
                    }
                } catch (Throwable ignore) {}
            }
        }).start();
    }

    private Models.OpdsEntry makeLink(String rel, String href) {
        Models.OpdsEntry l = new Models.OpdsEntry();
        l.id = rel;
        l.downloadUrl = href;
        return l;
    }

    private void download(final Models.OpdsEntry e) {
        if (e.downloadUrl == null) { Toast.makeText(act, "无下载链接", Toast.LENGTH_SHORT).show(); return; }
        // 本地文件直接打开
        if (e.downloadUrl.startsWith("file://")) {
            try {
                Intent view = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(e.downloadUrl));
                view.setDataAndType(android.net.Uri.parse(e.downloadUrl), "application/epub+zip");
                act.startActivity(view);
            } catch (Throwable t) {
                Toast.makeText(act, "请用 EPUB 阅读器打开", Toast.LENGTH_LONG).show();
            }
            return;
        }
        // 在线 EPUB 链接:打开浏览器(用户自己下载,或点 Import 添加)
        new android.app.AlertDialog.Builder(act)
            .setTitle("下载")
            .setMessage("打开浏览器下载:\n" + e.title + "\n" + e.downloadUrl)
            .setPositiveButton("浏览器打开", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    try {
                        Intent browse = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(e.downloadUrl));
                        act.startActivity(browse);
                    } catch (Throwable t) {
                        Toast.makeText(act, "无法打开", Toast.LENGTH_LONG).show();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
