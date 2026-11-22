package com.d4l3k.postureguard;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String ENABLED = "enabled";
    static final String THRESHOLD = "threshold";
    static final String DELAY = "delay";
    static final int DEFAULT_THRESHOLD = 55;
    static final int DEFAULT_DELAY = 5;

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("posture", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return get(c).getBoolean(ENABLED, false);
    }

    static int threshold(Context c) {
        return get(c).getInt(THRESHOLD, DEFAULT_THRESHOLD);
    }

    static int delay(Context c) {
        return get(c).getInt(DELAY, DEFAULT_DELAY);
    }
}
