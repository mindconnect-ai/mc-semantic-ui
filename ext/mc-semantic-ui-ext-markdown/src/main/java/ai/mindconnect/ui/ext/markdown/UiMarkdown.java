package ai.mindconnect.ui.ext.markdown;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Renderer node for rich-text content authored as Markdown. Lives in its own
 * module because the renderer relies on a third-party Markdown library
 * ({@code marked}) that is loaded lazily on demand — pulling that dependency
 * into the generic core vocabulary would have made the simplest possible app
 * pay for it.
 *
 * <p>Wire shape: {@code {"type": "markdown", "id": "...", "content": "..."}}.
 * The subtype is registered with Jackson via {@link UiMarkdownModule}, picked
 * up automatically by Spring Boot through {@link UiMarkdownAutoConfiguration}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeName("markdown")
public class UiMarkdown extends UiNode {

    private String content;

    public static UiMarkdown of(String id, String content) {
        var m = new UiMarkdown();
        m.setId(id);
        m.content = content;
        return m;
    }
}
