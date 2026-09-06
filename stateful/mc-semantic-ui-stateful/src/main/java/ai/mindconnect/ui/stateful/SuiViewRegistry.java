package ai.mindconnect.ui.stateful;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The views an application has, by route. Filled from the classpath under
 * Spring Boot, by hand otherwise. Also the only place a stored class name is
 * ever turned into a class: an instance record names its view, and the name
 * is looked up here rather than loaded.
 */
public final class SuiViewRegistry {

    private final Map<String, Class<? extends SuiView<?>>> byRoute = new LinkedHashMap<>();
    private final Map<String, Class<? extends SuiView<?>>> byName = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public synchronized SuiViewRegistry register(Class<?> viewClass) {
        if (!SuiView.class.isAssignableFrom(viewClass)) {
            throw new IllegalArgumentException(viewClass.getName() + " does not extend SuiView");
        }
        String route = SuiView.routeOf(viewClass);
        if (!route.startsWith("/")) {
            throw new IllegalArgumentException("@SuiRoute on " + viewClass.getName() + " must start with '/'");
        }
        var existing = byRoute.get(route);
        if (existing != null && existing != viewClass) {
            throw new IllegalStateException("route " + route + " is claimed by both "
                    + existing.getName() + " and " + viewClass.getName());
        }
        var cls = (Class<? extends SuiView<?>>) viewClass;
        byRoute.put(route, cls);
        byName.put(viewClass.getName(), cls);
        return this;
    }

    public synchronized Optional<Class<? extends SuiView<?>>> byRoute(String route) {
        return Optional.ofNullable(byRoute.get(route));
    }

    /** A registered view by class name — a name that is not registered is not resolved. */
    public synchronized Optional<Class<? extends SuiView<?>>> byName(String className) {
        return Optional.ofNullable(byName.get(className));
    }

    public synchronized Map<String, Class<? extends SuiView<?>>> routes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(byRoute));
    }
}
