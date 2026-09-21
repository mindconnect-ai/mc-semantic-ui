package ai.mindconnect.ui.ext.calendar;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.ssr.SuiServerRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Java painter and the TypeScript one draw the same calendar.
 *
 * <p>{@code calendar.json} in each view must render to
 * {@code calendar.expected-<view>.html}, three files the TypeScript test
 * ({@code src/test/ts/calendar.test.mjs}) holds the browser painter to as
 * well. Rendered through the real {@code SuiServerRenderer}, so the template
 * and the helper are covered too.
 */
class CalendarPainterParityTest {

    private static String resource(String name) throws Exception {
        try (var in = Objects.requireNonNull(
                CalendarPainterParityTest.class.getResourceAsStream("/calendar/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String tidy(String html) {
        return html.replaceAll(">\\s+<", "><").trim();
    }

    @ParameterizedTest
    @EnumSource(UiCalendar.View.class)
    void bothPaintersDrawTheSameBytes(UiCalendar.View view) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var calendar = (UiCalendar) mapper.readValue(resource("calendar.json"), UiNode.class);
        calendar.setView(view);

        String html = new SuiServerRenderer(mapper, java.util.List.of(new CalendarHelpers())).render(calendar);

        assertEquals(tidy(resource("calendar.expected-" + view.name().toLowerCase() + ".html")), tidy(html));
    }
}
