package ai.mindconnect.sui.demo.explorer;

import ai.mindconnect.ui.assets.SuiAsset;
import ai.mindconnect.ui.assets.SuiAssetContribution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The demo's own assets, contributed in code — the second way to declare
 * them, beside a jar's META-INF/sui/assets.json.
 *
 * <ul>
 *   <li>{@code explorer.css}, the demo's stylesheet — linked in every
 *       server-rendered head by the registry, where a filter used to write
 *       the tag by hand;</li>
 *   <li>{@code calendar.css} with order 10 — an override: the calendar jar
 *       declares {@code calendar.css} with the default order 0, and this
 *       declaration wins, so the page loads the demo's version, which imports
 *       the original and changes its accent.</li>
 * </ul>
 */
@Configuration
public class DemoAssets {

    @Bean
    public SuiAssetContribution explorerAssets() {
        return () -> List.of(
                SuiAsset.css("explorer.css", "/explorer.css"),
                SuiAsset.css("calendar.css", "/explorer-calendar.css").withOrder(10));
    }
}
