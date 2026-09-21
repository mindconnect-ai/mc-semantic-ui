package ai.mindconnect.ui.ext.calendar;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calendar nodes live here, not in the core — so they have to teach Jackson
 * about themselves, and the builder has to produce what the painters expect.
 */
class UiCalendarModuleTest {

    private static UiCalendar sample() {
        return UiCalendar.of("cal", UiCalendar.View.WEEK, LocalDate.of(2026, 9, 21))
                .today(LocalDate.of(2026, 9, 21))
                .hours(7, 19)
                .event(UiCalendarEvent.timed("e1", "Standup",
                        LocalDateTime.of(2026, 9, 21, 9, 0), LocalDateTime.of(2026, 9, 21, 9, 30)).color("#4f6bed")
                        .onClick(UiTrigger.go("/ev/e1")))
                .event(UiCalendarEvent.allDay("e2", "Offsite", LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 24)))
                .onNavigate(UiTrigger.go("/cal?date={date}&view={view}"))
                .onSelect(UiTrigger.api("POST", "/cal/pick?date={date}&hour={hour}"));
    }

    @Test
    void serviceLoaderIsEnoughToRegisterTheSubtypes() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();

        String json = mapper.writeValueAsString(sample());
        assertTrue(json.contains("\"type\":\"calendar\""), json);
        assertTrue(json.contains("\"type\":\"calendar-event\""), json);
        assertTrue(json.contains("\"start\":\"2026-09-21T09:00\""), json);

        UiNode back = mapper.readValue(json, UiNode.class);
        var cal = assertInstanceOf(UiCalendar.class, back);
        assertEquals(UiCalendar.View.WEEK, cal.getView());
        assertEquals("2026-09-21", cal.getDate());
        assertEquals(7, cal.getStartHour());
        assertEquals(2, cal.getEvents().size());
        assertEquals("/ev/e1", cal.getEvents().get(0).getOnClick().getUrl());
        assertEquals(Boolean.TRUE, cal.getEvents().get(1).getAllDay());
    }

    @Test
    void aCalendarNestsInsideAnOrdinaryCoreTree() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var stack = UiStack.of(sample());
        stack.setId("wrap");
        UiNode back = mapper.readValue(mapper.writeValueAsString(stack), UiNode.class);
        assertInstanceOf(UiCalendar.class, ((UiStack) back).getChildren().get(0));
    }

    @Test
    void labelsComeFromJavaTimeForALocale() {
        var labels = UiCalendar.Labels.of(Locale.GERMAN);
        assertEquals(7, labels.getWeekdays().size());
        assertEquals("Montag", labels.getWeekdaysLong().get(0));
        assertEquals("September", labels.getMonths().get(8));
        assertEquals(List.of("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez."),
                labels.getMonthsShort());
    }

    @Test
    void theHeadingUsesTheModelsWords() {
        var cal = sample().labels(Locale.GERMAN);
        cal.setView(UiCalendar.View.DAY);
        String html = CalendarPainter.html(cal, new ObjectMapper().findAndRegisterModules());
        assertTrue(html.contains("<h2 class=\"sui-calendar-title\">Montag, 21 September 2026</h2>"), html);
        // The words that are not names stay English unless set.
        assertTrue(html.contains(">Today</a>"), html);
    }
}
