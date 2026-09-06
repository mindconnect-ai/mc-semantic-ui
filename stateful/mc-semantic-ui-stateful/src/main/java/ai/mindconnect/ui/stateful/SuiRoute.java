package ai.mindconnect.ui.stateful;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The page address a {@link SuiView} answers to.
 *
 * <pre>{@code
 * @SuiRoute("/orders")
 * public class OrdersView extends SuiView<OrdersView.State> { … }
 * }</pre>
 *
 * <p>A {@code GET} on the route opens a fresh instance of the view and
 * redirects (or, in the SPA, navigates) to the same route with the instance
 * id appended as a query parameter; from then on the instance is addressed by
 * that id. Routes are literal paths — no path variables in this version.
 * Anything the view needs from the address arrives as query parameters
 * through {@link RouteParams}.
 *
 * <p>Under Spring Boot the view classes are found by scanning the
 * application's packages for this annotation; a view needs no
 * {@code @Component}. Without Spring, register the class on the
 * {@link SuiViewRegistry} by hand.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SuiRoute {

    /** The path, e.g. {@code "/orders"}. Must start with a slash. */
    String value();
}
