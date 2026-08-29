package com.icereading.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 书架 Tab - 2 列网格布局
 * 支持:导入本地 EPUB / 搜索 / 排序
 */
public class BookshelfView {

    private final Activity act;
    private View root;
    private GridView gv;
    private TextView tvStats, tvEmpty;
    private EditText etSearch;
    private Button btnImport, btnFilter;
    private final List<Models.BookRecord> data = new ArrayList<Models.BookRecord>();
    private BaseAdapter adapter;
    private String currentFilter = "";
    private String currentSort = "lastRead";

    public BookshelfView(Activity act) {
        this.act = act;
        root = LayoutInflater.from(act).inflate(R.layout.bookshelf, null);
        gv = (GridView) root.findViewById(R.id.gvBooks);
        tvStats = (TextView) root.findViewById(R.id.tvStats);
        tvEmpty = (TextView) root.findViewById(R.id.tvEmpty);
        etSearch = (EditText) root.findViewById(R.id.etSearch);
        btnImport = (Button) root.findViewById(R.id.btnImport);
        btnFilter = (Button) root.findViewById(R.id.btnFilter);

        adapter = new BaseAdapter() {
            public int getCount() { return data.size(); }
            public Object getItem(int i) { return data.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup parent) {
                if (cv == null) cv = LayoutInflater.from(act).inflate(R.layout.book_item, parent, false);
                Models.BookRecord b = data.get(i);
                ImageView iv = (ImageView) cv.findViewById(R.id.ivCover);
                TextView title = (TextView) cv.findViewById(R.id.tvTitle);
                TextView author = (TextView) cv.findViewById(R.id.tvAuthor);
                TextView progress = (TextView) cv.findViewById(R.id.tvProgress);
                title.setText(b.title != null && !b.title.isEmpty() ? b.title : "未命名");
                author.setText(b.author != null && !b.author.isEmpty() ? b.author : "未知作者");
                if (b.coverPath != null && new File(b.coverPath).exists()) {
                    iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(b.coverPath));
                } else {
                    iv.setImageResource(android.R.drawable.ic_menu_gallery);
                }
                if (b.progress > 0) {
                    progress.setVisibility(View.VISIBLE);
                    progress.setText("已读 " + (int)(b.progress * 100) + "%");
                } else {
                    progress.setVisibility(View.GONE);
                }
                return cv;
            }
        };
        gv.setAdapter(adapter);
        gv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Models.BookRecord b = data.get(pos);
                Intent i = new Intent(act, ReaderActivity.class);
                i.putExtra("bookId", b.id);
                i.putExtra("filePath", b.filePath);
                act.startActivity(i);
            }
        });
        gv.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                showBookMenu(data.get(pos));
                return true;
            }
        });

        btnImport.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openFilePicker(); }
        });
        btnFilter.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showFilterMenu(); }
        });
        etSearch.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                currentFilter = v.getText().toString().trim();
                refresh();
                return true;
            }
        });
        refresh();
    }

    public View getView() { return root; }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String name = queryDisplayName(uri);
                if (name == null) name = "book-" + System.currentTimeMillis() + ".epub";
                if (!name.endsWith(".epub")) name = name + ".epub";
                try {
                    File f = new File(act.getExternalFilesDir(null), name);
                    InputStream is = act.getContentResolver().openInputStream(uri);
                    FileOutputStream fos = new FileOutputStream(f);
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                    fos.close();
                    is.close();
                    importEpubFile(f);
                } catch (Throwable t) {
                    Toast.makeText(act, "复制失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    public void refresh() {
        data.clear();
        BookRepository repo = BookRepository.get(act);
        String orderBy = "last_read_time DESC, added_time DESC";
        if (currentSort.equals("added")) orderBy = "added_time DESC";
        else if (currentSort.equals("title")) orderBy = "title COLLATE NOCASE";
        else if (currentSort.equals("author")) orderBy = "author COLLATE NOCASE, title";
        List<Models.BookRecord> all = currentFilter.isEmpty() ? repo.listBooks(orderBy) : repo.searchBooks(currentFilter);
        data.addAll(all);
        if (currentSort.equals("lastRead")) {
            java.util.Collections.sort(data, new java.util.Comparator<Models.BookRecord>() {
                public int compare(Models.BookRecord a, Models.BookRecord b) {
                    if (a.lastReadTime == b.lastReadTime) return Long.compare(b.addedTime, a.addedTime);
                    return Long.compare(b.lastReadTime, a.lastReadTime);
                }
            });
        }
        tvStats.setText("📚 " + data.size() + " 本书 · 总章节 " + repo.getTotalChapters());
        tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        gv.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"application/epub+zip", "application/zip", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        act.startActivityForResult(intent, 1001);
    }

    private String queryDisplayName(Uri uri) {
        try {
            Cursor c = act.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) {
                    String n = c.getString(idx);
                    c.close();
                    return n;
                }
                c.close();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private void importEpubFile(File f) {
        final File target = f;
        new Thread(new Runnable() {
            public void run() {
                try {
                    final long bookId = BookImporter.doImport(act, target);
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            if (bookId > 0) {
                                Toast.makeText(act, "✅ 已导入", Toast.LENGTH_SHORT).show();
                                refresh();
                            } else {
                                Toast.makeText(act, "❌ 导入失败", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Throwable t) {
                    final String err = t.getMessage();
                    act.runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(act, "❌ " + err, Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        }).start();
    }

    private void showBookMenu(final Models.BookRecord b) {
        final String[] items = {"📖 继续阅读", "📑 章节目录", "🔖 书签", "🗑️ 删除"};
        new android.app.AlertDialog.Builder(act)
            .setTitle(b.title)
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    switch (w) {
                        case 0: { Intent i = new Intent(act, ReaderActivity.class); i.putExtra("bookId", b.id); act.startActivity(i); break; }
                        case 1: { Intent i = new Intent(act, ReaderActivity.class); i.putExtra("bookId", b.id); i.putExtra("showToc", true); act.startActivity(i); break; }
                        case 2: showBookmarks(b); break;
                        case 3: {
                            new android.app.AlertDialog.Builder(act)
                                .setTitle("删除?")
                                .setMessage("将从书架移除" + b.title + "(文件保留)")
                                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                                    public void onClick(android.content.DialogInterface d, int x) {
                                        BookRepository.get(act).deleteBook(b.id);
                                        refresh();
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                            break;
                        }
                    }
                }
            })
            .show();
    }

    private void showBookmarks(Models.BookRecord b) {
        java.util.List<Models.Bookmark> bms = BookRepository.get(act).listBookmarks(b.id);
        if (bms.isEmpty()) {
            Toast.makeText(act, "无书签", Toast.LENGTH_SHORT).show();
            return;
        }
        final Models.Bookmark[] arr = bms.toArray(new Models.Bookmark[0]);
        String[] titles = new String[arr.length];
        for (int i = 0; i < arr.length; i++) titles[i] = (i + 1) + ". " + (arr[i].chapterTitle != null ? arr[i].chapterTitle : "未命名") + " (页 " + (arr[i].cfiPage + 1) + ")";
        new android.app.AlertDialog.Builder(act)
            .setTitle(b.title + " - 书签(" + bms.size() + ")")
            .setItems(titles, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    Intent i = new Intent(act, ReaderActivity.class);
                    i.putExtra("bookId", b.id);
                    i.putExtra("bookmarkId", arr[w].id);
                    act.startActivity(i);
                }
            })
            .setNegativeButton("关闭", null)
            .show();
    }

    private void showFilterMenu() {
        final String[] items = {"最近阅读", "添加时间 ↓", "书名 A-Z", "作者 A-Z"};
        new android.app.AlertDialog.Builder(act)
            .setTitle("排序")
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    currentSort = w == 0 ? "lastRead" : w == 1 ? "added" : w == 2 ? "title" : "author";
                    refresh();
                }
            })
            .show();
    }
}
