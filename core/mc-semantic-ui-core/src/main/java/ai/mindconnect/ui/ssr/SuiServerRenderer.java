package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPage;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side renderer: turns a {@link UiNode} / {@link UiPage} tree into an
 * HTML string using Handlebars templates. The server-side counterpart to the
 * TypeScript {@code SuiRenderer} — both consume the same node model, only the
 * serialisation differs (JSON for the SPA, HTML here).
 *
 * <h2>Template selection</h2>
 * One template per node type lives under {@code templates/sui/{type}.hbs} on
 * the classpath (e.g. {@code detail.hbs}, {@code table.hbs}). The renderer
 * picks the template by the node's {@code type} discriminator. Unknown types
 * fall back to a {@code <pre>} dump (mirrors the TS renderer's behaviour).
 *
 * <h2>Recursion</h2>
 * Templates render child nodes through the {@code {{{render child}}}} helper,
 * which calls back into this renderer — exactly like the TS handlers recurse
 * via {@code renderer.render(child)}. This avoids Handlebars partials and
 * keeps every node type a self-contained template.
 *
 * <h2>App override</h2>
 * Apps override individual templates by placing a file of the same name under
 * their own {@code classpath:/templates/sui/}. The app loader is consulted
 * before the core-JAR loader (see {@link #buildHandlebars}), so a single
 * overridden {@code table.hbs} wins while everything else falls through to the
 * bundled default.
 *
 * <p>Instances are thread-safe: compiled templates are cached in a
 * {@link ConcurrentHashMap} and Handlebars templates are immutable once
 * compiled.
 */
public class SuiServerRenderer {

    /** Where bundled default templates live inside this JAR. */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(SuiServerRenderer.class);

    private static final String CORE_PREFIX = "/templates/sui";
    /** Where an app drops overrides. Same prefix; classpath ordering decides. */
    private static final String TEMPLATE_SUFFIX = ".hbs";

    private final Handlebars handlebars;
    private final Map<String, Template> cache = new ConcurrentHashMap<>();
    /** Class → type-discriminator, resolved once from the Jackson annotations. */
    private final Map<Class<?>, String> typeByClass = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public SuiServerRenderer() {
        this(new ObjectMapper());
    }

    public SuiServerRenderer(ObjectMapper mapper) {
        this(mapper, List.of());
    }

    /**
     * @param contributors extra {@link SuiHelperContributor}s — typically
     *        supplied by Spring from the application context. They are applied
     *        after the ones found via {@link ServiceLoader}, and both run after
     *        the core helpers, so either can override a core helper by
     *        registering the same name.
     */
    public SuiServerRenderer(ObjectMapper mapper, List<SuiHelperContributor> contributors) {
        this.mapper = mapper;
        this.handlebars = buildHandlebars();
        seedCoreTypes();
        SuiHandlebarsHelpers.register(this.handlebars, this, this.mapper);
        applyContributors(contributors);
    }

    /**
     * Lets extension JARs add the helpers their own templates need. Without
     * this an extension can ship a {@code templates/sui/<type>.hbs} that
     * shadows the core's, but has no way to give it the maths it calls — which
     * is why extensions used to be browser-only.
     *
     * <p>A broken contributor is logged and skipped rather than taking the
     * whole renderer down: a missing helper degrades one node type, an
     * exception here would break every page.
     */
    private void applyContributors(List<SuiHelperContributor> extra) {
        var all = new java.util.ArrayList<SuiHelperContributor>();
        ServiceLoader.load(SuiHelperContributor.class).forEach(all::add);
        if (extra != null) all.addAll(extra);
        for (SuiHelperContributor c : all) {
            try {
                c.contribute(this.handlebars, this.mapper);
            } catch (RuntimeException e) {
                log.warn("SuiServerRenderer: helper contributor {} failed; skipping",
                        c.getClass().getName(), e);
            }
        }
    }

    /**
     * Pre-populates the class→type map from {@link UiNode}'s
     * {@link JsonSubTypes} list — the same annotation that drives JSON
     * serialisation, so there's no second source of truth. Extension node
     * classes (UiMarkdown, UiJsonViewer …) aren't in that list; they're
     * resolved lazily from their own {@code @JsonTypeName} in
     * {@link #typeOf(UiNode)}.
     */
    private void seedCoreTypes() {
        JsonSubTypes subTypes = UiNode.class.getAnnotation(JsonSubTypes.class);
        if (subTypes == null) return;
        for (JsonSubTypes.Type t : subTypes.value()) {
            typeByClass.put(t.value(), t.name());
        }
    }

    /**
     * The node's type discriminator — the string used both as the JSON
     * {@code type} property and as the template name. Resolves from the
     * core {@link JsonSubTypes} list first, then falls back to a
     * {@code @JsonTypeName} on the class itself (extension nodes).
     */
    private String typeOf(UiNode node) {
        return typeByClass.computeIfAbsent(node.getClass(), cls -> {
            var named = cls.getAnnotation(com.fasterxml.jackson.annotation.JsonTypeName.class);
            return named != null ? named.value() : null;
        });
    }

    /**
     * Builds the Handlebars engine with a composite loader: a consumer app's
     * {@code classpath:/templates/sui/} sits in front of the core JAR's copy,
     * so app overrides win and unoverridden types fall through to the bundled
     * default. Both loaders share the same prefix/suffix, so the same logical
     * name ({@code "table"}) resolves in either.
     */
    private Handlebars buildHandlebars() {
        // Single ClassPathTemplateLoader for now — both the bundled core
        // defaults AND any app overrides live at the same classpath prefix
        // (templates/sui/), and Spring Boot's flat classpath means the app's
        // resources shadow the JAR's automatically. A CompositeTemplateLoader
        // becomes useful only when we add a *different* source (filesystem,
        // remote, etc.), not just for stacking two classpath roots.
        TemplateLoader loader = new ClassPathTemplateLoader(CORE_PREFIX, TEMPLATE_SUFFIX);
        var hb = new Handlebars(loader);
        hb.setPrettyPrint(false);
        // Allow a partial to include itself. Handlebars' default guard rejects
        // any partial already on the include stack — but a self-recursive partial
        // over a finite tree (menu-button-item.hbs walking UiMenuItem.children)
        // terminates naturally. The data, not the guard, bounds the recursion.
        hb.setInfiniteLoops(true);
        return hb;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Renders a full page. The {@code page.hbs} template wraps the page's
     * root node; {@code navigate} is exposed so the template can emit a
     * {@code <meta>} / data-attribute for the client router if desired.
     */
    public String renderPage(UiPage page) {
        if (page == null) return "";
        Template t = resolve("page");
        if (t == null) throw new IllegalStateException("Missing core template: page.hbs");
        try {
            return t.apply(page);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render SUI page template", e);
        }
    }

    /**
     * Renders a single node to HTML. Unknown node types (no discriminator,
     * or no matching template) fall back to a {@code <pre>} JSON dump —
     * visible enough to catch a missing template in development without
     * crashing the request.
     */
    public String render(UiNode node) {
        if (node == null) return "";
        String type = typeOf(node);
        if (type == null) return fallback(node);
        Template t = resolve(type);
        if (t == null) return fallback(node);
        try {
            return t.apply(node);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render SUI template '" + type + "'", e);
        }
    }

    /**
     * Render entry-point used from inside templates via the {@code render}
     * helper. Accepts {@code Object} because Handlebars hands the helper
     * whatever the context expression evaluated to (often a {@link UiNode},
     * sometimes null for an absent child).
     */
    public String renderChild(Object node) {
        if (node instanceof UiNode n) return render(n);
        return "";
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /** Marks node types already probed and found to have no template. */
    private final java.util.Set<String> missing = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Compiles and caches the template for {@code name}, or returns
     * {@code null} when no such template exists on the classpath. Misses
     * are recorded in {@link #missing} so a missing template isn't probed
     * on every render.
     */
    private Template resolve(String name) {
        Template cached = cache.get(name);
        if (cached != null) return cached;
        if (missing.contains(name)) return null;
        try {
            Template t = handlebars.compile(name);
            cache.put(name, t);
            return t;
        } catch (IOException e) {
            missing.add(name);
            return null;
        }
    }

    private String fallback(UiNode node) {
        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            return "<pre>" + SuiHandlebarsHelpers.escapeHtml(json) + "</pre>";
        } catch (Exception e) {
            return "<pre>[unrenderable node: "
                    + SuiHandlebarsHelpers.escapeHtml(node.getClass().getSimpleName()) + "]</pre>";
        }
    }
}
