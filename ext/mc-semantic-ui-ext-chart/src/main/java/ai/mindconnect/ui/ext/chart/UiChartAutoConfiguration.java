package ai.mindconnect.ui.ext.chart;

import ai.mindconnect.ui.ssr.SuiHelperContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Publishes the extension's two beans: the Jackson module that registers the
 * {@code chart} node type, and the helper contributor the SSR template needs.
 */
@AutoConfiguration
public class UiChartAutoConfiguration {

    /**
     * Only when handlebars is present — i.e. when the app has SSR switched on.
     * ChartHelpers touches Handlebars types, so loading it without the library
     * would fail; a browser-only consumer never needs it.
     */
    @Bean
    @ConditionalOnClass(name = "com.github.jknack.handlebars.Handlebars")
    @ConditionalOnMissingBean(ChartHelpers.class)
    public SuiHelperContributor suiChartHelpers() {
        return new ChartHelpers();
    }

    /**
     * Registers the {@code chart} subtype with every Spring-managed
     * ObjectMapper. Spring Boot wires each {@code Module} bean automatically.
     */
    @Bean
    @ConditionalOnMissingBean(UiChartModule.class)
    public UiChartModule uiChartModule() {
        return new UiChartModule();
    }
}
