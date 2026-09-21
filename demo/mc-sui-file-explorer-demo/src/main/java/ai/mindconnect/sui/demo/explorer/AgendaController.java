package ai.mindconnect.sui.demo.explorer;

import ai.mindconnect.ui.ext.calendar.UiCalendar;
import ai.mindconnect.ui.ext.calendar.UiCalendarEvent;
import ai.mindconnect.ui.ext.kanban.UiKanban;
import ai.mindconnect.ui.ext.kanban.UiKanbanCard;
import ai.mindconnect.ui.ext.kanban.UiKanbanLane;
import ai.mindconnect.ui.model.UiLink;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiText;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * A page of two extension nodes, a calendar and a kanban board. Nothing in
 * the demo wires either: the jars are on the classpath, the asset registry
 * found their assets.json, the server renders them, and /sui/assets.js
 * installs their browser side before the event bus takes over.
 */
@RestController
public class AgendaController {

    @GetMapping(path = "/agenda", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE})
    public UiPage agenda() {
        LocalDate today = LocalDate.now();
        var calendar = UiCalendar.of("agenda-calendar", UiCalendar.View.WEEK, today)
                .hours(8, 18)
                .event(UiCalendarEvent.timed("standup", "Standup",
                        today.atTime(9, 0), today.atTime(9, 15)).color("#4f6bed"))
                .event(UiCalendarEvent.timed("review", "Review uploads",
                        today.plusDays(1).atTime(14, 0), today.plusDays(1).atTime(15, 30)))
                .event(UiCalendarEvent.allDay("cleanup", "Clean up explorer-root", today.plusDays(2), null));
        var board = UiKanban.of("agenda-board",
                UiKanbanLane.of("todo", "To do", UiKanbanCard.of("k1", "Sort the uploads").tag("files")),
                UiKanbanLane.of("doing", "Doing", UiKanbanCard.of("k2", "Write the README")).limit(2),
                UiKanbanLane.of("done", "Done"));
        var page = UiStack.of("agenda").gap(20)
                .child(UiText.of("agenda-title", "Agenda").withCssClass("explorer-title"))
                .child(UiLink.of("agenda-files", "/files", "← back to the files"))
                .child(calendar)
                .child(board);
        return UiPage.of("/agenda", page);
    }
}
