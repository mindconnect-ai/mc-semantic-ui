package ai.mindconnect.ui.assets;

import java.util.List;

/**
 * Assets a module adds in code rather than in {@code META-INF/sui/assets.json}
 * — for a list that depends on configuration, say. Publish one as a Spring
 * bean; the {@link SuiAssetRegistry} collects every one at startup.
 *
 * <pre>{@code
 * @Bean
 * SuiAssetContribution officeAssets() {
 *     return () -> List.of(
 *             SuiAsset.css("office.css", "/office/office.css"),
 *             SuiAsset.extension("office", "/office/extension.js"));
 * }
 * }</pre>
 */
@FunctionalInterface
public interface SuiAssetContribution {

    /** The assets this contribution declares. */
    List<SuiAsset> assets();
}
