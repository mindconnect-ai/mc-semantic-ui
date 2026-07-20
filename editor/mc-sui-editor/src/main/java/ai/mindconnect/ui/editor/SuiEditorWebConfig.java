package ai.mindconnect.ui.editor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Maps the configured editor base path (default {@code /editor}) onto the
 * statically-bundled {@code editor.html} shell so the host app doesn't need
 * to write a forwarding controller. Apps can change the path via
 * {@code mindconnect.sui.editor.base-path}, e.g. to embed the editor under
 * {@code /admin/editor} without owning a single line of routing code.
 *
 * <p>Two paths are registered:
 * <ul>
 *   <li>The base path itself (e.g. {@code /editor}) → forwards to
 *       {@code /sui-editor/editor.html}.</li>
 *   <li>{@code basePath + "/"} for the trailing-slash variant, which
 *       browsers occasionally produce.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
public class SuiEditorWebConfig implements WebMvcConfigurer {

    private final String basePath;

    public SuiEditorWebConfig(@Value("${mindconnect.sui.editor.base-path:/editor}") String basePath) {
        this.basePath = basePath;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // forward:/sui-editor/editor.html keeps the URL the user typed (e.g.
        // /editor) while serving the static HTML shell. A redirect would
        // expose /sui-editor/* to the address bar, which we'd rather hide.
        registry.addViewController(basePath).setViewName("forward:/sui-editor/editor.html");
        registry.addViewController(basePath + "/").setViewName("forward:/sui-editor/editor.html");
    }
}
