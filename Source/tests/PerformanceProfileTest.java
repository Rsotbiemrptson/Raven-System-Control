package dev.odin.controls;

import java.util.Arrays;

public final class PerformanceProfileTest {
    static final long[][] STEPS = {
        {384000, 556800, 748800, 960000, 1152000, 1363200, 1555200, 1785600, 1996800, 2227200, 2400000,
            2745600, 2918400, 3072000, 3321600, 3532800},
        {1017600, 1209600, 1401600, 1689600, 1958400, 2246400, 2438400, 2649600, 2841600, 3072000, 3283200,
            3513600, 3801600, 4089600, 4204800, 4320000},
        {160000000, 222000000, 342000000, 389000000, 443000000, 525000000, 607000000, 660000000, 734000000,
            832000000, 900000000, 967000000, 1050000000, 1100000000}};
    static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
    public static void main(String[] args) {
        long[] low = {1996800, 2246400, 660000000}, high = {3532800, 4320000, 1100000000};
        PerformanceProfile stock = new PerformanceProfile(0, low, high);
        PerformanceProfile boosted = new PerformanceProfile(0, new long[] {2227200, 3283200, 660000000}, high);
        check(boosted.effectiveMatches(stock), "Independent CPU boost was rejected");
        check(!stock.same(boosted), "Boosted readback changed draft equality");
        check(!new PerformanceProfile(0, new long[] {384000, 2246400, 660000000}, high)
                   .effectiveMatches(stock),
            "An unapplied requested minimum was accepted");
        check(!new PerformanceProfile(0, low, new long[] {3321600, 4320000, 1100000000})
                   .effectiveMatches(stock),
            "A changed CPU ceiling was accepted");
        check(!new PerformanceProfile(0, new long[] {1996800, 2246400, 900000000}, high)
                   .effectiveMatches(stock),
            "A changed GPU floor was accepted");
        long[][] expected = {
            {3072000, 3801600, 967000000}, {2745600, 3513600, 900000000}, {2400000, 3072000, 734000000}};
        double[] reduction = {.10, .175, .25};
        for (int r = 0; r < 3; r++) {
            PerformanceProfile preset = stock.underclock(STEPS, reduction[r], 0);
            check(Arrays.equals(preset.maximum, expected[r]), "Wrong supported preset steps");
            for (int component = 0; component < 3; component++) {
                check(preset.maximum[component] <= high[component] * (1 - reduction[r]),
                    "Preset exceeds its target");
                check(
                    preset.minimum[component] == STEPS[component][0], "Underclock retained a boosted floor");
            }
        }
        for (int target = 1; target <= 4; target++) {
            PerformanceProfile preset = stock.underclock(STEPS, .25, target);
            for (int i = 0; i < 3; i++) {
                boolean selected = (target == 1 && i < 2) || (target == 2 && i == 2)
                    || (target == 3 && i == 0) || (target == 4 && i == 1);
                check(preset.maximum[i] == (selected ? expected[2][i] : high[i]),
                    "Preset changed an unselected component");
                check(preset.minimum[i] == (selected ? STEPS[i][0] : low[i]),
                    "Preset changed an unselected floor");
            }
        }
        check(Arrays.equals(stock.minimum, low) && Arrays.equals(stock.maximum, high), "Stock was mutated");
        PerformanceProfile copy = stock.copy();
        copy.minimum[0] = 384000;
        check(stock.minimum[0] == 1996800, "Draft shares stock state");
        check(PerformanceProfile.reserved("NORMAL") && PerformanceProfile.reserved("High performance"),
            "Stock names can be overwritten");
        try {
            new PerformanceProfile(
                0, new long[] {3532800, 2246400, 660000000}, new long[] {384000, 4320000, 1100000000})
                .validate(STEPS);
            throw new AssertionError("Inverted range accepted");
        } catch (IllegalArgumentException expectedError) {
        }
        try {
            new PerformanceProfile(0, low, new long[] {3532801, 4320000, 1100000000}).validate(STEPS);
            throw new AssertionError("Unsupported frequency accepted");
        } catch (IllegalArgumentException expectedError) {
        }
        System.out.println(
            "PASS performance presets, component targeting, protected stock copies and frequency validation");
    }
}
