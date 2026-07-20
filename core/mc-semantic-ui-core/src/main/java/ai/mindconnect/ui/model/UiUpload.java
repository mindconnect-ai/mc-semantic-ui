package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A drag-and-drop file-upload area. Renders a drop zone with a "browse" button
 * and a hidden {@code <input type="file">}; the browser-side {@code SuiEventBus}
 * handles both dropping files onto the zone and picking them via the button.
 *
 * <p>When files arrive, the bus fires {@link #onUpload}. The natural behaviour
 * is {@link UiTrigger.Behavior#UPLOAD} — the selected files are POSTed to the
 * trigger's URL as {@code multipart/form-data} (field name = {@link #name},
 * defaulting to the node id) and the response is applied as a
 * {@code UiPage} / {@code UiPatch}. It can also carry an
 * {@link UiTrigger.Behavior#INVOKE} trigger, in which case a client-side
 * handler receives the {@code File} objects directly — useful for a fully
 * client-side preview with no backend.
 *
 * <p>For a single inline control (no drop zone) use a {@link UiField} of type
 * {@link UiField.FieldType#FILE} instead.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiUpload extends UiNode {

    /** Caption shown above the drop zone. */
    private String label;
    /** Small helper text under the drop zone (e.g. "PNG or JPG, max 5 MB"). */
    private String hint;
    /**
     * Form field name the files are sent under (the multipart part name).
     * Defaults to the node id when null.
     */
    private String name;
    /**
     * Accepted file types — the HTML {@code accept} attribute
     * (e.g. {@code "image/*"} or {@code ".pdf,.docx"}). Null = any.
     */
    private String accept;
    /** Whether more than one file may be selected/dropped at once. */
    private boolean multiple;
    /** Label of the browse button. Defaults to "Browse…" in the renderer. */
    private String buttonLabel;
    /** Prompt text shown in the drop zone. Defaults to "Drag files here or" in the renderer. */
    private String dropText;
    /** Fired when files are dropped or picked. See the class docs for behaviours. */
    private UiTrigger onUpload;

    public static UiUpload of(String id, String label) {
        var u = new UiUpload();
        u.setId(id);
        u.label = label;
        return u;
    }

    // ── fluent setters ────────────────────────────────────────────────────

    public UiUpload hint(String hint)               { this.hint = hint; return this; }
    public UiUpload name(String name)               { this.name = name; return this; }
    public UiUpload accept(String accept)           { this.accept = accept; return this; }
    public UiUpload multiple()                      { this.multiple = true; return this; }
    public UiUpload buttonLabel(String label)       { this.buttonLabel = label; return this; }
    public UiUpload dropText(String text)           { this.dropText = text; return this; }

    /** Attach the upload behaviour. Usually {@link UiTrigger#upload(String)}. */
    public UiUpload onUpload(UiTrigger trigger)     { this.onUpload = trigger; return this; }

    /** Shortcut: multipart POST the files to {@code url}. */
    public UiUpload uploadTo(String url)            { this.onUpload = UiTrigger.upload(url); return this; }
}
