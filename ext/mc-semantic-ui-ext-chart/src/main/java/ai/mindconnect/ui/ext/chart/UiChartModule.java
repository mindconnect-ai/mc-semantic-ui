package ai.mindconnect.ui.ext.chart;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Teaches an {@code ObjectMapper} about the {@link UiChart} subtype of
 * {@code UiNode}. Public no-arg constructor so {@code ServiceLoader} can find
 * it — see {@code META-INF/services/com.fasterxml.jackson.databind.Module}.
 *
 * <p>Spring consumers get it from {@link UiChartAutoConfiguration}; plain-Java
 * ones call {@code mapper.findAndRegisterModules()} or register it directly.
 *
 * <p>This is why the chart node lives here rather than in the core: the core
 * has no way to draw it, and a node type the core cannot render is a promise
 * it can't keep. Same shape as the diagram extension.
 */
public class UiChartModule extends SimpleModule {

    public UiChartModule() {
        super("UiChartModule");
        registerSubtypes(new NamedType(UiChart.class, "chart"));
    }
}
