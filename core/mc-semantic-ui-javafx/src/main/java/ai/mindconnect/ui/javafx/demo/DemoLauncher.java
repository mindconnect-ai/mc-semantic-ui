package ai.mindconnect.ui.javafx.demo;

import javafx.application.Application;

/**
 * Entry point for {@link DemoApplication}.
 *
 * <p>It exists purely to be a main class that does <em>not</em> extend
 * {@link Application}. When the JVM's launcher sees an {@code Application}
 * subclass as the main class, it insists on {@code javafx.graphics} being a
 * named module on the module path and otherwise aborts with "JavaFX runtime
 * components are missing" — which is exactly what happens when you run the
 * demo from an IDE or with a plain {@code java -cp}, since this module puts
 * JavaFX on the classpath like any other dependency.
 *
 * <p>Launching through a non-Application class sidesteps that check, and
 * JavaFX then starts fine off the classpath. The only trace is a harmless
 * "classes were loaded from unnamed module" warning.
 *
 * <p>So: run <b>this</b> class, not {@link DemoApplication}.
 */
public final class DemoLauncher {

    private DemoLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(DemoApplication.class, args);
    }
}
