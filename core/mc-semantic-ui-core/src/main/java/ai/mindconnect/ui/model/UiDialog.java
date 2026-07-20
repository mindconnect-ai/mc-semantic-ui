package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A modal dialog overlay — the body ({@link #node}) is shown on top of a page's
 * main content. Dialogs live in {@link UiPage#getDialogs()} and render into a
 * body-level {@code #sui-dialogs} host; opening one later is an {@code APPEND}
 * of this node into that host, closing it a {@code REMOVE} by {@code id}. The
 * node renders itself (via {@code dialog.hbs} / {@code renderers/dialog.ts})
 * as a fixed-position {@code .sui-dialog-host}, so its place in the tree does
 * not affect where it appears on screen.
 *
 * <p><b>Why a UiNode?</b> Same reason as {@link UiSectionEntry}: a "wrapper"
 * with no {@code type} discriminator can't be selected, edited or deleted with
 * the same code path as everything else in the visual editor. Making the dialog
 * a first-class UiNode ({@code type: "dialog"}) means it is "just a tree node"
 * — the editor can compose several by id and a patch can open/close them.
 *
 * <p>{@code title} (the header) and {@code id} are inherited from {@link UiNode}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiDialog extends UiNode {

    /**
     * URL the close button navigates to in SSR mode (the dialog is gone after
     * the redirect). The SPA path also uses it as the navigate target so
     * reload-after-close lands on a consistent page; {@code null} means the SPA
     * just removes the overlay and stays put.
     */
    private String closeHref;

    /** The dialog's body — anything that renders as a {@link UiNode}. */
    private UiNode node;

    public static UiDialog of(String title, String closeHref, UiNode node) {
        var d = new UiDialog();
        d.setTitle(title);
        d.closeHref = closeHref;
        d.node = node;
        return d;
    }
}
