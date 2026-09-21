package ai.mindconnect.ui.ext.kanban;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Teaches an {@code ObjectMapper} the three node types of this extension:
 * {@code kanban}, {@code kanban-lane} and {@code kanban-card}. Public no-arg
 * constructor so {@code ServiceLoader} can find it — see
 * {@code META-INF/services/com.fasterxml.jackson.databind.Module}.
 *
 * <p>Spring consumers get it from {@link UiKanbanAutoConfiguration}; plain-Java
 * ones call {@code mapper.findAndRegisterModules()} or register it directly.
 */
public class UiKanbanModule extends SimpleModule {

    public UiKanbanModule() {
        super("UiKanbanModule");
        registerSubtypes(
                new NamedType(UiKanban.class, "kanban"),
                new NamedType(UiKanbanLane.class, "kanban-lane"),
                new NamedType(UiKanbanCard.class, "kanban-card"));
    }
}
