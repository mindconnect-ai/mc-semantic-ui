package ai.mindconnect.ui.stateful.spring;

import ai.mindconnect.ui.stateful.SuiViewEngine;
import ai.mindconnect.ui.stateful.SuiViewRegistry;
import ai.mindconnect.ui.stateful.ViewFactory;
import ai.mindconnect.ui.stateful.store.InMemoryViewStateStore;
import ai.mindconnect.ui.stateful.store.ViewStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * Wires the stateful mode into a Spring Boot web application:
 * {@link SuiRoute}-annotated views are found under the application's
 * packages, an in-memory store is provided unless the app defines a
 * {@link ViewStateStore} bean, and the routes plus the event endpoint are
 * mapped. Off with {@code mindconnect.sui.stateful.enabled=false}.
 *
 * <p>HTML rendering of a view's {@code GET} is the core's SSR converter —
 * enable {@code mindconnect.sui.ssr.enabled=true} and have handlebars on the
 * classpath, exactly as for a stateless page.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "mindconnect.sui.stateful", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SuiStatefulProperties.class)
public class SuiStatefulAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ViewStateStore suiViewStateStore() {
        return new InMemoryViewStateStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public SuiViewRegistry suiViewRegistry(BeanFactory beanFactory) {
        List<String> packages = AutoConfigurationPackages.has(beanFactory)
                ? AutoConfigurationPackages.get(beanFactory) : List.of();
        return SuiViewScanner.scan(packages, getClass().getClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean
    public ViewFactory suiViewFactory(AutowireCapableBeanFactory beanFactory) {
        return new SpringViewFactory(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public SuiViewEngine suiViewEngine(ObjectMapper mapper, SuiViewRegistry registry, ViewFactory factory,
                                       ViewStateStore store, SuiStatefulProperties properties) {
        return new SuiViewEngine(mapper, registry, factory, store, properties.toSettings());
    }

    @Bean
    @ConditionalOnMissingBean
    public SuiViewController suiViewController(SuiViewEngine engine, ObjectMapper mapper) {
        return new SuiViewController(engine, mapper);
    }

    @Bean
    public SuiViewMappings suiViewMappings(RequestMappingHandlerMapping requestMappingHandlerMapping,
                                           SuiViewController controller, SuiViewEngine engine) {
        return new SuiViewMappings(requestMappingHandlerMapping, controller, engine);
    }

    @Bean
    public InstanceReaper suiInstanceReaper(SuiViewEngine engine, SuiStatefulProperties properties) {
        return new InstanceReaper(engine, properties.getReapInterval());
    }
}
