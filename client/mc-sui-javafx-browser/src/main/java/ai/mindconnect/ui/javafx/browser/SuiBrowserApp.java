package ai.mindconnect.ui.javafx.browser;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The window around {@link SuiBrowser}.
 *
 * <p>A start url may come from the command line or from {@code -Dsui.url};
 * without one the window comes up empty and waits for the address bar, the way
 * a browser opened on a blank tab does.
 *
 * <pre>{@code
 * mvn -f client/mc-sui-javafx-browser/pom.xml javafx:run \
 *     -Djavafx.args=http://localhost:8080/ui
 * }</pre>
 */
public class SuiBrowserApp extends Application {

    /** System property for the start url, for launchers that pass no arguments. */
    public static final String START_URL_PROPERTY = "sui.url";

    @Override
    public void start(Stage stage) {
        var browser = new SuiBrowser();

        stage.setTitle("Semantic UI — JavaFX browser");
        stage.setScene(new Scene(browser, 1100, 760));
        stage.show();

        var start = startUrl(getParameters() == null ? null : getParameters().getRaw());
        if (start != null) browser.go(start);
    }

    /** The first argument wins, then {@code -Dsui.url}, then nothing. */
    static String startUrl(java.util.List<String> args) {
        if (args != null && !args.isEmpty() && !args.get(0).isBlank()) return args.get(0);
        var property = System.getProperty(START_URL_PROPERTY);
        return property == null || property.isBlank() ? null : property;
    }
}
