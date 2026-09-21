package ai.mindconnect.ui.ext.calendar;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Teaches an {@code ObjectMapper} the two node types of this extension:
 * {@code calendar} and {@code calendar-event}. Public no-arg constructor so
 * {@code ServiceLoader} can find it — see
 * {@code META-INF/services/com.fasterxml.jackson.databind.Module}.
 */
public class UiCalendarModule extends SimpleModule {

    public UiCalendarModule() {
        super("UiCalendarModule");
        registerSubtypes(
                new NamedType(UiCalendar.class, "calendar"),
                new NamedType(UiCalendarEvent.class, "calendar-event"));
    }
}
