package ai.mindconnect.ui.ext.kanban;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The kanban nodes live here, not in the core — so they have to teach Jackson
 * about themselves. These tests prove the types are discoverable without any
 * manual wiring and round-trip inside an ordinary core tree, lanes and cards
 * included.
 */
class UiKanbanModuleTest {

    private static UiKanban sample() {
        return UiKanban.of("board",
                        UiKanbanLane.of("todo", "To do",
                                UiKanbanCard.of("t1", "Write the spec").badge("P1").tag("docs")
                                        .onClick(UiTrigger.go("/cards/t1"))),
                        UiKanbanLane.of("doing", "Doing").limit(3),
                        UiKanbanLane.of("done", "Done").locked(true))
                .onMove(UiTrigger.api("POST", "/board/move?card={card}&to={to}&index={index}"));
    }

    @Test
    void serviceLoaderIsEnoughToRegisterTheSubtypes() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();

        String json = mapper.writeValueAsString(sample());
        assertTrue(json.contains("\"type\":\"kanban\""), json);
        assertTrue(json.contains("\"type\":\"kanban-lane\""), json);
        assertTrue(json.contains("\"type\":\"kanban-card\""), json);

        UiNode back = mapper.readValue(json, UiNode.class);
        var board = assertInstanceOf(UiKanban.class, back);
        assertEquals("board", board.getId());
        assertEquals(3, board.getLanes().size());
        assertEquals("/board/move?card={card}&to={to}&index={index}", board.getOnMove().getUrl());
        var todo = board.getLanes().get(0);
        assertEquals("t1", todo.getCards().get(0).getId());
        assertEquals("/cards/t1", todo.getCards().get(0).getOnClick().getUrl());
        assertEquals(3, board.getLanes().get(1).getLimit());
        assertTrue(board.getLanes().get(2).isLocked());
    }

    @Test
    void aBoardNestsInsideAnOrdinaryCoreTree() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var stack = UiStack.of(sample());
        stack.setId("wrap");

        UiNode back = mapper.readValue(mapper.writeValueAsString(stack), UiNode.class);
        var children = ((UiStack) back).getChildren();
        assertEquals(1, children.size());
        assertInstanceOf(UiKanban.class, children.get(0));
    }

    @Test
    void theCountLabelIsForRenderingOnlyAndStaysOutOfTheJson() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var lane = UiKanbanLane.of("doing", "Doing", UiKanbanCard.of("a", "A")).limit(3);
        assertEquals("1/3", lane.getCountLabel());
        assertEquals("0", UiKanbanLane.of("x", "X").getCountLabel());
        assertFalse(mapper.writeValueAsString(lane).contains("countLabel"));
    }
}
