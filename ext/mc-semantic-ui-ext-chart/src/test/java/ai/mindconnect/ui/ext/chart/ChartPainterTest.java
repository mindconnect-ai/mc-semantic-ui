package ai.mindconnect.ui.ext.chart;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The painter's contract. The parity with the TypeScript twin is checked by a
 * separate script that runs both over the same fixtures; these tests pin the
 * things that are easy to break by hand.
 */
class ChartPainterTest {

    private static UiChart chart(UiChart.ChartType type) {
        var c = new UiChart();
        c.setId("sales");
        c.setChartType(type);
        var data = new UiChart.ChartData();
        data.setLabels(List.of("Q1", "Q2", "Q3"));
        var s = new UiChart.ChartData.Series();
        s.setName("Revenue");
        s.setValues(List.of(12, 30, 18));
        data.setSeries(List.of(s));
        c.setData(data);
        return c;
    }

    @Test
    void barChartDrawsOneRectPerValueWithATooltip() {
        String svg = ChartPainter.svg(chart(UiChart.ChartType.BAR));

        assertEquals(3, svg.split("<rect", -1).length - 1, svg);
        // No JavaScript anywhere — the tooltip is a native <title>.
        assertTrue(svg.contains("<title>Q2: 30</title>"), svg);
        assertFalse(svg.contains("script"), svg);
        assertTrue(svg.contains("aria-label=\"bar chart\""), svg);
    }

    @Test
    void lineAndAreaShareThePolylineButOnlyAreaFills() {
        String line = ChartPainter.svg(chart(UiChart.ChartType.LINE));
        String area = ChartPainter.svg(chart(UiChart.ChartType.AREA));

        assertTrue(line.contains("<polyline"), line);
        assertFalse(line.contains("<polygon"), line);
        assertTrue(area.contains("<polyline"), area);
        assertTrue(area.contains("<polygon"), area);
    }

    @Test
    void pieAndDonutDifferOnlyInStrokeWidth() {
        String pie = ChartPainter.svg(chart(UiChart.ChartType.PIE));
        String donut = ChartPainter.svg(chart(UiChart.ChartType.DONUT));

        assertTrue(pie.contains("stroke-width=\"84\""), pie);
        assertTrue(donut.contains("stroke-width=\"20\""), donut);
        // Both carry a legend entry per label.
        assertEquals(3, donut.split("<li>", -1).length - 1, donut);
    }

    @Test
    void wholeNumbersPrintWithoutADecimalPoint() {
        // JS prints 30, Java's default would print 30.0 — the tooltips have to
        // read the same in both render modes.
        String svg = ChartPainter.svg(chart(UiChart.ChartType.BAR));
        assertTrue(svg.contains(": 30<"), svg);
        assertFalse(svg.contains(": 30.0<"), svg);
    }

    @Test
    void noDataRendersAnEmptyStateRatherThanABrokenAxis() {
        var c = new UiChart();
        c.setId("empty");
        c.setChartType(UiChart.ChartType.BAR);
        assertTrue(ChartPainter.svg(c).contains("sui-chart-empty"));
    }

    @Test
    void labelsAreEscaped() {
        var c = chart(UiChart.ChartType.BAR);
        c.getData().setLabels(List.of("<script>", "Q2", "Q3"));
        assertTrue(ChartPainter.svg(c).contains("&lt;script&gt;"));
    }
}
