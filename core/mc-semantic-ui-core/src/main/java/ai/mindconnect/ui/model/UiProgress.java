package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A progress indicator — a horizontal {@link Variant#BAR} or a
 * {@link Variant#CIRCLE} ring showing how far along a task is.
 *
 * <p>Set {@link #value} (against {@link #max}, default 100) for determinate
 * progress; leave {@code value} null for an <em>indeterminate</em> animation
 * (the CSS animates a sliding chunk / a spinning ring) when you know work is
 * happening but not how much is left.
 *
 * <p>{@link #status} tints the fill (success / warning / error) so a progress
 * bar can double as a result state once the task finishes. {@link #showValue}
 * toggles the trailing {@code NN%} readout.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiProgress extends UiNode {

    public enum Variant { BAR, CIRCLE }

    public enum Status { NORMAL, SUCCESS, WARNING, ERROR }

    /** Current progress. Null renders an indeterminate (looping) animation. */
    private Double value;

    /** Upper bound for {@link #value}. Defaults to 100 when null. */
    private Double max;

    /** Bar (default) or circular ring. */
    private Variant variant;

    /** Colour intent of the fill. Defaults to {@link Status#NORMAL}. */
    private Status status;

    /** Whether to show the {@code NN%} text. Defaults to true when null. */
    private Boolean showValue;

    public static UiProgress of(double value) {
        var p = new UiProgress();
        p.value = value;
        return p;
    }

    public static UiProgress of(double value, double max) {
        var p = of(value);
        p.max = max;
        return p;
    }

    /** An indeterminate progress indicator (value unknown). */
    public static UiProgress indeterminate() {
        return new UiProgress();
    }

    public UiProgress variant(Variant variant) {
        this.variant = variant;
        return this;
    }

    public UiProgress status(Status status) {
        this.status = status;
        return this;
    }

    public UiProgress showValue(boolean showValue) {
        this.showValue = showValue;
        return this;
    }
}
