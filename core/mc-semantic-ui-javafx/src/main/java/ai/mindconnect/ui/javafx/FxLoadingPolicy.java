package ai.mindconnect.ui.javafx;

/**
 * Decides whether a dispatch shows a loading indicator — the JavaFX twin of
 * the SPA's {@code LoadingPolicy}.
 *
 * <p>{@link #AUTO} (the default) marks every dispatch busy; {@link #MANUAL}
 * leaves it entirely to the app. Anything in between is a lambda over the
 * trigger context — e.g. "only for HTTP, never for local handlers":
 *
 * <pre>{@code
 * bus.setLoadingPolicy(ctx -> ctx.trigger().getBehavior() != UiTrigger.Behavior.INVOKE);
 * }</pre>
 */
@FunctionalInterface
public interface FxLoadingPolicy {

    /** Every dispatch shows the indicator. */
    FxLoadingPolicy AUTO = ctx -> true;

    /** No dispatch does; the app drives the indicator itself. */
    FxLoadingPolicy MANUAL = ctx -> false;

    boolean showLoading(FxTriggerContext ctx);
}
