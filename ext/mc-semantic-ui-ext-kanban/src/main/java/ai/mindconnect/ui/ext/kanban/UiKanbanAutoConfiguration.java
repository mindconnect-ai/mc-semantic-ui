package ai.mindconnect.ui.ext.kanban;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Publishes the Jackson module that registers the kanban node types with
 * every Spring-managed ObjectMapper. The server-side templates need no helper
 * of their own — the core's {@code render}, {@code events} and {@code trigger}
 * helpers cover them — so this is the extension's only bean.
 */
@AutoConfiguration
public class UiKanbanAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UiKanbanModule.class)
    public UiKanbanModule uiKanbanModule() {
        return new UiKanbanModule();
    }
}
