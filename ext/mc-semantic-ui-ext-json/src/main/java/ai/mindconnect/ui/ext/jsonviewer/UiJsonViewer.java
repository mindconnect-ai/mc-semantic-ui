package ai.mindconnect.ui.ext.jsonviewer;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Renderer node for displaying large JSON payloads with IDE-style folding.
 * Lives in its own module — <em>not</em> in {@code mc-semantic-ui-core} —
 * because the renderer depends on the third-party {@code andypf-json-viewer}
 * web component, which is an implementation detail rather than part of the
 * generic semantic UI vocabulary. A host that wants a JSON viewer takes this
 * artifact alone and pulls in nothing else.
 *
 * <p>Wire shape: {@code {"type": "json-viewer", "id": "...", "json": "..."}}.
 * The subtype is registered with Jackson via {@link UiJsonViewerModule}, which
 * Spring Boot picks up automatically through
 * {@link UiJsonViewerAutoConfiguration}; non-Spring consumers can call
 * {@code mapper.findAndRegisterModules()} to register it via {@code ServiceLoader}.
 *
 * <p>The matching front-end handler lives at
 * {@code src/main/ts/jsonviewer/extension.ts} in the same module and is loaded
 * by the host page via {@code import("/sui-ext/jsonviewer/extension.js")}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("json-viewer")
public class UiJsonViewer extends UiNode {

    /** Verbatim JSON string. The viewer parses it client-side. */
    private String json;

    /**
     * Initial expansion depth: 0 = all collapsed, 1 = top-level visible,
     * 99 = effectively all expanded. Null leaves the web component's own
     * default in place.
     */
    private Integer expandLevel;

    /** Theme passed straight to the web component (e.g. {@code default-light}). */
    private String theme;

    public static UiJsonViewer of(String id, String json) {
        var v = new UiJsonViewer();
        v.setId(id);
        v.json = json;
        return v;
    }

    public UiJsonViewer expandLevel(int level) {
        this.expandLevel = level;
        return this;
    }

    public UiJsonViewer theme(String theme) {
        this.theme = theme;
        return this;
    }
}
