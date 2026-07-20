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

    private ChartType chartType;
    private ChartData data;

    public static UiChart of(String id, String title, ChartType type, ChartData data) {
        var c = new UiChart();
        c.setId(id); c.setTitle(title);
        c.chartType = type; c.data = data;
        return c;
    }
}
