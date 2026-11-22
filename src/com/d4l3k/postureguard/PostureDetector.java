package com.d4l3k.postureguard;

/**
 * Turns raw accelerometer samples into a tilt angle and a "held in hand" flag.
 *
 * Tilt is the angle between the screen and the horizontal: 0° flat face-up on
 * a table (looking straight down), 90° upright at eye level, above 90° tipped
 * back overhead. It uses only the screen normal, so portrait and landscape are
 * treated the same.
 *
 * A phone in a hand never stays still: physiological tremor adds a small,
 * constant wobble on top of gravity. A phone resting on a table or stand reads
 * as near-perfectly still. The detector tracks the RMS of the non-gravity part
 * of the signal and calls the phone held while it stays above a small floor.
 */
public final class PostureDetector {
    /** Low-pass time constant separating gravity from motion. */
    private static final double GRAVITY_TAU_S = 0.4;
    /** Smoothing time constant of the motion RMS. */
    private static final double JITTER_TAU_S = 0.8;
    /** Motion RMS (m/s²) above which the phone counts as in a hand. */
    public static final double HELD_RMS = 0.045;
    /** How long the phone stays "held" after motion drops below the floor. */
    private static final long HELD_HOLD_NS = 1_500_000_000L;

    private double gx, gy, gz;
    private double jitterSq;
    private long lastNs = -1;
    private long heldUntilNs = -1;
    private int samples;

    public void reset() {
        lastNs = -1;
        heldUntilNs = -1;
        samples = 0;
        jitterSq = 0;
    }

    public void add(float x, float y, float z, long timestampNs) {
        if (lastNs < 0) {
            gx = x;
            gy = y;
            gz = z;
            lastNs = timestampNs;
            return;
        }
        double dt = Math.max(0, Math.min(0.5, (timestampNs - lastNs) / 1e9));
        lastNs = timestampNs;
        double a = dt / (GRAVITY_TAU_S + dt);
        gx += a * (x - gx);
        gy += a * (y - gy);
        gz += a * (z - gz);
        double lx = x - gx, ly = y - gy, lz = z - gz;
        double b = dt / (JITTER_TAU_S + dt);
        jitterSq += b * ((lx * lx + ly * ly + lz * lz) - jitterSq);
        samples++;
        if (samples > 10 && jitter() > HELD_RMS) {
            heldUntilNs = timestampNs + HELD_HOLD_NS;
        }
    }

    /** True once enough samples have arrived for the outputs to mean anything. */
    public boolean ready() {
        return samples > 10;
    }

    public double tiltDegrees() {
        double g = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (g < 1e-3) return 90;
        double c = Math.max(-1, Math.min(1, gz / g));
        return Math.toDegrees(Math.acos(c));
    }

    public double jitter() {
        return Math.sqrt(jitterSq);
    }

    public boolean held() {
        return ready() && lastNs >= 0 && lastNs <= heldUntilNs;
    }
}
