package ai.mindconnect.ui.editor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configures the SUI editor library when present on a Spring Boot
 * host's classpath.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link NodeRegistry} — the catalogue of editable node types.</li>
 *   <li>{@link InMemoryEditorContentStore} as the default
 *       {@link EditorContentStore}. Host apps that want JPA / file / S3
 *       persistence register their own bean; the {@code @ConditionalOnMissingBean}
 *       gate steps aside.</li>
 *   <li>{@link EditorRestController} for the {@code /editor/api/*} routes.</li>
 * </ul>
 *
 * <p>Bundled static assets land under {@code /sui-editor/*} automatically
 * because the JAR ships them at {@code META-INF/resources/sui-editor/}.
 * The user-facing path ({@code /editor}) is mapped by
 * {@link SuiEditorWebConfig}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@Import({EditorRestController.class, ProjectsRestController.class})
public class SuiEditorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NodeRegistry suiNodeRegistry() {
        return new NodeRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public EditorContentStore suiEditorContentStore() {
        return new InMemoryEditorContentStore();
    }

    /**
     * The multi-project store behind {@link ProjectsRestController}. In-memory
     * by default; a host that wants durable projects (JPA, file, S3, …)
     * registers its own {@link ProjectRepository} bean and this steps aside.
     */
    @Bean
    @ConditionalOnMissingBean
    public ProjectRepository suiProjectRepository() {
        return new InMemoryProjectRepository();
    }
}
