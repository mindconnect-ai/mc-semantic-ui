package ai.mindconnect.ui.ext.chart;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The two painters draw the same characters. Every fixture in
 * {@code src/test/resources/chart-golden/} is a chart node as JSON; next to it
 * is what the TypeScript painter drew for it ({@code scripts/chart-golden.mjs}
 * writes those). This test draws the fixture in Java and compares.
 *
 * <p>After a change to the drawing: change both painters, run
 * {@code npm run build && node scripts/chart-golden.mjs}, and this test says
 * whether Java still agrees.
 */
class ChartPainterGoldenTest {

    private static final Path DIR = Path.of("src/test/resources/chart-golden");
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new UiChartModule());

    @TestFactory
    Stream<DynamicTest> javaDrawsWhatTypeScriptDraws() throws IOException {
        var fixtures = Files.list(DIR).filter(p -> p.toString().endsWith(".json")).sorted().toList();
        assertFalse(fixtures.isEmpty(), "no fixtures in " + DIR.toAbsolutePath());
        return fixtures.stream().map(json -> DynamicTest.dynamicTest(json.getFileName().toString(), () -> {
            UiChart chart = (UiChart) MAPPER.readValue(Files.readString(json, StandardCharsets.UTF_8), UiNode.class);
            Path svg = Path.of(json.toString().replaceAll("\\.json$", ".svg"));
            String expected = Files.readString(svg, StandardCharsets.UTF_8).stripTrailing();
            assertEquals(expected, ChartPainter.svg(chart), json.getFileName().toString());
        }));
    }
}
