package dev.odin.controls;

import java.util.Arrays;

/** Frequency values use kHz for CPUs and Hz for the GPU. */
final class PerformanceProfile {
    static final String[] STOCK_NAMES = {"Normal", "Performance", "High Performance"};
    static final String[] COMPONENTS = {"CPU cores 0–5", "CPU cores 6–7", "GPU"};
    final int mode;
    final long[] minimum, maximum;

    PerformanceProfile(int mode, long[] minimum, long[] maximum) {
        if (mode < 0 || mode > 2 || minimum.length != 3 || maximum.length != 3)
            throw new IllegalArgumentException("Invalid performance profile");
        this.mode = mode;
        this.minimum = minimum.clone();
        this.maximum = maximum.clone();
    }
    PerformanceProfile copy() {
        return new PerformanceProfile(mode, minimum, maximum);
    }
    boolean same(PerformanceProfile other) {
        return mode == other.mode && Arrays.equals(minimum, other.minimum)
            && Arrays.equals(maximum, other.maximum);
    }
    boolean effectiveMatches(PerformanceProfile requested) {
        if (mode != requested.mode || !Arrays.equals(maximum, requested.maximum)
            || minimum[2] != requested.minimum[2])
            return false;
        for (int i = 0; i < 2; i++) {
            if (minimum[i] < requested.minimum[i] || minimum[i] > requested.maximum[i])
                return false;
        }
        return true;
    }
    void validate(long[][] frequencies) {
        for (int i = 0; i < 3; i++) {
            if (Arrays.binarySearch(frequencies[i], minimum[i]) < 0
                || Arrays.binarySearch(frequencies[i], maximum[i]) < 0)
                throw new IllegalArgumentException("A saved frequency is unavailable on this device");
            if (minimum[i] > maximum[i])
                throw new IllegalArgumentException("Minimum must be at or below maximum");
        }
    }
    static long floor(long[] steps, double target) {
        long value = steps[0];
        for (long step : steps) {
            if (step > target)
                break;
            value = step;
        }
        return value;
    }
    PerformanceProfile underclock(long[][] frequencies, double reduction, int target) {
        if (reduction <= 0 || reduction >= 1 || target < 0 || target > 4)
            throw new IllegalArgumentException("Invalid underclock preset");
        PerformanceProfile result = copy();
        for (int i = 0; i < 3; i++) {
            boolean selected = target == 0 || (target == 1 && i < 2) || (target == 2 && i == 2)
                || (target == 3 && i == 0) || (target == 4 && i == 1);
            if (selected) {
                result.minimum[i] = frequencies[i][0];
                result.maximum[i] =
                    floor(frequencies[i], frequencies[i][frequencies[i].length - 1] * (1 - reduction));
            }
        }
        result.validate(frequencies);
        return result;
    }
    static boolean reserved(String name) {
        for (String stock : STOCK_NAMES)
            if (stock.equalsIgnoreCase(name))
                return true;
        return false;
    }
}
