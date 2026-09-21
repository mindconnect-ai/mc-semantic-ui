package ai.mindconnect.ui.ext.calendar;

import ai.mindconnect.ui.ssr.SuiHelperContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Publishes the extension's two beans: the Jackson module that registers the
 * calendar node types, and the helper contributor the SSR template needs.
 */
@AutoConfiguration
public class UiCalendarAutoConfiguration {

    /** Only when handlebars is present — i.e. when the app has SSR switched on. */
    @Bean
    @ConditionalOnClass(name = "com.github.jknack.handlebars.Handlebars")
    @ConditionalOnMissingBean(CalendarHelpers.class)
    public SuiHelperContributor suiCalendarHelpers() {
        return new CalendarHelpers();
    }

    @Bean
    @ConditionalOnMissingBean(UiCalendarModule.class)
    public UiCalendarModule uiCalendarModule() {
        return new UiCalendarModule();
    }
}
