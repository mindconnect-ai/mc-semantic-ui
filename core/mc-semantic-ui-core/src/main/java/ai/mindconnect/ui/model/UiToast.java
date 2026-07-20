package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Transient notification shown to the user — typically anchored to a corner
 * of the viewport, auto-dismissed after a few seconds.
 *
 * <p>Not a {@link UiNode}: toasts ride <em>alongside</em> the page content
 * via {@link UiPage#getToasts()} (or {@link UiPatch#getToasts()} for patch
 * responses) so the server can attach feedback to any response without
 * touching the main UI tree. The renderer surfaces them through the
 * platform-specific path:
 *
 * <ul>
 *   <li><strong>SSR</strong>: {@code UiPageHtmlMessageConverter} emits a
 *       {@code <div class="sui-toast-container">} at the end of the body
 *       and a tiny inline script auto-dismisses each toast after its
 *       {@link #durationMs} elapses.</li>
 *   <li><strong>SPA</strong>: the {@code SuiEventBus} appends rendered toast
 *       nodes to a persistent body-level container so they survive page
 *       swaps; the auto-dismiss timer is JS-driven.</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiToast {

    /**
     * Visual severity. Drives the {@code .sui-toast--…} CSS modifier and the
     * default icon glyph. Mirrors the conventional info/success/warn/error
     * scale used by most web frameworks.
     */
    public enum Level { INFO, SUCCESS, WARN, ERROR }

    /** Default auto-dismiss timeout for toasts that don't override it. */
    public static final int DEFAULT_DURATION_MS = 4000;

    private Level level;
    /** Short headline. Optional — when null the toast is body-only. */
    private String title;
    /** Required body text. Single line in practice; long messages get truncated by CSS. */
    private String message;
    /**
     * Auto-dismiss timeout in milliseconds. Use {@code 0} to mean
     * "stays until the user clicks it away" — the renderer treats any
     * non-positive value as sticky. Defaults to {@link #DEFAULT_DURATION_MS}.
     */
    private int durationMs = DEFAULT_DURATION_MS;

    // ── factories ─────────────────────────────────────────────────────────

    public static UiToast info(String message)    { return of(Level.INFO,    null, message); }
    public static UiToast success(String message) { return of(Level.SUCCESS, null, message); }
    public static UiToast warn(String message)    { return of(Level.WARN,    null, message); }
    public static UiToast error(String message)   { return of(Level.ERROR,   null, message); }

    public static UiToast of(Level level, String title, String message) {
        var t = new UiToast();
        t.level = level;
        t.title = title;
        t.message = message;
        return t;
    }

    // ── fluent setters ────────────────────────────────────────────────────

    public UiToast title(String title)      { this.title = title; return this; }
    public UiToast durationMs(int ms)        { this.durationMs = ms; return this; }
    /** Marks the toast as sticky (no auto-dismiss). Sugar for {@code durationMs(0)}. */
    public UiToast sticky()                  { this.durationMs = 0; return this; }
}
