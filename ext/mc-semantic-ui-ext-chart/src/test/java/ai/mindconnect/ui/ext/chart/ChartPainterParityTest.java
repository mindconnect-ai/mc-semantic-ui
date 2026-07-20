package ai.mindconnect.ui.ext.chart;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the specific formatting rules that make the Java painter's output
 * byte-identical to the TypeScript one. They look pedantic in isolation; each
 * of them is a divergence that actually happened while the two were written.
 *
 * <p>The full comparison is done by rendering every chart type through both
 * painters and diffing — see the module README. These tests pin the rules so a
 * future edit fails here rather than silently drifting apart.
 */
class ChartPainterParityTest {

    private static UiChart chart(UiChart.ChartType type, List<Number> values) {
        var c = new UiChart();
        c.setId("c");
        c.setChartType(type);
        var data = new UiChart.ChartData();
        data.setLabels(List.of("A", "B"));
        var s = new UiChart.ChartData.Series();
        s.setValues(values);
        data.setSeries(List.of(s));
        c.setData(data);
        return c;
    }

    @Test
    void negativeZeroIsNeverPrinted() {
        // The first pie segment starts at offset -0.0. Java prints "-0.00",
        // JavaScript prints "0.00" — same drawing, different string.
        String svg = ChartPainter.svg(chart(UiChart.ChartType.PIE, List.of(1, 1)));
        assertFalse(svg.contains("-0.00"), svg);
        assertTrue(svg.contains("stroke-dashoffset=\"0.00\""), svg);
    }

    @Test
    void wholeNumbersHaveNoTrailingDecimal() {
        // Java's default would render 30.0 in the tooltip where JS renders 30.
        String svg = ChartPainter.svg(chart(UiChart.ChartType.BAR, List.of(30, 12)));
        assertTrue(svg.contains(">A: 30<"), svg);
        assertFalse(svg.contains(">A: 30.0<"), svg);
    }

    @Test
    void coordinatesUseOneDecimalAndArcsTwo() {
        // toFixed(1) for geometry, toFixed(2) for arc lengths — mirrored exactly.
        String bar = ChartPainter.svg(chart(UiChart.ChartType.BAR, List.of(3, 7)));
        assertTrue(bar.matches("(?s).*x=\"\\d+\\.\\d\".*"), bar);

        String pie = ChartPainter.svg(chart(UiChart.ChartType.PIE, List.of(3, 7)));
        assertTrue(pie.matches("(?s).*stroke-dasharray=\"\\d+\\.\\d{2} \\d+\\.\\d{2}\".*"), pie);
    }

    @Test
    void outputDoesNotDependOnTheSystemLocale() {
        // A German default locale formats 12.5 as "12,5", which would both
        // break the SVG and diverge from the browser. Comparing the two
        // renderings is the honest check — asserting "no commas" would be
        // wrong, since SVG separates polyline coordinates with commas.
        var previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.US);
            String us = ChartPainter.svg(chart(UiChart.ChartType.AREA, List.of(3, 7)));
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            String de = ChartPainter.svg(chart(UiChart.ChartType.AREA, List.of(3, 7)));
            org.junit.jupiter.api.Assertions.assertEquals(us, de);
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
