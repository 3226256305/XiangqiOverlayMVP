package com.local.xiangqioverlay;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPrefs {
    private static final String NAME = "xiangqi_overlay";
    private AppPrefs() {}

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String side(Context c) {
        return get(c).getString("side", "w");
    }

    public static void setSide(Context c, String side) {
        get(c).edit().putString("side", side).apply();
    }

    public static boolean blackBottom(Context c) {
        return get(c).getBoolean("black_bottom", false);
    }

    public static long templateVersion(Context c) {
        return get(c).getLong("template_version", 0L);
    }
}
