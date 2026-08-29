package com.icereading.app;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

/**
 * 下载 Tab - 显示下载任务列表
 */
public class DownloadView {

    private final Activity act;
    private View root;
    private ListView lv;
    private TextView tvEmpty;
    private final List<Models.DownloadTask> data = new java.util.ArrayList<Models.DownloadTask>();
    private BaseAdapter adapter;

    public DownloadView(Activity act) {
        this.act = act;
        root = LayoutInflater.from(act).inflate(R.layout.download, null);
        lv = (ListView) root.findViewById(R.id.lvTasks);
        tvEmpty = (TextView) root.findViewById(R.id.tvEmpty);
        adapter = new BaseAdapter() {
            public int getCount() { return data.size(); }
            public Object getItem(int i) { return data.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup parent) {
                if (cv == null) cv = LayoutInflater.from(act).inflate(android.R.layout.simple_list_item_2, parent, false);
                Models.DownloadTask t = data.get(i);
                ((TextView) cv.findViewById(android.R.id.text1)).setText(t.title != null ? t.title : t.url);
                String s;
                switch (t.status) {
                    case 0: s = "等待中"; break;
                    case 1: s = "下载中 " + t.progress + "%"; break;
                    case 2: s = "已暂停"; break;
                    case 3: s = "✅ 已完成 · " + (t.totalBytes / 1024) + " KB"; break;
                    case 4: s = "❌ 失败: " + (t.errorMessage != null ? t.errorMessage : ""); break;
                    default: s = "?";
                }
                ((TextView) cv.findViewById(android.R.id.text2)).setText(s);
                ((TextView) cv.findViewById(android.R.id.text1)).setTextColor(0xFFE8E8E8);
                ((TextView) cv.findViewById(android.R.id.text2)).setTextColor(0xFF888888);
                cv.setBackgroundColor(0xFF1C2128);
                return cv;
            }
        };
        lv.setAdapter(adapter);
        refresh();
    }

    public View getView() { return root; }

    public void refresh() {
        data.clear();
        data.addAll(BookRepository.get(act).listDownloads(-1));
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
