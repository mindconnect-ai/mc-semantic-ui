package ai.mindconnect.ui.ext.chart;

import ai.mindconnect.ui.model.UiNode;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@com.fasterxml.jackson.annotation.JsonTypeName("chart")
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiChart extends UiNode {

    public enum ChartType { LINE, BAR, PIE, DONUT, AREA }

    @Data
    public static class ChartData {
        private List<String> labels;
        private List<Series> series;

        @Data
        public static class Series {
            private String name;
            private List<Number> values;
        }
    }

    /**
     * How values are written in tooltips, legends and on the value axis: a
     * prefix ({@code "$"}), a suffix ({@code " CHF"}) and a fixed number of
     * decimals. Without decimals a whole number gets none, one of at least 1
     * two, and a smaller one up to four. The axis keeps the prefix and drops
     * the suffix — the title names the unit once.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValueFormat {
        private String prefix;
        private String suffix;
        private Integer decimals;

        public static ValueFormat of(String prefix, String suffix, Integer decimals) {
            var f = new ValueFormat();
            f.prefix = prefix; f.suffix = suffix; f.decimals = decimals;
            return f;
        }
    }

    private ChartType chartType;
    private ChartData data;
    /** A bar chart with several series stacks them — one bar per label, the total on top. */
    private Boolean stacked;
    /** The dashed line down the hovered column of a bar, line or area chart; on unless false. */
    private Boolean crosshair;
    private ValueFormat valueFormat;

    public UiChart stacked(boolean stacked) {
        this.stacked = stacked;
        return this;
    }

    public UiChart crosshair(boolean crosshair) {
        this.crosshair = crosshair;
        return this;
    }

    public UiChart valueFormat(String prefix, String suffix, Integer decimals) {
        this.valueFormat = ValueFormat.of(prefix, suffix, decimals);
        return this;
    }

    public static UiChart of(String id, String title, ChartType type, ChartData data) {
        var c = new UiChart();
        c.setId(id); c.setTitle(title);
        c.chartType = type; c.data = data;
        return c;
    }
}
