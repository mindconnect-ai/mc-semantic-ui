package ai.mindconnect.ui.javafx;

/**
 * Where an {@link FxClientHandler} runs.
 *
 * <p>{@link #BACKGROUND} is the default and the right answer almost always: a
 * local handler is real application code — it hits a repository, a file, a
 * service — and doing that on the JavaFX thread freezes the window. Running it
 * off-thread also makes the busy indicator meaningful, because the dispatch
 * now genuinely lasts as long as the work.
 *
 * <p>{@link #FX} is the opt-out for handlers that only touch the scene graph
 * and want to skip the thread hop.
 */
public enum FxHandlerThread {

    /** Runs on the bus's background pool. The default. */
    BACKGROUND,

    /** Runs inline on the JavaFX application thread. */
    FX
}
