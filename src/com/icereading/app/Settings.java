package com.icereading.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用设置(主题/字体/大小/翻页动画/...)
 */
public class Settings {

    public static final String P = "icereading_settings";

    // 主题
    public static final String THEME_DAY = "day";
    public static final String THEME_PAPER = "paper";
    public static final String THEME_SEPIA = "sepia";
    public static final String THEME_NIGHT = "night";
    public static final String THEME_DARK = "dark";

    // 字体大小(sp)
    public static final int FONT_MIN = 12;
    public static final int FONT_MAX = 32;
    public static final int FONT_DEFAULT = 18;

    // 行距
    public static final float LINE_HEIGHT_MIN = 1.0f;
    public static final float LINE_HEIGHT_MAX = 2.5f;
    public static final float LINE_HEIGHT_DEFAULT = 1.5f;

    // 段距
    public static final int PARAGRAPH_MIN = 0;
    public static final int PARAGRAPH_MAX = 32;
    public static final int PARAGRAPH_DEFAULT = 8;

    // 页边距
    public static final int MARGIN_MIN = 0;
    public static final int MARGIN_MAX = 64;
    public static final int MARGIN_DEFAULT = 16;

    public static String getTheme(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getString("theme", THEME_DAY);
    }
    public static void setTheme(Context ctx, String t) {
        ctx.getSharedPreferences(P, 0).edit().putString("theme", t).apply();
    }

    public static int getFontSize(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getInt("fontSize", FONT_DEFAULT);
    }
    public static void setFontSize(Context ctx, int s) {
        if (s < FONT_MIN) s = FONT_MIN;
        if (s > FONT_MAX) s = FONT_MAX;
        ctx.getSharedPreferences(P, 0).edit().putInt("fontSize", s).apply();
    }

    public static float getLineHeight(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getFloat("lineHeight", LINE_HEIGHT_DEFAULT);
    }
    public static void setLineHeight(Context ctx, float v) {
        if (v < LINE_HEIGHT_MIN) v = LINE_HEIGHT_MIN;
        if (v > LINE_HEIGHT_MAX) v = LINE_HEIGHT_MAX;
        ctx.getSharedPreferences(P, 0).edit().putFloat("lineHeight", v).apply();
    }

    public static int getParagraphSpacing(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getInt("paragraphSpacing", PARAGRAPH_DEFAULT);
    }
    public static void setParagraphSpacing(Context ctx, int v) {
        if (v < PARAGRAPH_MIN) v = PARAGRAPH_MIN;
        if (v > PARAGRAPH_MAX) v = PARAGRAPH_MAX;
        ctx.getSharedPreferences(P, 0).edit().putInt("paragraphSpacing", v).apply();
    }

    public static int getMargin(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getInt("margin", MARGIN_DEFAULT);
    }
    public static void setMargin(Context ctx, int v) {
        if (v < MARGIN_MIN) v = MARGIN_MIN;
        if (v > MARGIN_MAX) v = MARGIN_MAX;
        ctx.getSharedPreferences(P, 0).edit().putInt("margin", v).apply();
    }

    /**
     * 字体(用户导入的字体文件名,存 cacheDir/fonts/)
     */
    public static String getFontFamily(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getString("fontFamily", "");
    }
    public static void setFontFamily(Context ctx, String font) {
        ctx.getSharedPreferences(P, 0).edit().putString("fontFamily", font).apply();
    }

    public static boolean getKeepScreenOn(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getBoolean("keepScreenOn", true);
    }
    public static void setKeepScreenOn(Context ctx, boolean v) {
        ctx.getSharedPreferences(P, 0).edit().putBoolean("keepScreenOn", v).apply();
    }

    public static boolean getFullscreen(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getBoolean("fullscreen", true);
    }
    public static void setFullscreen(Context ctx, boolean v) {
        ctx.getSharedPreferences(P, 0).edit().putBoolean("fullscreen", v).apply();
    }

    public static boolean getVolumeKeyFlip(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getBoolean("volumeKey", true);
    }
    public static void setVolumeKeyFlip(Context ctx, boolean v) {
        ctx.getSharedPreferences(P, 0).edit().putBoolean("volumeKey", v).apply();
    }

    public static boolean getTapZoneFlip(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getBoolean("tapZone", true);
    }
    public static void setTapZoneFlip(Context ctx, boolean v) {
        ctx.getSharedPreferences(P, 0).edit().putBoolean("tapZone", v).apply();
    }

    public static int getAnimationType(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getInt("anim", 0);  // 0=无,1=滑动,2=仿真
    }
    public static void setAnimationType(Context ctx, int v) {
        ctx.getSharedPreferences(P, 0).edit().putInt("anim", v).apply();
    }

    public static int getBrightness(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getInt("brightness", -1);  // -1 = 跟随系统
    }
    public static void setBrightness(Context ctx, int v) {
        ctx.getSharedPreferences(P, 0).edit().putInt("brightness", v).apply();
    }

    public static String getDefaultOpdsUrls(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getString("defaultOpds", "gutenberg,standardebooks");
    }
    public static void setDefaultOpdsUrls(Context ctx, String v) {
        ctx.getSharedPreferences(P, 0).edit().putString("defaultOpds", v).apply();
    }

    public static String getSortBy(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getString("sortBy", "lastRead");
    }
    public static void setSortBy(Context ctx, String v) {
        ctx.getSharedPreferences(P, 0).edit().putString("sortBy", v).apply();
    }

    public static String getFilterBy(Context ctx) {
        return ctx.getSharedPreferences(P, 0).getString("filterBy", "all");
    }
    public static void setFilterBy(Context ctx, String v) {
        ctx.getSharedPreferences(P, 0).edit().putString("filterBy", v).apply();
    }
}
