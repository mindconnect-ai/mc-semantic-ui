package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// Core subtypes are listed here for self-documentation. Extension subtypes
// (e.g. UiMarkdown, UiJsonViewer) register themselves via Jackson
// SimpleModule + ServiceLoader/Spring auto-config from the
// extension JARs (ext/*) — no edits to this list needed for them.
@JsonSubTypes({
    @JsonSubTypes.Type(value = UiAppShell.class,     name = "app-shell"),
    @JsonSubTypes.Type(value = UiSection.class,      name = "section"),
    @JsonSubTypes.Type(value = UiSectionEntry.class, name = "section-entry"),
    @JsonSubTypes.Type(value = UiStack.class,        name = "stack"),
    @JsonSubTypes.Type(value = UiForm.class,      name = "form"),
    @JsonSubTypes.Type(value = UiTable.class,     name = "table"),
    @JsonSubTypes.Type(value = UiColumn.class,    name = "column"),
    @JsonSubTypes.Type(value = UiRow.class,       name = "row"),
    @JsonSubTypes.Type(value = UiText.class,      name = "text"),
    @JsonSubTypes.Type(value = UiIcon.class,      name = "icon"),
    @JsonSubTypes.Type(value = UiSpinner.class,   name = "spinner"),
    @JsonSubTypes.Type(value = UiProgress.class,  name = "progress"),
    @JsonSubTypes.Type(value = UiList.class,      name = "list"),
    @JsonSubTypes.Type(value = UiTree.class,      name = "tree"),
    @JsonSubTypes.Type(value = UiTreeNode.class,  name = "tree-node"),
    @JsonSubTypes.Type(value = UiMenu.class,       name = "menu"),
    @JsonSubTypes.Type(value = UiMenuItem.class,   name = "menu-item"),
    @JsonSubTypes.Type(value = UiMenuButton.class, name = "menu-button"),
    @JsonSubTypes.Type(value = UiDetail.class,    name = "detail"),
    @JsonSubTypes.Type(value = UiHeader.class,    name = "header"),
    @JsonSubTypes.Type(value = UiAction.class,    name = "action"),
    @JsonSubTypes.Type(value = UiField.class,     name = "field"),
    @JsonSubTypes.Type(value = UiFieldGroup.class, name = "fieldgroup"),
    @JsonSubTypes.Type(value = UiUpload.class,    name = "upload"),
    @JsonSubTypes.Type(value = UiLink.class,      name = "link"),
    @JsonSubTypes.Type(value = UiDialog.class,    name = "dialog"),
    @JsonSubTypes.Type(value = UiPage.class,      name = "page"),
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class UiNode {
    private String id;
    private String title;
    private String cssClass;

    /**
     * What happens when this node is clicked. Declared here, so <em>every</em>
     * node type has it — a {@link UiStack}, a {@link UiText} or a node you
     * invented can respond to a click without being an action.
     *
     * <p>For a {@link UiAction} this is also the no-JS contract: the server
     * renderer turns the trigger into a real {@code <a href>} or {@code <form>}
     * so the button works without JavaScript.
     *
     * <p>The nearest handler wins. A click is offered to the built-in handlers
     * first (action, link, tab, menu); a container's {@code onClick} only fires
     * when none of them was responsible — so a button inside a clickable row
     * keeps its own behaviour.
     */
    private UiTrigger onClick;

    /** Double-click. */
    private UiTrigger onDblClick;

    /**
     * Pointer enters the node. Delegated via {@code mouseover}, and moves
     * between the node's own children do not re-fire it.
     */
    private UiTrigger onHover;

    /** Pointer leaves the node. Same filtering as {@link #onHover}. */
    private UiTrigger onLeave;

    /**
     * A value was committed — a select picked, a checkbox toggled, an input
     * blurred. On a {@link UiField} this is the idiomatic place for it; on a
     * container it catches changes from anything inside.
     */
    private UiTrigger onChange;

    /** A value changed as the user types. */
    private UiTrigger onInput;

    @SuppressWarnings("unchecked")
    public <T extends UiNode> T withCssClass(String cssClass) {
        this.cssClass = cssClass;
        return (T) this;
    }
}
