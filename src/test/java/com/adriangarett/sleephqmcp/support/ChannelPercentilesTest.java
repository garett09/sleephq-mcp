package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelPercentilesTest {

    @Test
    void percentile_ceilRank_matchesExpectedIndices() {
        List<Double> sorted = ChannelPercentiles.sortedClean(rangeOneTo(100));
        assertThat(ChannelPercentiles.percentile(sorted, 50)).isEqualTo(50.0);
        assertThat(ChannelPercentiles.percentile(sorted, 95)).isEqualTo(95.0);
        assertThat(ChannelPercentiles.percentile(sorted, 99)).isEqualTo(99.0);
    }

    @Test
    void percentile_emptyList_returnsZero() {
        assertThat(ChannelPercentiles.percentile(List.of(), 95)).isEqualTo(0.0);
    }

    @Test
    void percentile_fractionalPct_p99_5_ceilRank() {
        // ceil(99.5/100.0 * 200) - 1 = ceil(199.0) - 1 = 198 → value 199.0
        List<Double> sorted = ChannelPercentiles.sortedClean(rangeOneTo(200));
        assertThat(ChannelPercentiles.percentile(sorted, 99.5)).isEqualTo(199.0);
    }

    @Test
    void percentile_fractional_emptyList_returnsZero() {
        assertThat(ChannelPercentiles.percentile(List.of(), 99.5)).isEqualTo(0.0);
    }

    @Test
    void sortedClean_dropsNaN_andSortsAscending() {
        List<Double> cleaned = ChannelPercentiles.sortedClean(List.of(3.0, Double.NaN, 1.0, 2.0));
        assertThat(cleaned).containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    void avg_ignoresEmpty_andAveragesValues() {
        assertThat(ChannelPercentiles.avg(List.of())).isEqualTo(0.0);
        assertThat(ChannelPercentiles.avg(List.of(2.0, 4.0, 6.0))).isEqualTo(4.0);
    }

    private static List<Double> rangeOneTo(int n) {
        List<Double> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            out.add((double) i);
        }
        return out;
    }
}
