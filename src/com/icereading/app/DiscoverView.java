package com.icereading.app;

import android.app.Activity;
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
 * 发现 Tab - OPDS 目录浏览 + 搜索 + 下载
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
                String sm = (e.summary != null ? e.summary : "") + (e.publisher != null ? " · " + e.publisher : "") + (e.issued != null ? " · " + e.issued : "");
                summary.setText(sm);
                btnDl.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { download(e); }
                });
                if (e.coverUrl != null && !e.coverUrl.isEmpty()) {
                    loadCover(iv, e.coverUrl);
                } else {
                    iv.setImageResource(android.R.drawable.ic_menu_gallery);
                }
                return cv;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                download(entries.get(pos));
            }
        });

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refresh(); }
        });
        btnAddSource.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                act.startActivity(new android.content.Intent(act, OpdsActivity.class));
            }
        });
        btnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                etSearch.setVisibility(etSearch.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        etSearch.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                String q = v.getText().toString().trim();
                if (q.isEmpty()) refresh();
                else search(q);
                return true;
            }
        });
        refreshSources();
    }

    public View getView() { return root; }

    public void refresh() {
        List<Models.OpdsSource> sources = BookRepository.get(act).listOpdsSources();
        if (sources.isEmpty()) {
            entries.clear();
            adapter.notifyDataSetChanged();
            tvEmpty.setText("无 OPDS 源\n点 + 添加");
            tvEmpty.setVisibility(View.VISIBLE);
            lv.setVisibility(View.GONE);
            return;
        }
        Models.OpdsSource src = sources.get(spSource.getSelectedItemPosition());
        if (src == null) src = sources.get(0);
        fetchSource(src, null);
    }

    private void refreshSources() {
        List<Models.OpdsSource> sources = BookRepository.get(act).listOpdsSources();
        List<String> labels = new ArrayList<String>();
        for (Models.OpdsSource s : sources) labels.add(s.name);
        android.widget.ArrayAdapter<String> ad = new android.widget.ArrayAdapter<String>(act, android.R.layout.simple_spinner_item, labels) {
            public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                ((TextView) v).setTextColor(0xFFE8E8E8);
                return v;
            }
        };
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSource.setAdapter(ad);
    }

    public void refreshOnResume() {
        refreshSources();
        refresh();
    }

    private void search(String q) {
        List<Models.OpdsSource> sources = BookRepository.get(act).listOpdsSources();
        if (sources.isEmpty()) { return; }
        Models.OpdsSource src = sources.get(spSource.getSelectedItemPosition());
        if (src == null) src = sources.get(0);
        fetchSource(src, q);
    }

    private void fetchSource(final Models.OpdsSource src, final String query) {
        tvEmpty.setText("加载中…");
        tvEmpty.setVisibility(View.VISIBLE);
        lv.setVisibility(View.GONE);
        new Thread(new Runnable() {
            public void run() {
                new OpdsClient().fetch(src.url, src.username, src.password, src.bearerToken, new OpdsClient.Callback() {
                    public void onResult(final List<Models.OpdsEntry> result, String next) {
                        ui.post(new Runnable() {
                            public void run() {
                                entries.clear();
                                entries.addAll(result);
                                adapter.notifyDataSetChanged();
                                tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                                lv.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
                                if (entries.isEmpty()) tvEmpty.setText("无内容\n下拉刷新或换源");
                            }
                        });
                    }
                    public void onError(final String err) {
                        ui.post(new Runnable() {
                            public void run() {
                                entries.clear();
                                adapter.notifyDataSetChanged();
                                tvEmpty.setText("❌ " + err);
                                tvEmpty.setVisibility(View.VISIBLE);
                                lv.setVisibility(View.GONE);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void loadCover(final ImageView iv, final String url) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.net.HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(15000);
                    con.setRequestProperty("User-Agent", "iceReading/1.0");
                    if (con.getResponseCode() == 200) {
                        final android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(con.getInputStream());
                        if (bm != null) {
                            ui.post(new Runnable() { public void run() { iv.setImageBitmap(bm); } });
                        }
                    }
                } catch (Throwable ignore) {}
            }
        }).start();
    }

    private void download(final Models.OpdsEntry e) {
        if (e.downloadUrl == null) { Toast.makeText(act, "无下载链接", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(act, "开始下载: " + e.title, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.net.HttpURLConnection con = (HttpURLConnection) new URL(e.downloadUrl).openConnection();
                    con.setRequestProperty("User-Agent", "iceReading/1.0");
                    con.setConnectTimeout(15000);
                    con.setReadTimeout(300000);
                    int code = con.getResponseCode();
                    if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
                    String name = (e.title != null ? e.title.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff.-]", "_") : "book") + ".epub";
                    File f = new File(act.getExternalFilesDir(null), name);
                    InputStream is = con.getInputStream();
                    FileOutputStream fos = new FileOutputStream(f);
                    byte[] buf = new byte[8192];
                    int n;
                    long total = con.getContentLengthLong();
                    long done = 0;
                    while ((n = is.read(buf)) > 0) { fos.write(buf, 0, n); done += n; }
                    fos.close();
                    is.close();
                    final File ff = f;
                    ui.post(new Runnable() {
                        public void run() { Toast.makeText(act, "✅ 下载完成,正在导入…", Toast.LENGTH_SHORT).show(); }
                    });
                    // 记 DB
                    Models.DownloadTask t = new Models.DownloadTask();
                    t.url = e.downloadUrl;
                    t.title = e.title;
                    t.filePath = ff.getAbsolutePath();
                    t.totalBytes = total;
                    t.downloadedBytes = total;
                    t.status = 3;
                    t.progress = 100;
                    BookRepository.get(act).addDownload(t);
                    // 导入
                    final long bookId = BookImporter.doImport(act, ff);
                    ui.post(new Runnable() {
                        public void run() {
                            if (bookId > 0) {
                                Toast.makeText(act, "✅ 导入成功: " + ff.getName(), Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(act, "⚠️ 导入失败,文件在: " + ff.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Throwable t) {
                    final String err = t.getMessage();
                    ui.post(new Runnable() {
                        public void run() { Toast.makeText(act, "❌ 下载失败: " + err, Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        }).start();
    }
}
