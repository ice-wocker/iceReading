package com.icereading.app;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

/**
 * 设置 Tab - 阅读/行为/网络/数据
 */
public class SettingsView {

    private final Activity act;
    private View root;

    public SettingsView(Activity act) {
        this.act = act;
        root = LayoutInflater.from(act).inflate(R.layout.settings, null);
        bindTheme();
        bindFont();
        bindLineHeight();
        bindParagraph();
        bindMargin();
        bindFontImport();
        bindBehavior();
        bindAnimation();
        bindNetwork();
        bindData();
        bindAbout();
    }

    public View getView() { return root; }

    private void bindTheme() {
        RadioGroup rg = (RadioGroup) root.findViewById(R.id.rgTheme);
        String cur = Settings.getTheme(act);
        if (cur.equals(Settings.THEME_DAY)) ((RadioButton) root.findViewById(R.id.rbDay)).setChecked(true);
        else if (cur.equals(Settings.THEME_PAPER)) ((RadioButton) root.findViewById(R.id.rbPaper)).setChecked(true);
        else if (cur.equals(Settings.THEME_SEPIA)) ((RadioButton) root.findViewById(R.id.rbSepia)).setChecked(true);
        else if (cur.equals(Settings.THEME_NIGHT)) ((RadioButton) root.findViewById(R.id.rbNight)).setChecked(true);
        else if (cur.equals(Settings.THEME_DARK)) ((RadioButton) root.findViewById(R.id.rbDark)).setChecked(true);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String t = Settings.THEME_DAY;
                if (checkedId == R.id.rbDay) t = Settings.THEME_DAY;
                else if (checkedId == R.id.rbPaper) t = Settings.THEME_PAPER;
                else if (checkedId == R.id.rbSepia) t = Settings.THEME_SEPIA;
                else if (checkedId == R.id.rbNight) t = Settings.THEME_NIGHT;
                else if (checkedId == R.id.rbDark) t = Settings.THEME_DARK;
                Settings.setTheme(act, t);
            }
        });
    }

    private void bindFont() {
        SeekBar sb = (SeekBar) root.findViewById(R.id.sbFont);
        sb.setProgress(Settings.getFontSize(act));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) Settings.setFontSize(act, p);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void bindLineHeight() {
        SeekBar sb = (SeekBar) root.findViewById(R.id.sbLine);
        sb.setProgress((int)(Settings.getLineHeight(act) * 10));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) Settings.setLineHeight(act, p / 10.0f);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void bindParagraph() {
        SeekBar sb = (SeekBar) root.findViewById(R.id.sbPara);
        sb.setProgress(Settings.getParagraphSpacing(act));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) Settings.setParagraphSpacing(act, p);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void bindMargin() {
        SeekBar sb = (SeekBar) root.findViewById(R.id.sbMargin);
        sb.setProgress(Settings.getMargin(act));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) Settings.setMargin(act, p);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void bindFontImport() {
        Button btn = (Button) root.findViewById(R.id.btnFont);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                String[] mimes = {"font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"};
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
                act.startActivityForResult(intent, 2001);
            }
        });
    }

    public void onFontPicked(android.content.Intent data) {
        if (data == null) return;
        android.net.Uri uri = data.getData();
        if (uri == null) return;
        try {
            String name = uri.getLastPathSegment();
            if (name == null) name = "font.ttf";
            java.io.File out = new java.io.File(act.getCacheDir(), "fonts/" + name);
            out.getParentFile().mkdirs();
            java.io.InputStream is = act.getContentResolver().openInputStream(uri);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            is.close();
            Settings.setFontFamily(act, name);
            Toast.makeText(act, "✅ 已导入字体: " + name, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(act, "❌ 导入失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void bindBehavior() {
        Switch sw;
        sw = (Switch) root.findViewById(R.id.swKeep); sw.setChecked(Settings.getKeepScreenOn(act));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { Settings.setKeepScreenOn(act, c); }
        });
        sw = (Switch) root.findViewById(R.id.swFull); sw.setChecked(Settings.getFullscreen(act));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { Settings.setFullscreen(act, c); }
        });
        sw = (Switch) root.findViewById(R.id.swVol); sw.setChecked(Settings.getVolumeKeyFlip(act));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { Settings.setVolumeKeyFlip(act, c); }
        });
        sw = (Switch) root.findViewById(R.id.swTap); sw.setChecked(Settings.getTapZoneFlip(act));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { Settings.setTapZoneFlip(act, c); }
        });
    }

    private void bindAnimation() {
        RadioGroup rg = (RadioGroup) root.findViewById(R.id.rgAnim);
        int cur = Settings.getAnimationType(act);
        if (cur == 0) ((RadioButton) root.findViewById(R.id.rbAnimNone)).setChecked(true);
        else if (cur == 1) ((RadioButton) root.findViewById(R.id.rbAnimSlide)).setChecked(true);
        else if (cur == 2) ((RadioButton) root.findViewById(R.id.rbAnimSim)).setChecked(true);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int v = 0;
                if (checkedId == R.id.rbAnimNone) v = 0;
                else if (checkedId == R.id.rbAnimSlide) v = 1;
                else if (checkedId == R.id.rbAnimSim) v = 2;
                Settings.setAnimationType(act, v);
            }
        });
    }

    private void bindNetwork() {
        Button btn = (Button) root.findViewById(R.id.btnOpds);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                act.startActivity(new Intent(act, OpdsActivity.class));
            }
        });
    }

    private void bindData() {
        Button exp = (Button) root.findViewById(R.id.btnExport);
        exp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { doExport(); }
        });
        Button imp = (Button) root.findViewById(R.id.btnImportData);
        imp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { doImport(); }
        });
        Button clr = (Button) root.findViewById(R.id.btnClear);
        clr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(act)
                    .setTitle("清空书架?")
                    .setMessage("将删除所有书籍记录/书签/笔记/进度(EPUB 文件保留)。此操作不可恢复!")
                    .setPositiveButton("清空", new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            BookRepository.get(act).clearAll();
                            Toast.makeText(act, "已清空", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        });
    }

    private void doExport() {
        String json = BookRepository.get(act).exportToJson();
        android.content.ClipboardManager cb = (android.content.ClipboardManager) act.getSystemService(act.CLIPBOARD_SERVICE);
        cb.setText(json);
        try {
            java.io.File f = new java.io.File(act.getExternalFilesDir(null), "iceReading-export.json");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(json.getBytes("UTF-8"));
            fos.close();
            Toast.makeText(act, "✅ 已导出 " + json.length() + " 字符\n" + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(act, "✅ 已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    private void doImport() {
        android.content.ClipboardManager cb = (android.content.ClipboardManager) act.getSystemService(act.CLIPBOARD_SERVICE);
        android.content.ClipData data = cb.getPrimaryClip();
        if (data == null) { Toast.makeText(act, "剪贴板为空", Toast.LENGTH_SHORT).show(); return; }
        String txt = data.getItemAt(0).coerceToText(act).toString();
        int n = BookRepository.get(act).importFromJson(txt);
        Toast.makeText(act, "✅ 导入 " + n + " 本书", Toast.LENGTH_SHORT).show();
    }

    private void bindAbout() {
        Button btn = (Button) root.findViewById(R.id.btnAbout);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(act)
                    .setTitle("冰读 iceReading v1.0")
                    .setMessage(act.getString(R.string.settings_about_content))
                    .setPositiveButton("OK", null).show();
            }
        });
        Button help = (Button) root.findViewById(R.id.btnHelp);
        help.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(act)
                    .setTitle("使用帮助")
                    .setMessage("• 书架:点 📂 导入本地 EPUB\n• 发现:内置 6 个 OPDS 源(古登堡/Standard Ebooks/忠实书屋等)\n• 阅读:左右翻页/点屏翻页/音量键翻页,长按选词高亮\n• 主题:5 套(深/浅/护眼/羊皮/深邃),可在设置切换\n• 字体:支持自定义 .ttf/.otf\n• 进度:自动保存,下次打开续读\n• 书签/高亮:长按 AI 消息可加书签,选词后可高亮\n• 数据:导出/导入 JSON,跨设备迁移")
                    .setPositiveButton("知道了", null).show();
            }
        });
    }
}
