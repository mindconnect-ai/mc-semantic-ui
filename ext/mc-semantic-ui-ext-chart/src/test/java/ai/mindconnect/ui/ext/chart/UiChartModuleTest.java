package ai.mindconnect.ui.ext.chart;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiStack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chart node lives here, not in the core — so it has to teach Jackson about
 * itself. These tests prove both halves of that: the type is discoverable
 * without any manual wiring, and it round-trips inside an ordinary core tree.
 */
class UiChartModuleTest {

    private static UiChart sample() {
        var c = new UiChart();
        c.setId("revenue");
        c.setChartType(UiChart.ChartType.BAR);
        return c;
    }

    @Test
    void serviceLoaderIsEnoughToRegisterTheSubtype() {
        // No Spring, no explicit module registration — just what a plain-Java
        // consumer gets from findAndRegisterModules().
        var mapper = new ObjectMapper().findAndRegisterModules();

        String json = assertDoesNotThrowJson(() -> mapper.writeValueAsString(sample()));
        assertTrue(json.contains("\"type\":\"chart\""), json);

        UiNode back = assertDoesNotThrowNode(() -> mapper.readValue(json, UiNode.class));
        assertInstanceOf(UiChart.class, back);
        assertEquals("revenue", back.getId());
    }

    @Test
    void aChartNestsInsideAnOrdinaryCoreTree() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var stack = UiStack.of(sample());
        stack.setId("wrap");

        String json = assertDoesNotThrowJson(() -> mapper.writeValueAsString(stack));
        UiNode back = assertDoesNotThrowNode(() -> mapper.readValue(json, UiNode.class));

        var children = ((UiStack) back).getChildren();
        assertEquals(1, children.size());
        assertInstanceOf(UiChart.class, children.get(0));
    }

    // Small helpers so the tests read as assertions rather than try/catch.
    private interface JsonSupplier { String get() throws Exception; }
    private interface NodeSupplier { UiNode get() throws Exception; }

    private static String assertDoesNotThrowJson(JsonSupplier s) {
        try { return s.get(); } catch (Exception e) { throw new AssertionError(e); }
    }
    private static UiNode assertDoesNotThrowNode(NodeSupplier s) {
        try { return s.get(); } catch (Exception e) { throw new AssertionError(e); }
    }
}
