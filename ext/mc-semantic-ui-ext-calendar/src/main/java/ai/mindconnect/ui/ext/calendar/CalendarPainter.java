package ai.mindconnect.ui.ext.calendar;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiTrigger;
import ai.mindconnect.ui.ssr.SuiHandlebarsHelpers;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws a {@link UiCalendar} as HTML — the server-side twin of
 * {@code calendar/extension.ts}, held to the same bytes by
 * {@code CalendarPainterParityTest}. Every helper here mirrors one there,
 * in the same order, so a change on either side has an obvious home on the
 * other.
 */
public final class CalendarPainter {

    private CalendarPainter() {}

    private static final Map<String, String> HTML_ESCAPE = Map.of(
            "&", "&amp;", "<", "&lt;", ">", "&gt;", "\"", "&quot;", "'", "&#39;");

    static String esc(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            sb.append(HTML_ESCAPE.getOrDefault(ch, ch));
        }
        return sb.toString();
    }

    private static final String[] EN_WEEKDAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] EN_WEEKDAYS_LONG = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] EN_MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
    private static final String[] EN_MONTHS_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /** The resolved words: the model's, or English. */
    private record Labels(List<String> weekdays, List<String> weekdaysLong, List<String> months, List<String> monthsShort,
                          String today, String day, String week, String month, String allDay, String more,
                          String previous, String next) {
        static Labels of(UiCalendar.Labels l) {
            if (l == null) l = new UiCalendar.Labels();
            return new Labels(
                    list(l.getWeekdays(), 7, EN_WEEKDAYS), list(l.getWeekdaysLong(), 7, EN_WEEKDAYS_LONG),
                    list(l.getMonths(), 12, EN_MONTHS), list(l.getMonthsShort(), 12, EN_MONTHS_SHORT),
                    or(l.getToday(), "Today"), or(l.getDay(), "Day"), or(l.getWeek(), "Week"), or(l.getMonth(), "Month"),
                    or(l.getAllDay(), "All day"), or(l.getMore(), "+{n} more"), or(l.getPrevious(), "Previous"), or(l.getNext(), "Next"));
        }
        private static List<String> list(List<String> own, int n, String[] fallback) {
            return own != null && own.size() == n ? own : List.of(fallback);
        }
        private static String or(String s, String fallback) { return s == null ? fallback : s; }
    }

    // ── Dates ────────────────────────────────────────────────────────────────

    private static final Pattern DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T(\\d{2}):(\\d{2})");

    private static LocalDate parseDate(String iso) {
        if (iso == null) return null;
        Matcher m = DATE.matcher(iso);
        if (!m.find()) return null;
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    /** Minutes past midnight, or null when the value carries no time. */
    private static Integer parseMinutes(String iso) {
        Matcher m = TIME.matcher(iso);
        return m.find() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : null;
    }

    private static String pad2(int n) { return (n < 10 ? "0" : "") + n; }
    private static String iso(LocalDate d) { return d.toString(); }
    /** ISO weekday: 0 = Monday … 6 = Sunday. */
    private static int weekday(LocalDate d) { return d.getDayOfWeek().getValue() - 1; }
    private static String hhmm(int minutes) { return pad2(minutes / 60) + ":" + pad2(minutes % 60); }

    // ── Events, normalised ───────────────────────────────────────────────────

    private record Ev(UiCalendarEvent node, LocalDate start, LocalDate end, Integer startMin, Integer endMin, boolean allDay) {}

    private static List<Ev> normalise(List<UiCalendarEvent> events) {
        var out = new ArrayList<Ev>();
        if (events == null) return out;
        for (var node : events) {
            if (node.getStart() == null) continue;
            LocalDate start = parseDate(node.getStart());
            if (start == null) continue;
            Integer startMin = parseMinutes(node.getStart());
            boolean allDay = node.getAllDay() != null ? node.getAllDay() : startMin == null;
            LocalDate end = start;
            Integer endMin = null;
            if (node.getEnd() != null) {
                LocalDate e = parseDate(node.getEnd());
                end = e == null ? start : e;
                endMin = parseMinutes(node.getEnd());
            }
            if (allDay) {
                if (end.isBefore(start)) end = start;
                out.add(new Ev(node, start, end, null, null, true));
            } else {
                int s = startMin == null ? 0 : startMin;
                int e = end.isEqual(start) && endMin != null ? endMin : (end.isAfter(start) ? 1440 : s + 60);
                if (e <= s) e = Math.min(1440, s + 60);
                out.add(new Ev(node, start, start, s, e, false));
            }
        }
        return out;
    }

    private static List<Ev> onDay(List<Ev> events, LocalDate day) {
        var list = new ArrayList<Ev>();
        for (var e : events) {
            boolean on = e.allDay ? !e.start.isAfter(day) && !day.isAfter(e.end) : e.start.isEqual(day);
            if (on) list.add(e);
        }
        list.sort(Comparator.comparingInt((Ev e) -> e.allDay ? 0 : 1)
                .thenComparingInt(e -> e.startMin == null ? 0 : e.startMin));
        return list;
    }

    // ── Markup ───────────────────────────────────────────────────────────────

    private static String cls(String base, UiNode node) {
        String marker = node.getDisplay() == UiNode.Display.HIDDEN ? "sui-hidden"
                : node.getDisplay() == UiNode.Display.BLANK ? "sui-blank" : null;
        var own = new StringBuilder();
        String raw = node.getCssClass() == null ? "" : node.getCssClass();
        for (String token : raw.trim().split("\\s+")) {
            if (token.isEmpty() || token.equals("sui-hidden") || token.equals("sui-blank")) continue;
            if (own.length() > 0) own.append(' ');
            own.append(token);
        }
        var sb = new StringBuilder(base);
        if (own.length() > 0) sb.append(' ').append(esc(own));
        if (marker != null) sb.append(' ').append(marker);
        return sb.toString();
    }

    private static String accent(String color) {
        return color == null || color.isEmpty() ? "" : " style=\"--sui-calendar-accent:" + esc(color) + "\"";
    }

    private static UiTrigger fill(UiTrigger t, Map<String, String> values) {
        var copy = new UiTrigger();
        copy.setMethod(t.getMethod());
        copy.setPayload(t.getPayload());
        copy.setBehavior(t.getBehavior());
        copy.setHandler(t.getHandler());
        copy.setPatch(t.getPatch());
        String url = t.getUrl() == null ? "" : t.getUrl();
        for (var v : values.entrySet()) url = url.replace("{" + v.getKey() + "}", v.getValue());
        copy.setUrl(url);
        return copy;
    }

    private static String chip(Ev e, LocalDate day, ObjectMapper mapper) {
        boolean first = e.start.isEqual(day);
        String id = first ? esc(e.node.getId()) : esc(e.node.getId()) + "__" + iso(day);
        String time = e.allDay ? "" : "<span class=\"sui-calendar-event-time\">" + hhmm(e.startMin) + "</span>";
        return "<li class=\"" + cls("sui-calendar-event", e.node) + (e.allDay ? " is-allday" : "") + "\""
                + SuiHandlebarsHelpers.eventAttrs(e.node, mapper) + " id=\"" + id + "\" data-sui=\"calendar-event\"" + accent(e.node.getColor()) + ">"
                + time + "<span class=\"sui-calendar-event-title\">" + esc(e.node.getTitle()) + "</span></li>";
    }

    private static String block(Ev e, int startHour, int endHour, ObjectMapper mapper) {
        int lo = startHour * 60, hi = endHour * 60;
        int s = Math.max(lo, e.startMin), en = Math.min(hi, e.endMin);
        if (en <= s) return "";
        // One style attribute: the accent (when there is one) and the position.
        String style = (e.node.getColor() == null || e.node.getColor().isEmpty() ? "" : "--sui-calendar-accent:" + esc(e.node.getColor()) + ";")
                + "--sui-calendar-start:" + (s - lo) + ";--sui-calendar-length:" + Math.max(15, en - s);
        return "<div class=\"" + cls("sui-calendar-event", e.node) + "\"" + SuiHandlebarsHelpers.eventAttrs(e.node, mapper)
                + " id=\"" + esc(e.node.getId()) + "\" data-sui=\"calendar-event\" style=\"" + style + "\">"
                + "<span class=\"sui-calendar-event-time\">" + hhmm(e.startMin) + "</span><span class=\"sui-calendar-event-title\">"
                + esc(e.node.getTitle()) + "</span></div>";
    }

    private record Ctx(UiCalendar node, UiCalendar.View view, LocalDate date, LocalDate today, LocalDate selected,
                       boolean sundayFirst, int startHour, int endHour, Labels labels, List<Ev> events, ObjectMapper mapper) {}

    private static Ctx context(UiCalendar node, ObjectMapper mapper) {
        UiCalendar.View view = node.getView() == UiCalendar.View.DAY || node.getView() == UiCalendar.View.WEEK
                ? node.getView() : UiCalendar.View.MONTH;
        LocalDate today = parseDate(node.getToday());
        LocalDate date = parseDate(node.getDate());
        if (date == null) date = today != null ? today : LocalDate.of(1970, 1, 1);
        int startHour = Math.min(23, Math.max(0, node.getStartHour() == null ? 0 : node.getStartHour()));
        int endHour = Math.min(24, Math.max(startHour + 1, node.getEndHour() == null ? 24 : node.getEndHour()));
        return new Ctx(node, view, date, today, parseDate(node.getSelectedDate()),
                node.getWeekStart() == UiCalendar.WeekStart.SUNDAY, startHour, endHour,
                Labels.of(node.getLabels()), normalise(node.getEvents()), mapper);
    }

    private static int column(Ctx c, LocalDate d) { return c.sundayFirst ? (weekday(d) + 1) % 7 : weekday(d); }
    private static LocalDate weekOf(Ctx c, LocalDate d) { return d.minusDays(column(c, d)); }

    private static String dayState(Ctx c, LocalDate d) {
        return (c.today != null && c.today.isEqual(d) ? " is-today" : "")
                + (c.selected != null && c.selected.isEqual(d) ? " is-selected" : "");
    }

    private static String heading(Ctx c) {
        Labels l = c.labels;
        LocalDate d = c.date;
        if (c.view == UiCalendar.View.DAY) {
            return l.weekdaysLong.get(weekday(d)) + ", " + d.getDayOfMonth() + " " + l.months.get(d.getMonthValue() - 1) + " " + d.getYear();
        }
        if (c.view == UiCalendar.View.WEEK) {
            LocalDate a = weekOf(c, d), b = a.plusDays(6);
            return a.getMonthValue() == b.getMonthValue()
                    ? a.getDayOfMonth() + " – " + b.getDayOfMonth() + " " + l.monthsShort.get(b.getMonthValue() - 1) + " " + b.getYear()
                    : a.getDayOfMonth() + " " + l.monthsShort.get(a.getMonthValue() - 1) + " – " + b.getDayOfMonth() + " "
                    + l.monthsShort.get(b.getMonthValue() - 1) + " " + b.getYear();
        }
        return l.months.get(d.getMonthValue() - 1) + " " + d.getYear();
    }

    private static LocalDate shift(Ctx c, int n) {
        return switch (c.view) {
            case DAY -> c.date.plusDays(n);
            case WEEK -> c.date.plusDays(7L * n);
            case MONTH -> c.date.plusMonths(n);
        };
    }

    private static String btn(Ctx c, UiTrigger t, String label, Map<String, String> values, String extra) {
        return "<a class=\"sui-calendar-btn" + extra + "\" href=\"#\" data-trigger='"
                + SuiHandlebarsHelpers.encodeTrigger(fill(t, values), c.mapper) + "'>" + label + "</a>";
    }

    private static String header(Ctx c) {
        UiTrigger t = c.node.getOnNavigate();
        String title = "<h2 class=\"sui-calendar-title\">" + esc(heading(c)) + "</h2>";
        if (t == null) return "<header class=\"sui-calendar-head\">" + title + "</header>";
        String view = c.view.name();
        String nav = "<div class=\"sui-calendar-nav\">"
                + "<a class=\"sui-calendar-btn\" href=\"#\" data-trigger='"
                + SuiHandlebarsHelpers.encodeTrigger(fill(t, Map.of("date", iso(shift(c, -1)), "view", view)), c.mapper)
                + "' aria-label=\"" + esc(c.labels.previous) + "\">&lsaquo;</a>"
                + (c.today != null ? btn(c, t, esc(c.labels.today), Map.of("date", iso(c.today), "view", view), "") : "")
                + "<a class=\"sui-calendar-btn\" href=\"#\" data-trigger='"
                + SuiHandlebarsHelpers.encodeTrigger(fill(t, Map.of("date", iso(shift(c, 1)), "view", view)), c.mapper)
                + "' aria-label=\"" + esc(c.labels.next) + "\">&rsaquo;</a>"
                + "</div>";
        var views = new StringBuilder("<div class=\"sui-calendar-views\">");
        for (UiCalendar.View v : new UiCalendar.View[]{UiCalendar.View.DAY, UiCalendar.View.WEEK, UiCalendar.View.MONTH}) {
            String label = v == UiCalendar.View.DAY ? c.labels.day : v == UiCalendar.View.WEEK ? c.labels.week : c.labels.month;
            views.append(btn(c, t, esc(label), Map.of("date", iso(c.date), "view", v.name()), v == c.view ? " is-active" : ""));
        }
        views.append("</div>");
        return "<header class=\"sui-calendar-head\">" + nav + title + views + "</header>";
    }

    private static String weekdayHeads(Ctx c, LocalDate first) {
        var sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            LocalDate d = first.plusDays(i);
            sb.append("<span class=\"sui-calendar-weekday\" role=\"columnheader\">").append(esc(c.labels.weekdays.get(weekday(d)))).append("</span>");
        }
        return sb.toString();
    }

    private static String monthBody(Ctx c) {
        YearMonth ym = YearMonth.from(c.date);
        LocalDate first = ym.atDay(1), last = ym.atEndOfMonth();
        LocalDate gridStart = weekOf(c, first);
        LocalDate gridEnd = last.plusDays(6 - column(c, last));
        long weeks = (ChronoUnit.DAYS.between(gridStart, gridEnd) + 1) / 7;
        int max = Math.max(1, c.node.getMaxEventsPerDay() == null ? 3 : c.node.getMaxEventsPerDay());
        var rows = new StringBuilder();
        for (int w = 0; w < weeks; w++) {
            var cells = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                LocalDate d = gridStart.plusDays(w * 7L + i);
                List<Ev> evs = onDay(c.events, d);
                var shown = new StringBuilder();
                for (int k = 0; k < Math.min(max, evs.size()); k++) shown.append(chip(evs.get(k), d, c.mapper));
                String more = evs.size() > max
                        ? "<span class=\"sui-calendar-more\">" + esc(c.labels.more.replace("{n}", String.valueOf(evs.size() - max))) + "</span>"
                        : "";
                String outside = d.getMonthValue() != c.date.getMonthValue() ? " is-outside" : "";
                cells.append("<div class=\"sui-calendar-day").append(outside).append(dayState(c, d))
                        .append("\" role=\"gridcell\" data-date=\"").append(iso(d)).append("\">")
                        .append("<span class=\"sui-calendar-daynum\">").append(d.getDayOfMonth()).append("</span>")
                        .append("<ul class=\"sui-calendar-events\">").append(shown).append("</ul>").append(more).append("</div>");
            }
            rows.append("<div class=\"sui-calendar-week\" role=\"row\">").append(cells).append("</div>");
        }
        return "<div class=\"sui-calendar-month\" role=\"grid\">"
                + "<div class=\"sui-calendar-weekdays\" role=\"row\">" + weekdayHeads(c, gridStart) + "</div>" + rows + "</div>";
    }

    private static String timeBody(Ctx c) {
        int days = c.view == UiCalendar.View.DAY ? 1 : 7;
        LocalDate first = c.view == UiCalendar.View.DAY ? c.date : weekOf(c, c.date);
        int hours = c.endHour - c.startHour;
        var heads = new StringBuilder();
        var allday = new StringBuilder();
        var cols = new StringBuilder();
        for (int i = 0; i < days; i++) {
            LocalDate d = first.plusDays(i);
            List<Ev> evs = onDay(c.events, d);
            String name = esc(c.labels.weekdays.get(weekday(d)));
            heads.append("<span class=\"sui-calendar-weekday").append(dayState(c, d)).append("\" role=\"columnheader\" data-date=\"")
                    .append(iso(d)).append("\">").append(name).append(' ').append(d.getDayOfMonth()).append("</span>");
            var chips = new StringBuilder();
            for (var e : evs) if (e.allDay) chips.append(chip(e, d, c.mapper));
            allday.append("<div class=\"sui-calendar-allday-day").append(dayState(c, d)).append("\" role=\"gridcell\" data-date=\"")
                    .append(iso(d)).append("\"><ul class=\"sui-calendar-events\">").append(chips).append("</ul></div>");
            var slots = new StringBuilder();
            for (int h = c.startHour; h < c.endHour; h++) slots.append("<div class=\"sui-calendar-slot\" data-hour=\"").append(h).append("\"></div>");
            var blocks = new StringBuilder();
            for (var e : evs) if (!e.allDay) blocks.append(block(e, c.startHour, c.endHour, c.mapper));
            cols.append("<div class=\"sui-calendar-daycol").append(dayState(c, d)).append("\" role=\"gridcell\" data-date=\"")
                    .append(iso(d)).append("\">").append(slots).append(blocks).append("</div>");
        }
        var hourLabels = new StringBuilder();
        for (int h = c.startHour; h < c.endHour; h++) hourLabels.append("<span class=\"sui-calendar-hour\">").append(hhmm(h * 60)).append("</span>");
        return "<div class=\"sui-calendar-timegrid sui-calendar-timegrid--" + c.view.name().toLowerCase() + "\" role=\"grid\" style=\"--sui-calendar-hours:" + hours + "\">"
                + "<div class=\"sui-calendar-weekdays\" role=\"row\"><span class=\"sui-calendar-corner\"></span>" + heads + "</div>"
                + "<div class=\"sui-calendar-allday\" role=\"row\"><span class=\"sui-calendar-corner\">" + esc(c.labels.allDay) + "</span>" + allday + "</div>"
                + "<div class=\"sui-calendar-times\"><div class=\"sui-calendar-hours\">" + hourLabels + "</div>" + cols + "</div></div>";
    }

    /** The whole element — what {@code {{{calendarHtml this}}}} inserts. */
    public static String html(UiCalendar node, ObjectMapper mapper) {
        Ctx c = context(node, mapper);
        String select = node.getOnSelect() != null
                ? " data-select-trigger='" + SuiHandlebarsHelpers.encodeTrigger(node.getOnSelect(), mapper) + "'" : "";
        String view = c.view.name();
        return "<sui-calendar class=\"" + cls("sui-calendar sui-calendar--" + view.toLowerCase(), node) + "\""
                + SuiHandlebarsHelpers.eventAttrs(node, mapper) + " id=\"" + esc(node.getId()) + "\" data-sui=\"calendar\""
                + " data-view=\"" + view + "\" data-date=\"" + iso(c.date) + "\"" + select + ">"
                + header(c) + "<div class=\"sui-calendar-body\">" + (c.view == UiCalendar.View.MONTH ? monthBody(c) : timeBody(c))
                + "</div></sui-calendar>";
    }
}
