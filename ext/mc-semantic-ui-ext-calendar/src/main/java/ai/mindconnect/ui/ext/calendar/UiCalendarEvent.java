package ai.mindconnect.ui.ext.calendar;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One event on a {@link UiCalendar}. {@code title} (inherited) is what the
 * chip shows; {@link #start} and {@link #end} say when. A start without a time
 * ({@code 2026-09-22}) makes an all-day event, which may run over several
 * days; a start with one ({@code 2026-09-21T09:00}) makes a timed event, drawn
 * on its start day and lasting an hour when {@link #end} is absent.
 *
 * <p>An event reacts to clicks like any node — {@link #onClick(UiTrigger)}
 * opens its detail, say. It is a node with an id so the model can address it,
 * but it is drawn only as part of its calendar: to change one, send the
 * calendar again.
 */
@JsonTypeName("calendar-event")
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiCalendarEvent extends UiNode {

    /** {@code yyyy-MM-dd} for an all-day event, {@code yyyy-MM-ddTHH:mm} for a timed one. */
    private String start;
    /** Same form as {@link #start}. Absent: one hour after the start, or the same day. */
    private String end;
    /** Forces all-day; when null, a {@link #start} without a time means all-day. */
    private Boolean allDay;
    /** Accent colour (any CSS colour). */
    private String color;

    public static UiCalendarEvent of(String id, String title) {
        var e = new UiCalendarEvent();
        e.setId(id);
        e.setTitle(title);
        return e;
    }

    /** A timed event; {@code end} may be null for one hour. */
    public static UiCalendarEvent timed(String id, String title, LocalDateTime start, LocalDateTime end) {
        var e = of(id, title);
        e.start = minutes(start);
        e.end = end == null ? null : minutes(end);
        return e;
    }

    /** A timed event from ISO strings such as {@code 2026-09-21T09:00}. */
    public static UiCalendarEvent timed(String id, String title, String start, String end) {
        var e = of(id, title);
        e.start = start;
        e.end = end;
        return e;
    }

    /** An all-day event; {@code end} may be null for a single day. */
    public static UiCalendarEvent allDay(String id, String title, LocalDate start, LocalDate end) {
        var e = of(id, title);
        e.start = start.toString();
        e.end = end == null ? null : end.toString();
        e.allDay = true;
        return e;
    }

    private static String minutes(LocalDateTime t) {
        // yyyy-MM-ddTHH:mm — seconds never travel; the painters read minutes.
        return t.toLocalDate() + "T" + String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    public UiCalendarEvent color(String color) {
        this.color = color;
        return this;
    }

    public UiCalendarEvent onClick(UiTrigger trigger) {
        setOnClick(trigger);
        return this;
    }
}
