package ai.mindconnect.ui.stateful.spring;

import ai.mindconnect.ui.stateful.SuiViewEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * Registers the controller's two handlers with Spring MVC once every bean
 * exists: a {@code GET} for each route the registry knows, and the
 * {@code POST} that every listener's trigger points at.
 */
public class SuiViewMappings implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SuiViewMappings.class);

    private final RequestMappingHandlerMapping mapping;
    private final SuiViewController controller;
    private final SuiViewEngine engine;

    public SuiViewMappings(RequestMappingHandlerMapping mapping, SuiViewController controller,
                           SuiViewEngine engine) {
        this.mapping = mapping;
        this.controller = controller;
        this.engine = engine;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            Method show = SuiViewController.class.getMethod("show", HttpServletRequest.class);
            Method event = SuiViewController.class.getMethod("event",
                    String.class, String.class, String.class, HttpServletRequest.class);
            var options = mapping.getBuilderConfiguration();

            String eventPath = engine.settings().eventBasePath() + "/{id}/{node}/{event}";
            mapping.registerMapping(RequestMappingInfo.paths(eventPath)
                    .methods(RequestMethod.POST).options(options).build(), controller, event);

            engine.registry().routes().forEach((route, view) -> {
                mapping.registerMapping(RequestMappingInfo.paths(route)
                        .methods(RequestMethod.GET).options(options).build(), controller, show);
                log.info("stateful view {} at GET {}", view.getSimpleName(), route);
            });
            log.info("stateful events at POST {}", eventPath);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
