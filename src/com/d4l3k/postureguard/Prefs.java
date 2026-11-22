package com.d4l3k.postureguard;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String ENABLED = "enabled";
    static final String THRESHOLD = "threshold";
    static final String DELAY = "delay";
    static final int DEFAULT_THRESHOLD = 55;
    static final int DEFAULT_DELAY = 5;
    /** Motion threshold for "in hand", in mm/s² (0.001 m/s²). */
    static final String HELD_MILLI = "held_milli";
    static final int DEFAULT_HELD_MILLI = (int) Math.round(PostureDetector.DEFAULT_HELD_RMS * 1000);
    static final int MIN_HELD_MILLI = 10;
    static final int MAX_HELD_MILLI = 500;

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("posture", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return get(c).getBoolean(ENABLED, false);
    }

    static int threshold(Context c) {
        return get(c).getInt(THRESHOLD, DEFAULT_THRESHOLD);
    }

    static double heldRms(Context c) {
        return get(c).getInt(HELD_MILLI, DEFAULT_HELD_MILLI) / 1000.0;
    }

    static int delay(Context c) {
        return get(c).getInt(DELAY, DEFAULT_DELAY);
    }
}
