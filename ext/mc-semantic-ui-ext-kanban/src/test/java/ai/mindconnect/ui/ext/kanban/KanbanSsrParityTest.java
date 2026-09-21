package ai.mindconnect.ui.ext.kanban;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.ssr.SuiServerRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server templates and the browser painter draw the same board.
 *
 * <p>{@code board.expected.html} is the agreed markup for {@code board.json}.
 * The TypeScript test ({@code src/test/ts/kanban.test.mjs}) holds the browser
 * renderer to it; this one holds the three Handlebars templates to the same
 * file, so the two cannot drift apart unnoticed. Whitespace between tags is
 * ignored, because each template ends in a newline.
 */
class KanbanSsrParityTest {

    private static String resource(String name) throws Exception {
        try (var in = Objects.requireNonNull(
                KanbanSsrParityTest.class.getResourceAsStream("/kanban/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String tidy(String html) {
        return html.replaceAll(">\\s+<", "><").trim();
    }

    @Test
    void theTemplatesDrawBoardJsonAsBoardExpectedHtml() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        UiNode board = mapper.readValue(resource("board.json"), UiNode.class);

        String html = new SuiServerRenderer(mapper).render(board);

        assertEquals(tidy(resource("board.expected.html")), tidy(html));
    }

    @Test
    void aBoardBuiltInJavaRendersWithoutAnyJavaScriptHooksWhenReadOnly() {
        var board = UiKanban.of("b", UiKanbanLane.of("l", "Lane", UiKanbanCard.of("c", "Card")))
                .readOnly(true);
        String html = new SuiServerRenderer(new ObjectMapper().findAndRegisterModules()).render(board);
        assertTrue(html.contains("<sui-kanban class=\"sui-kanban\" id=\"b\" data-sui=\"kanban\" data-readonly=\"\">"), html);
        assertTrue(html.contains("draggable=\"true\""), html);
    }
}
