package ai.mindconnect.ui.ext.jsonviewer;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson module that teaches an {@code ObjectMapper} about the
 * {@link UiJsonViewer} subtype of {@code UiNode}. Has a public no-arg
 * constructor so it can be picked up via Java's {@code ServiceLoader} —
 * see {@code META-INF/services/com.fasterxml.jackson.databind.Module}.
 *
 * <p>Spring-Boot consumers don't need to register this directly: the
 * {@link UiJsonViewerAutoConfiguration} exposes it as a {@code @Bean}, and
 * Spring Boot wires every {@code Module} bean into every Spring-managed
 * {@code ObjectMapper}. Plain-Java consumers can call
 * {@code mapper.findAndRegisterModules()} or instantiate this class directly.
 */
public class UiJsonViewerModule extends SimpleModule {

    public UiJsonViewerModule() {
        super("UiJsonViewerModule");
        registerSubtypes(new NamedType(UiJsonViewer.class, "json-viewer"));
    }
}
