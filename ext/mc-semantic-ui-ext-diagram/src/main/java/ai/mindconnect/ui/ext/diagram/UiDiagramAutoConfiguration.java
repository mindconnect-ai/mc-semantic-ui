package ai.mindconnect.ui.ext.diagram;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Spring-Boot auto-configuration: registers {@link UiDiagramModule} as a bean
 * whenever Jackson is on the classpath. Spring Boot's
 * {@code Jackson2ObjectMapperBuilder} picks up every {@code Module} bean from
 * the context and applies it to every managed {@code ObjectMapper}, so apps
 * that depend on this artefact don't need any additional wiring.
 *
 * <p>Discovered via {@code META-INF/spring/
 * org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class UiDiagramAutoConfiguration {

    @Bean
    public Module uiDiagramModule() {
        return new UiDiagramModule();
    }
}
