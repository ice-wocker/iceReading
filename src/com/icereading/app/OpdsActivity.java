package com.icereading.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * OPDS 源管理 — 添加/编辑/启用/删除/恢复默认
 */
public class OpdsActivity extends Activity {

    private ListView lv;
    private final java.util.List<Models.OpdsSource> data = new java.util.ArrayList<Models.OpdsSource>();
    private BaseAdapter adapter;

    /**
     * 6 个默认 OPDS 源(2026-08 验证可用)
     */
    public static final String[][] DEFAULT_SOURCES = new String[][]{
        // name, url
        {"古登堡 Project Gutenberg", "https://www.gutenberg.org/ebooks/opds/"},
        {"Standard Ebooks", "https://standardebooks.org/opds/"},
        {"LibriVox(免费有声书)", "https://librivox.org/api/feed/audiobooks/"},
        {"Feedbooks(公版/原创)", "https://www.feedbooks.com/books/opds"},
        {"ManyBooks", "https://manybooks.net/opds/"},
        {"OPDS.io 发现(目录聚合)", "https://opds.io/"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.opds);
        lv = (ListView) findViewById(R.id.lvSources);
        Button btnAdd = (Button) findViewById(R.id.btnAdd);
        Button btnReset = (Button) findViewById(R.id.btnReset);

        adapter = new BaseAdapter() {
            public int getCount() { return data.size(); }
            public Object getItem(int i) { return data.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup parent) {
                if (cv == null) cv = LayoutInflater.from(OpdsActivity.this).inflate(android.R.layout.simple_list_item_2, parent, false);
                Models.OpdsSource s = data.get(i);
                ((TextView) cv.findViewById(android.R.id.text1)).setText(s.name + (s.enabled == 0 ? "  [禁用]" : ""));
                StringBuilder sb = new StringBuilder();
                sb.append(s.url);
                if (s.username != null && !s.username.isEmpty()) sb.append(" [Auth]");
                else if (s.bearerToken != null && !s.bearerToken.isEmpty()) sb.append(" [Bearer]");
                sb.append("  长按编辑/删除");
                ((TextView) cv.findViewById(android.R.id.text2)).setText(sb.toString());
                ((TextView) cv.findViewById(android.R.id.text1)).setTextColor(0xFFE8E8E8);
                ((TextView) cv.findViewById(android.R.id.text2)).setTextColor(0xFF888888);
                cv.setBackgroundColor(0xFF1C2128);
                return cv;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                toggleEnabled(pos);
            }
        });
        lv.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                showEditDialog(data.get(pos), pos);
                return true;
            }
        });
        btnAdd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showEditDialog(null, -1); }
        });
        btnReset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(OpdsActivity.this)
                    .setTitle("恢复默认?")
                    .setMessage("将清空所有 OPDS 源并恢复 6 个默认源")
                    .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            for (Models.OpdsSource s : data) BookRepository.get(OpdsActivity.this).deleteOpdsSource(s.id);
                            for (String[] ds : DEFAULT_SOURCES) {
                                Models.OpdsSource s = new Models.OpdsSource();
                                s.name = ds[0]; s.url = ds[1]; s.enabled = 1;
                                BookRepository.get(OpdsActivity.this).addOpdsSource(s);
                            }
                            refresh();
                            Toast.makeText(OpdsActivity.this, "已恢复 " + DEFAULT_SOURCES.length + " 个默认源", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        });
        refresh();
        // 如果没源,自动添加默认
        if (data.isEmpty()) {
            for (String[] ds : DEFAULT_SOURCES) {
                Models.OpdsSource s = new Models.OpdsSource();
                s.name = ds[0]; s.url = ds[1]; s.enabled = 1;
                BookRepository.get(this).addOpdsSource(s);
            }
            refresh();
        }
    }

    public void refresh() {
        data.clear();
        data.addAll(BookRepository.get(this).listOpdsSources());
        adapter.notifyDataSetChanged();
    }

    private void toggleEnabled(int pos) {
        Models.OpdsSource s = data.get(pos);
        s.enabled = s.enabled == 0 ? 1 : 0;
        BookRepository.get(this).updateOpdsSource(s);
        refresh();
    }

    private void showEditDialog(final Models.OpdsSource existing, final int pos) {
        final EditText etName = new EditText(this);
        etName.setHint("显示名(如 私人书库)");
        final EditText etUrl = new EditText(this);
        etUrl.setHint("OPDS 入口 URL");
        final EditText etUser = new EditText(this);
        etUser.setHint("Basic Auth 用户名(可选)");
        final EditText etPass = new EditText(this);
        etPass.setHint("Basic Auth 密码(可选)");
        final EditText etBearer = new EditText(this);
        etBearer.setHint("Bearer Token(可选)");
        if (existing != null) {
            etName.setText(existing.name);
            etUrl.setText(existing.url);
            etUser.setText(existing.username);
            etPass.setText(existing.password);
            etBearer.setText(existing.bearerToken);
        }
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(30, 20, 30, 20);
        box.addView(etName);
        box.addView(etUrl);
        box.addView(etUser);
        box.addView(etPass);
        box.addView(etBearer);
        android.app.AlertDialog.Builder ad = new android.app.AlertDialog.Builder(this)
            .setTitle(existing == null ? "+ 添加 OPDS 源" : "编辑 OPDS 源")
            .setView(box);
        if (existing != null) {
            ad.setNegativeButton("删除", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    BookRepository.get(OpdsActivity.this).deleteOpdsSource(existing.id);
                    refresh();
                }
            });
        }
        ad.setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) {
                String n = etName.getText().toString().trim();
                String u = etUrl.getText().toString().trim();
                if (n.isEmpty() || u.isEmpty()) {
                    Toast.makeText(OpdsActivity.this, "名称和 URL 必填", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (existing == null) {
                    Models.OpdsSource s = new Models.OpdsSource();
                    s.name = n; s.url = u;
                    s.username = etUser.getText().toString().trim();
                    s.password = etPass.getText().toString().trim();
                    s.bearerToken = etBearer.getText().toString().trim();
                    s.enabled = 1;
                    BookRepository.get(OpdsActivity.this).addOpdsSource(s);
                } else {
                    existing.name = n;
                    existing.url = u;
                    existing.username = etUser.getText().toString().trim();
                    existing.password = etPass.getText().toString().trim();
                    existing.bearerToken = etBearer.getText().toString().trim();
                    BookRepository.get(OpdsActivity.this).updateOpdsSource(existing);
                }
                refresh();
            }
        });
        ad.setNeutralButton("取消", null);
        ad.show();
    }
}
