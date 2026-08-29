package com.icereading.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 角色/Persona - 简单阅读偏好(为未来扩展预留)
 * 当前实现:
 *  - 字体大小/主题(已在 Settings)
 *  - 翻译/朗读/AI 摘要(为 v2.0 预留)
 */
public class PersonaActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("🎭 阅读偏好\n\n此功能在 v1.0 中已集成到「设置 → 阅读」\n\nv2.0 计划:\n• AI 摘要(联网)\n• 翻译成多语言\n• TTS 朗读\n• 人物词典\n• 阅读习惯自适应");
        tv.setPadding(40, 60, 40, 40);
        tv.setTextSize(14);
        tv.setTextColor(0xFFE8E8E8);
        tv.setBackgroundColor(0xFF0F1419);
        setContentView(tv);
    }
}
