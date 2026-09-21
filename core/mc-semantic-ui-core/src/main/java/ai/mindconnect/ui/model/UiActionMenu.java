package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A button that opens a menu, placed wherever an action goes — chiefly in a
 * {@link UiForm}'s button bar, in the order it was added:
 *
 * <pre>{@code
 * UiForm.of("compose", "New mail")
 *     .action(UiAction.primary("send", "Send").onClick(…))
 *     .action(UiAction.menu("ai", "AI",
 *             UiMenuItem.of("draft", "Draft with AI").onClick(UiTrigger.api("POST", "/ai/draft", "compose")),
 *             UiMenuItem.divider(),
 *             UiMenuItem.heading("Quick actions"),
 *             UiMenuItem.of("shorten", "Shorten").onClick(UiTrigger.api("POST", "/ai/shorten", "compose")))
 *         .icon("sparkles"));
 * }</pre>
 *
 * <p>It looks like the other buttons ({@link UiAction#getStyle() style},
 * {@link UiAction#getIcon() icon}, label) with a small caret, and opens like a
 * {@link UiMenuButton}: above the button when there is no room below, kept
 * inside the window, driven by the keyboard. An entry whose trigger names the
 * form as its payload — or, inside a form's button bar, names nothing — sends
 * the form's fields, like any button of the form.
 *
 * <p>Its own {@code onClick} is not used: the button opens the menu, the
 * entries act. {@code enabled = false} keeps the menu shut.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiActionMenu extends UiAction {

    /** The menu entries: items, {@link UiMenuItem#divider() dividers} and {@link UiMenuItem#heading headings}. */
    private List<UiMenuItem> items = new ArrayList<>();

    // ── fluent builders (covariant overrides so chaining stays UiActionMenu) ──

    @Override
    public UiActionMenu icon(String iconToken) {
        super.icon(iconToken);
        return this;
    }

    @Override
    public UiActionMenu style(Style style) {
        super.style(style);
        return this;
    }

    /** Shown, but shut: the menu does not open, {@code reason} is its tooltip. */
    @Override
    public UiActionMenu disabled(String reason) {
        super.disabled(reason);
        return this;
    }

    @Override
    public UiActionMenu enabledIf(boolean condition, String disabledReason) {
        super.enabledIf(condition, disabledReason);
        return this;
    }

    /** Adds an entry. */
    public UiActionMenu item(UiMenuItem item) {
        items.add(item);
        return this;
    }
}
