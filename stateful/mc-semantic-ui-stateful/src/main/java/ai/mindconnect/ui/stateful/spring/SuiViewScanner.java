package ai.mindconnect.ui.stateful.spring;

import ai.mindconnect.ui.stateful.SuiRoute;
import ai.mindconnect.ui.stateful.SuiViewRegistry;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.List;

/** Finds every {@link SuiRoute}-annotated class under the given packages and registers it. */
public final class SuiViewScanner {

    private SuiViewScanner() {
    }

    public static SuiViewRegistry scan(List<String> basePackages, ClassLoader classLoader) {
        var registry = new SuiViewRegistry();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(SuiRoute.class));
        for (String pkg : basePackages) {
            for (BeanDefinition bd : scanner.findCandidateComponents(pkg)) {
                try {
                    registry.register(ClassUtils.forName(bd.getBeanClassName(), classLoader));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("view class vanished during scan: " + bd.getBeanClassName(), e);
                }
            }
        }
        return registry;
    }
}
