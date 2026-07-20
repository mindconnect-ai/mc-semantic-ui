package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Standalone icon node — an icon anywhere a {@link UiNode} is accepted: a
 * {@code UiStack} child, a tree/list {@code labelNode}, a table
 * {@code cellTemplate}. The convenience {@code icon} string on
 * {@link UiAction}/{@link UiField}/{@link UiLink}/… covers the common
 * leading-icon case; this node covers free placement.
 *
 * <p><b>Swappable by design.</b> {@link #name} is only a token — a stable
 * semantic alias ({@code "delete"}, {@code "success"}) or a raw sprite id
 * ({@code "trash-2"}). What it renders to is decided by the icon layer
 * (default: an SVG {@code <use>} into the curated sprite at
 * {@code /sui/icons.svg}); apps can point at a different sprite, inline SVG,
 * or an icon font without touching any UiNode. A legacy emoji passed as the
 * name is rendered verbatim.
 *
 * <p>Colour follows {@code currentColor} and size follows the surrounding
 * font (1em). The inherited {@code title} makes the icon accessible
 * ({@code role="img"} + label); without it the icon is decorative.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiIcon extends UiNode {

    /** Icon token: semantic alias ({@code "delete"}) or raw sprite id ({@code "trash-2"}). */
    private String name;

    public static UiIcon of(String name) {
        var i = new UiIcon();
        i.name = name;
        return i;
    }

    public static UiIcon of(String id, String name) {
        var i = of(name);
        i.setId(id);
        return i;
    }

    /** Set the accessible label via the inherited {@code title} (fluent). */
    public UiIcon labelled(String title) {
        setTitle(title);
        return this;
    }
}
