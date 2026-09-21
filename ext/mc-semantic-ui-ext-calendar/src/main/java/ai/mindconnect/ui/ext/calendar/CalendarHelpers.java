package ai.mindconnect.ui.ext.calendar;

import ai.mindconnect.ui.ssr.SuiHelperContributor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;

/**
 * Supplies the one helper this module's template calls. Registered two ways so
 * the same JAR works with and without Spring: via {@code META-INF/services}
 * (ServiceLoader) and as a Spring bean from {@link UiCalendarAutoConfiguration}.
 * Registering twice is harmless — the second replaces the first with the same.
 */
public final class CalendarHelpers implements SuiHelperContributor {

    @Override
    public void contribute(Handlebars handlebars, ObjectMapper mapper) {
        // {{{calendarOpen this}}} … {{{calendarRest this}}} — the element in two
        // halves, straight from CalendarPainter; the template renders the
        // header's extras between them with the core's `render` helper.
        handlebars.registerHelper("calendarOpen", (ctx, opts) -> {
            if (!(ctx instanceof UiCalendar calendar)) return "";
            return new Handlebars.SafeString(CalendarPainter.open(calendar, mapper));
        });
        handlebars.registerHelper("calendarRest", (ctx, opts) -> {
            if (!(ctx instanceof UiCalendar calendar)) return "";
            return new Handlebars.SafeString(CalendarPainter.rest(calendar, mapper));
        });
    }
}
