package ai.mindconnect.ui.ssr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Activates server-side HTML rendering when:
 * <ul>
 *   <li>{@link Handlebars} is on the classpath (the optional handlebars.java
 *       dependency the consumer pulled in),</li>
 *   <li>Spring's {@link WebMvcConfigurer} is around (regular Spring Boot web
 *       app),</li>
 *   <li>and {@code mindconnect.sui.ssr.enabled=true} is set.</li>
 * </ul>
 *
 * <p>Registers:
 * <ul>
 *   <li>a {@link SuiServerRenderer} bean (one per app),</li>
 *   <li>a {@link UiPageHtmlMessageConverter} that Spring picks for
 *       {@code Accept: text/html} requests,</li>
 *   <li>Spring's {@link HiddenHttpMethodFilter} so HTML forms can issue
 *       {@code DELETE}/{@code PUT} via a hidden {@code _method} field.</li>
 * </ul>
 *
 * <p>Discovered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnClass({ Handlebars.class, WebMvcConfigurer.class })
@ConditionalOnProperty(name = "mindconnect.sui.ssr.enabled", havingValue = "true")
public class SuiSsrAutoConfiguration {

    /**
     * The HTML renderer, wired with every {@link SuiHelperContributor} bean in
     * the context. {@link ConditionalOnMissingBean} still lets an app supply
     * its own renderer instead.
     */
    @Bean
    @ConditionalOnMissingBean
    public SuiServerRenderer suiServerRenderer(
            ObjectMapper mapper,
            org.springframework.beans.factory.ObjectProvider<SuiHelperContributor> contributors) {
        // Every SuiHelperContributor bean is handed to the renderer, which is
        // how an extension module (charts, …) gets the Handlebars helpers its
        // own template needs. Extensions without Spring are still picked up by
        // the renderer's own ServiceLoader lookup.
        return new SuiServerRenderer(mapper, contributors.orderedStream().toList());
    }

    /**
     * Adds the HTML converter <i>before</i> Jackson's JSON converter, so
     * Spring's content negotiation picks HTML for {@code Accept: text/html}
     * but still uses JSON for everything else.
     */
    @Bean
    public WebMvcConfigurer suiSsrWebMvcConfigurer(SuiServerRenderer renderer) {
        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(
                    java.util.List<HttpMessageConverter<?>> converters) {
                converters.add(0, new UiPageHtmlMessageConverter(renderer));
            }
        };
    }

    /**
     * HTML forms can only natively issue {@code GET} and {@code POST}.
     * {@code HiddenHttpMethodFilter} rewrites a POST with
     * {@code _method=DELETE} (or PUT/PATCH) so the controller's
     * {@code @DeleteMapping}/{@code @PutMapping} fires as expected.
     * Required by {@link SsrTriggerMapper}'s form-with-_method output.
     */
    @Bean
    @ConditionalOnMissingBean(HiddenHttpMethodFilter.class)
    public FilterRegistrationBean<HiddenHttpMethodFilter> hiddenHttpMethodFilter() {
        var bean = new FilterRegistrationBean<>(new HiddenHttpMethodFilter());
        // Run before the dispatcher so the rewrite is in effect by the time
        // request mapping decides which controller method to call.
        bean.setOrder(Integer.MIN_VALUE);
        return bean;
    }
}
