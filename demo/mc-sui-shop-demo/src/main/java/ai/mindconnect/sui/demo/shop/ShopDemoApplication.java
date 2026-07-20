package ai.mindconnect.sui.demo.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SUI shop demo — a tiny product-admin app that exists to exercise the
 * server-side Handlebars renderer end-to-end.
 *
 * <p>Everything is JS-free: each interaction is a plain navigation
 * ({@code <a href>}) or a form post. The SUI core's
 * {@code SuiSsrAutoConfiguration} wires a {@code text/html} message converter
 * for {@code UiPage}, so the same controller methods could later serve JSON
 * to a SPA client without code changes.
 */
@SpringBootApplication
public class ShopDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopDemoApplication.class, args);
    }
}
