package ai.mindconnect.ui.javafx.browser;

/**
 * Plain entry point.
 *
 * <p>A class with a {@code main} that does not itself extend
 * {@link javafx.application.Application} is what lets the app start from a
 * classpath jar rather than only from the module path — the same trick the
 * renderer module's demo launcher uses.
 */
public final class SuiBrowserLauncher {

    private SuiBrowserLauncher() {
    }

    public static void main(String[] args) {
        javafx.application.Application.launch(SuiBrowserApp.class, args);
    }
}
