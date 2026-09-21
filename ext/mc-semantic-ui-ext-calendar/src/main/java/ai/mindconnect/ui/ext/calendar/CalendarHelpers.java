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
        // {{{calendarHtml this}}} — the whole element, straight from CalendarPainter.
        handlebars.registerHelper("calendarHtml", (ctx, opts) -> {
            if (!(ctx instanceof UiCalendar calendar)) return "";
            return new Handlebars.SafeString(CalendarPainter.html(calendar, mapper));
        });
    }
}
