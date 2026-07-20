package ai.mindconnect.ui.ext.markdown;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link UiMarkdownModule} as a bean whenever Jackson is on the
 * classpath. Discovered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class UiMarkdownAutoConfiguration {

    @Bean
    public Module uiMarkdownModule() {
        return new UiMarkdownModule();
    }
}
