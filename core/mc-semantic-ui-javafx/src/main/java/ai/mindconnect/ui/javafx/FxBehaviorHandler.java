package ai.mindconnect.ui.javafx;

import java.util.concurrent.CompletionStage;

/**
 * Implements one {@link ai.mindconnect.ui.model.UiTrigger.Behavior} — what
 * actually happens once the bus decided a trigger fired.
 *
 * <p>Registered by name in
 * {@link SuiFxEventBus#registerBehavior(String, FxBehaviorHandler)}; the name
 * is the {@code Behavior} enum constant. Apps override a built-in by
 * registering their own under the same name (e.g. swapping the plain
 * {@code APPLY_RESPONSE} HTTP client for one that carries auth headers).
 *
 * <p><b>Return a stage for async work.</b> The busy state — the spinner on the
 * clicked button, the global indicator — lasts exactly as long as the returned
 * {@link CompletionStage}. A behaviour that finishes synchronously returns
 * {@code null} and the busy state ends when {@code handle} returns:
 *
 * <pre>{@code
 * bus.registerBehavior("PATCH", ctx -> { applyPatch(...); return null; });
 * bus.registerBehavior("APPLY_RESPONSE", ctx -> httpClient.sendAsync(...));
 * }</pre>
 */
@FunctionalInterface
public interface FxBehaviorHandler {

    /**
     * @return a stage completing when the behaviour is really done, or
     *         {@code null} when it already is
     */
    CompletionStage<?> handle(FxTriggerContext ctx) throws Exception;
}
