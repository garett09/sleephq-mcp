package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;
import java.util.TreeMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OscarHistogramTest {

    @Test
    void computesMedianFromSymmetricHistogram() {
        TreeMap<Integer, Long> hist = new TreeMap<>();
        for (int i = 1; i <= 5; i++) hist.put(i, 1L);
        assertThat(OscarHistogram.percentile(hist, 1.0, 50.0)).isCloseTo(3.0, within(0.01));
    }

    @Test
    void computesP95WithGain() {
        TreeMap<Integer, Long> hist = new TreeMap<>();
        hist.put(80, 100L);
        assertThat(OscarHistogram.percentile(hist, 0.2, 95.0)).isCloseTo(16.0, within(0.01));
    }

    @Test
    void mergesCombinesCountsCorrectly() {
        TreeMap<Integer, Long> a = new TreeMap<>(java.util.Map.of(10, 3L, 20, 2L));
        TreeMap<Integer, Long> b = new TreeMap<>(java.util.Map.of(10, 1L, 30, 5L));
        TreeMap<Integer, Long> merged = OscarHistogram.merge(a, b);
        assertThat(merged.get(10)).isEqualTo(4L);
        assertThat(merged.get(20)).isEqualTo(2L);
        assertThat(merged.get(30)).isEqualTo(5L);
    }

    @Test
    void p995FromSkewedHistogram() {
        TreeMap<Integer, Long> hist = new TreeMap<>();
        hist.put(10, 98L);
        hist.put(100, 2L);
        double p995 = OscarHistogram.percentile(hist, 1.0, 99.5);
        assertThat(p995).isGreaterThanOrEqualTo(100.0);
    }

    @Test
    void emptyHistogramReturnsNaN() {
        assertThat(OscarHistogram.percentile(new TreeMap<>(), 1.0, 95.0)).isNaN();
    }

    @Test
    void computesAvgWithGain() {
        TreeMap<Integer, Long> hist = new TreeMap<>();
        hist.put(80, 3L);
        hist.put(100, 2L);
        // avg = (80*3 + 100*2)/5 * 0.2 = 88 * 0.2 = 17.6
        assertThat(OscarHistogram.avg(hist, 0.2)).isCloseTo(17.6, within(0.01));
    }
}
