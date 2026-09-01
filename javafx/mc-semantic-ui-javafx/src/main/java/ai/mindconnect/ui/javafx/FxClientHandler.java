package ai.mindconnect.ui.javafx;

/**
 * A local, in-process "endpoint" invoked by the {@code INVOKE} behaviour —
 * the rich-client equivalent of the SPA's {@code registerClientHandler}.
 *
 * <p>This is what makes a JavaFX app out of the vocabulary: a trigger built
 * with {@code UiTrigger.invoke("saveCustomer", "customer-form")} calls a plain
 * Java method with the form's collected values, with no server in between.
 *
 * <pre>{@code
 * bus.registerClientHandler("saveCustomer", ctx -> {
 *     Map<String, Object> values = ctx.payload();
 *     repository.save(Customer.from(values));
 *     ctx.bus().applyPatch(UiPatch.of().toast(UiToast.success("Saved")));
 * });
 * }</pre>
 *
 * <p><b>Handlers run on a background thread</b> ({@link FxHandlerThread}). A
 * local handler is real application code — it hits a repository, a file, a
 * service — and running that on the JavaFX thread would freeze the window. It
 * also makes the busy indicator honest: the button stays busy for exactly as
 * long as the handler takes.
 *
 * <p>The bus methods a handler normally reaches for are safe from any thread:
 * {@link SuiFxEventBus#applyPatch}, {@link SuiFxEventBus#toast},
 * {@link SuiFxEventBus#dispatch} and {@link SuiFxEventBus#showDialog} all get
 * themselves onto the JavaFX thread. Touching the scene graph <em>directly</em>
 * from a handler is not safe — either go through the model, or register the
 * handler with {@link FxHandlerThread#FX}.
 */
@FunctionalInterface
public interface FxClientHandler {

    /** @param ctx trigger, source node and resolved payload */
    void handle(FxTriggerContext ctx) throws Exception;
}
