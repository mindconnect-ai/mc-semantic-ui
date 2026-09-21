package ai.mindconnect.ui.ext.calendar;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A calendar: a month, a week or a day, with its events. The server decides
 * what is shown — {@link #view}, {@link #date} and the {@link #events} — and
 * the calendar asks for more through two triggers:
 * <ul>
 *   <li>{@link #onNavigate} — previous, next, today and the view switch. Its
 *       URL may carry {@code {date}} and {@code {view}}; each button is an
 *       ordinary trigger link with them filled in, handled by the event bus
 *       with no extension script.</li>
 *   <li>{@link #onSelect} — a day (month view) or an hour (week and day
 *       views) picked. Its URL may carry {@code {date}} and {@code {hour}};
 *       the browser bundle fills them at click time.</li>
 * </ul>
 *
 * <p>Words the calendar shows come from {@link #labels}; English when unset,
 * {@link #labels(Locale)} fills them from {@code java.time}. Nothing is
 * formatted by locale on the client, so the server and the browser draw the
 * same bytes — which is also why {@link #today} travels in the model.
 *
 * <pre>{@code
 * UiCalendar.of("cal", UiCalendar.View.WEEK, LocalDate.of(2026, 9, 21))
 *     .labels(Locale.GERMAN)
 *     .hours(7, 19)
 *     .event(UiCalendarEvent.timed("e1", "Standup", "2026-09-21T09:00", "2026-09-21T09:30"))
 *     .onNavigate(UiTrigger.go("/cal?date={date}&view={view}"))
 *     .onSelect(UiTrigger.api("POST", "/cal/pick?date={date}&hour={hour}"));
 * }</pre>
 */
@JsonTypeName("calendar")
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiCalendar extends UiNode {

    public enum View { DAY, WEEK, MONTH }
    public enum WeekStart { MONDAY, SUNDAY }

    /** The words the calendar shows. Any field left null falls back to English. */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Labels {
        /** Seven short weekday names, Monday first. */
        private List<String> weekdays;
        /** Seven full weekday names, Monday first. */
        private List<String> weekdaysLong;
        /** Twelve month names, January first. */
        private List<String> months;
        /** Twelve short month names, January first. */
        private List<String> monthsShort;
        private String today;
        private String day;
        private String week;
        private String month;
        private String allDay;
        /** With {@code {n}} for the count, e.g. {@code +{n} more}. */
        private String more;
        private String previous;
        private String next;

        /** Weekday and month names from {@code java.time}; the words stay English unless set. */
        public static Labels of(Locale locale) {
            var l = new Labels();
            l.weekdays = new ArrayList<>();
            l.weekdaysLong = new ArrayList<>();
            for (DayOfWeek d : DayOfWeek.values()) {
                l.weekdays.add(d.getDisplayName(TextStyle.SHORT, locale));
                l.weekdaysLong.add(d.getDisplayName(TextStyle.FULL, locale));
            }
            l.months = new ArrayList<>();
            l.monthsShort = new ArrayList<>();
            for (Month m : Month.values()) {
                l.months.add(m.getDisplayName(TextStyle.FULL, locale));
                l.monthsShort.add(m.getDisplayName(TextStyle.SHORT, locale));
            }
            return l;
        }
    }

    /** Which view. Defaults to {@code MONTH}. */
    private View view;
    /** The day shown, or a day of the week or month shown, as {@code yyyy-MM-dd}. */
    private String date;
    /** Today, as {@code yyyy-MM-dd} — set by {@link #of} so the painters need no clock. */
    private String today;
    /** A day to highlight, as {@code yyyy-MM-dd}. */
    private String selectedDate;
    /** Defaults to {@code MONDAY}. */
    private WeekStart weekStart;
    /** First hour the day and week views show (0–23). Default 0. */
    private Integer startHour;
    /** Hour the day and week views end at (1–24). Default 24. */
    private Integer endHour;
    /** Chips a month cell shows before folding the rest into "+n more". Default 3. */
    private Integer maxEventsPerDay;
    private Labels labels;
    private List<UiCalendarEvent> events;
    /** Previous / next / today and the view switch: {@code {date}} and {@code {view}} in its URL. */
    private UiTrigger onNavigate;
    /** A day or an hour picked: {@code {date}} and {@code {hour}} in its URL. */
    private UiTrigger onSelect;
    /**
     * Nodes of the page's own in the header, after the view switch — a "New
     * event" button, a filter, a legend. Any node type; rendered by the
     * renderer like everything else.
     */
    private List<UiNode> extras;

    /** A calendar on {@code date}, with today set to the server's clock. */
    public static UiCalendar of(String id, View view, LocalDate date) {
        var c = new UiCalendar();
        c.setId(id);
        c.view = view;
        c.date = date.toString();
        c.today = LocalDate.now().toString();
        c.events = new ArrayList<>();
        return c;
    }

    public UiCalendar today(LocalDate today) {
        this.today = today == null ? null : today.toString();
        return this;
    }

    public UiCalendar selectedDate(LocalDate selected) {
        this.selectedDate = selected == null ? null : selected.toString();
        return this;
    }

    public UiCalendar weekStart(WeekStart weekStart) {
        this.weekStart = weekStart;
        return this;
    }

    /** The hours the day and week views show, {@code from} inclusive to {@code to} exclusive. */
    public UiCalendar hours(int from, int to) {
        this.startHour = from;
        this.endHour = to;
        return this;
    }

    public UiCalendar maxEventsPerDay(int max) {
        this.maxEventsPerDay = max;
        return this;
    }

    public UiCalendar labels(Labels labels) {
        this.labels = labels;
        return this;
    }

    /** Weekday and month names for {@code locale}. */
    public UiCalendar labels(Locale locale) {
        return labels(Labels.of(locale));
    }

    public UiCalendar event(UiCalendarEvent event) {
        if (events == null) events = new ArrayList<>();
        events.add(event);
        return this;
    }

    public UiCalendar onNavigate(UiTrigger trigger) {
        this.onNavigate = trigger;
        return this;
    }

    public UiCalendar onSelect(UiTrigger trigger) {
        this.onSelect = trigger;
        return this;
    }

    /** A widget of the page's own for the header — see {@link #extras}. */
    public UiCalendar extra(UiNode node) {
        if (extras == null) extras = new ArrayList<>();
        extras.add(node);
        return this;
    }
}
