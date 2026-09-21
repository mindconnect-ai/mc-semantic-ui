package ai.mindconnect.ui.assets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Publishes the {@link SuiAssetRegistry} — every {@code META-INF/sui/assets.json}
 * on the classpath and every {@link SuiAssetContribution} bean — and, in a
 * servlet web app, the endpoints {@code /sui/assets} and {@code /sui/assets.js}.
 *
 * <p>On unless {@code mindconnect.sui.assets.enabled=false}. An app that
 * wants a registry of its own defines a {@link SuiAssetRegistry} bean.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mindconnect.sui.assets.enabled", havingValue = "true", matchIfMissing = true)
public class SuiAssetsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SuiAssetRegistry suiAssetRegistry(ResourceLoader resourceLoader,
                                             ObjectProvider<SuiAssetContribution> contributions) {
        ClassLoader loader = resourceLoader.getClassLoader() != null
                ? resourceLoader.getClassLoader() : SuiAssetRegistry.class.getClassLoader();
        return new SuiAssetRegistry(loader, contributions.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    public SuiAssetsController suiAssetsController(SuiAssetRegistry registry) {
        return new SuiAssetsController(registry);
    }
}
