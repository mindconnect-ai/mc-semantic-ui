package ai.mindconnect.ui.editor.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone Spring Boot application that boots the SUI editor library.
 *
 * <p>Nothing fancy here — the library auto-configures itself via
 * {@code spring.factories}, so the only job of this class is to provide a
 * {@code main()} method and a Spring Boot application context. The default
 * port is set in {@code application.yaml} so the showcase runs alongside
 * the shop demo (9091) and any future siblings without colliding.
 */
@SpringBootApplication
public class SuiEditorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SuiEditorApplication.class, args);
    }
}
