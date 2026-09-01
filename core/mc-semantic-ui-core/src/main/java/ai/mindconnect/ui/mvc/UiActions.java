package ai.mindconnect.ui.mvc;

import ai.mindconnect.ui.model.UiTrigger;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Builds a {@link UiTrigger} from the controller method it should call,
 * instead of from a URL written out by hand.
 *
 * <pre>{@code
 * import static ai.mindconnect.ui.mvc.UiActions.trigger;
 * import static org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder.on;
 *
 * UiAction.danger("delete", "Delete")
 *         .onClick(trigger(on(AgentController.class).delete(agent.id())));
 * }</pre>
 *
 * <p>{@code on(...)} records the call on a proxy; both the path and the HTTP
 * verb are then read off the handler's own {@code @RequestMapping}. What a
 * string cannot do: the compiler checks the arguments, renaming the handler
 * updates every caller, and "go to definition" lands on the code that runs.
 *
 * <p><b>Scope.</b> This is for ACTIONS — the POSTs and DELETEs behind buttons.
 * Page addresses stay literal: they are bookmarked, linked and read by humans,
 * and deriving them buys nothing.
 *
 * <p><b>Two constraints</b> come from the recording proxy. The controller
 * class and the called method must not be {@code final}, and the return type
 * must be proxyable — a {@code String}-returning handler (a view-name forward)
 * cannot be referenced this way. Both are navigation endpoints in practice,
 * which keep their literal URLs anyway.
 *
 * <p>Spring MVC is an <i>optional</i> dependency of this module. Using this
 * class requires it on the classpath; nothing else in the model does.
 */
public final class UiActions {

    private UiActions() {
    }

    /**
     * Stands in for a value the CLIENT fills in, not the server: pass it where
     * a table row supplies its own id, and the rendered URL keeps the literal
     * {@code {id}} placeholder that {@link ai.mindconnect.ui.model.UiTable}'s
     * row actions substitute per row.
     *
     * <pre>{@code
     * .rowAction(UiAction.secondary("view", "View")
     *         .onClick(trigger(on(ToolController.class).view(agentId, ROW_ID))));
     * }</pre>
     *
     * <p>A sentinel rather than a raw string, so the argument still type-checks
     * in the position it belongs to.
     */
    public static final UUID ROW_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    /** What {@link #ROW_ID} becomes in the rendered URL. */
    private static final String ROW_ID_PLACEHOLDER = "{id}";

    /** The trigger for a recorded call, e.g. {@code on(X.class).delete(id)}. */
    public static UiTrigger trigger(Object recordedCall) {
        return UiTrigger.api(verbOf(recordedCall), urlOf(recordedCall));
    }

    /**
     * Same, but the form named by {@code payloadNodeId} travels as the body.
     * Spell the form out rather than relying on the event bus inferring the
     * enclosing one — a control outside its form would otherwise submit
     * whatever encloses it, or nothing.
     */
    public static UiTrigger trigger(Object recordedCall, String payloadNodeId) {
        return UiTrigger.api(verbOf(recordedCall), urlOf(recordedCall), payloadNodeId);
    }

    /**
     * The SSE variant: same derivation, but the trigger streams its response
     * instead of applying it in one go — for handlers returning an emitter.
     */
    public static UiTrigger streaming(Object recordedCall, String payloadNodeId) {
        return UiTrigger.stream(verbOf(recordedCall), urlOf(recordedCall), payloadNodeId);
    }

    private static String urlOf(Object recordedCall) {
        // The baseUrl overload on purpose: the no-arg variant reads the current
        // request from RequestContextHolder and throws on any thread without
        // one — which is exactly where a streaming response renders.
        //
        // encode() matters too: a path or query value carrying a space or a
        // reserved character would otherwise land raw in the URL. Callers that
        // spell URLs out have to remember URLEncoder themselves, and the ones
        // that forget break only on the ids nobody thinks to try.
        String url = MvcUriComponentsBuilder
                .fromMethodCall(UriComponentsBuilder.newInstance(), recordedCall)
                .build().encode().toUriString();
        // Put the placeholder back AFTER encoding, so its braces survive.
        return url.replace(ROW_ID.toString(), ROW_ID_PLACEHOLDER);
    }

    /** The handler's own mapping decides the verb; an unannotated one is a GET. */
    private static String verbOf(Object recordedCall) {
        Method method = ((MvcUriComponentsBuilder.MethodInvocationInfo) recordedCall).getControllerMethod();
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        return mapping != null && mapping.method().length > 0 ? mapping.method()[0].name() : "GET";
    }
}
