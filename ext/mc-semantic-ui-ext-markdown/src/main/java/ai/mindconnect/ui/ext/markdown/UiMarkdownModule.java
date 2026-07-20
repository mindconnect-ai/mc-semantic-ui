package ai.mindconnect.ui.ext.markdown;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson module that teaches an {@code ObjectMapper} about the
 * {@link UiMarkdown} subtype of {@code UiNode}. Public no-arg constructor so
 * Java's {@code ServiceLoader} can pick it up via
 * {@code META-INF/services/com.fasterxml.jackson.databind.Module} — Spring
 * Boot reads it from the bean exposed by {@link UiMarkdownAutoConfiguration}.
 */
public class UiMarkdownModule extends SimpleModule {

    public UiMarkdownModule() {
        super("UiMarkdownModule");
        registerSubtypes(new NamedType(UiMarkdown.class, "markdown"));
    }
}
