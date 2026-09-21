package ai.mindconnect.ui.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The stylesheets, modules and extensions a page loads, gathered from every
 * jar on the classpath, from beans, and from registrations made at run time —
 * so a host no longer has to list its extensions by hand, and a module added
 * to the classpath (or installed from a marketplace) is on the page without
 * touching the host.
 *
 * <h2>Where assets come from</h2>
 * <ul>
 *   <li><b>Classpath</b> — every {@value #RESOURCE} found by the class loader,
 *       read once at startup: a JSON array of assets, or an object with an
 *       {@code assets} array.</li>
 *   <li><b>Beans</b> — every {@link SuiAssetContribution}, read once at startup.</li>
 *   <li><b>Run time</b> — {@link #register} and {@link #unregister}, for a
 *       marketplace. The host guards these with its own permissions; the
 *       registry only checks the asset.</li>
 * </ul>
 *
 * <h2>Resolving</h2>
 * Declarations are grouped by {@code id}. In each group the highest
 * {@code order} wins; a winner that is {@code disabled} takes the id off the
 * page. On a tie the run-time declaration beats a bean's, a bean's beats a
 * jar's, and between two of the same kind the one whose source sorts last
 * (jar URL, bean class) wins — deterministic from one start to the next, and
 * logged as a warning, because a tie is almost always an accident. The
 * resolved assets load in {@code order}, then by id.
 *
 * <h2>Icon sets</h2>
 * An asset of kind {@code icons} is an SVG sprite for the icon tokens that
 * start with its {@code prefix}. Two icon sets with the same prefix (under
 * different ids) compete like two declarations of one id: the higher
 * {@code order} wins, on a tie the id that sorts last, with a warning; the
 * loser is off the page. Different prefixes all stay, and a token resolves
 * from the longest one it starts with — {@link #iconSprites}. A token no
 * prefix matches keeps coming from the standard sprite.
 *
 * <h2>What is accepted</h2>
 * An {@code href} must be a path on this server: it starts with a single
 * {@code /} and carries no scheme, host, backslash, quote, angle bracket,
 * whitespace or control character. A file the page loads can run code, and
 * the registry will not point a page at another origin. A declaration that
 * fails the check is skipped at startup with a warning, and refused by
 * {@link #register} with an {@link IllegalArgumentException}.
 *
 * <h2>For the browser</h2>
 * {@link #moduleScript} is the ES module served as {@code /sui/assets.js}
 * ({@code installAll(renderer, bus)} and {@code linkStyles()}), and
 * {@link #headTags} the {@code <link>} tags for a head a server renders.
 * {@link #etag} changes with every change to the registry, so a browser
 * revalidating the module sees a new one at once.
 *
 * <p>Thread-safe: reads see a consistent snapshot; changes are serialised.
 */
public class SuiAssetRegistry {

    /** Where a jar declares its assets. */
    public static final String RESOURCE = "META-INF/sui/assets.json";

    private static final System.Logger LOG = System.getLogger(SuiAssetRegistry.class.getName());
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern PREFIX = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*-");
    private static final Pattern HREF = Pattern.compile("/(?![/\\\\])[^\\s\\\\\"'<>`\\x00-\\x1f\\x7f-\\x9f]{0,499}");
    private static final String TEMPLATE = "/ai/mindconnect/ui/assets/sui-assets.js";
    private static final String ASSETS_PLACEHOLDER = "/*SUI_ASSETS*/[]";
    private static final String GENERATION_PLACEHOLDER = "/*SUI_GENERATION*/0";

    /** Where a declaration came from — which also breaks a tie: a later kind wins. */
    public enum Source { CLASSPATH, BEAN, RUNTIME }

    /** One declaration and where it came from. */
    public record Declaration(SuiAsset asset, Source source, String origin) { }

    /** The outcome of resolving: the assets in load order, and what was worth a warning. */
    public record Resolution(List<SuiAsset> assets, List<String> warnings) {

        /** The icon sets among the assets, longest prefix first — one per prefix. */
        public List<SuiAsset> iconSets() {
            return byLongestPrefix(assets);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Declaration> fixed;
    private final Map<String, Declaration> runtime = new LinkedHashMap<>();
    private final String template;
    private volatile List<SuiAsset> resolved;
    private volatile long generation;

    /**
     * Reads every {@value #RESOURCE} {@code loader} can see, and the given
     * contributions.
     */
    public SuiAssetRegistry(ClassLoader loader, List<? extends SuiAssetContribution> contributions) {
        List<Declaration> declarations = new ArrayList<>(fromClasspath(loader));
        for (SuiAssetContribution contribution : contributions == null ? List.<SuiAssetContribution>of() : contributions) {
            String origin = contribution.getClass().getName();
            List<SuiAsset> assets = contribution.assets();
            for (SuiAsset asset : assets == null ? List.<SuiAsset>of() : assets) {
                accept(asset, Source.BEAN, origin, declarations);
            }
        }
        this.fixed = List.copyOf(declarations);
        this.template = readTemplate();
        this.resolved = resolveAndLog();
    }

    /** A registry over the class loader that loaded this class, with no contributions. */
    public static SuiAssetRegistry fromClasspath() {
        return new SuiAssetRegistry(SuiAssetRegistry.class.getClassLoader(), List.of());
    }

    // ── Reading ─────────────────────────────────────────────────────────────

    /** The resolved assets, in load order. */
    public List<SuiAsset> assets() {
        return resolved;
    }

    /** Every declaration the registry holds, winners and losers alike — for a diagnostics page. */
    public synchronized List<Declaration> declarations() {
        List<Declaration> all = new ArrayList<>(fixed);
        all.addAll(runtime.values());
        return Collections.unmodifiableList(all);
    }

    /**
     * The icon sets, longest prefix first: prefix → sprite URL under
     * {@code contextPath}. A token takes the first entry whose prefix it
     * starts with, or the standard sprite when none matches.
     */
    public Map<String, String> iconSprites(String contextPath) {
        Map<String, String> out = new LinkedHashMap<>();
        for (SuiAsset set : byLongestPrefix(resolved)) out.put(set.prefix(), url(contextPath, set.href()));
        return out;
    }

    /** Counts the changes made at run time; part of the {@link #etag}. */
    public long generation() {
        return generation;
    }

    // ── Changing at run time ────────────────────────────────────────────────

    /**
     * Adds a declaration, or replaces the run-time declaration of the same id.
     * It competes with the jars' and beans' declarations of that id by
     * {@code order} like any other — to replace a shipped asset, give it a
     * higher order; to take one off the page, register
     * {@link SuiAsset#disabled} with a higher order.
     *
     * @throws IllegalArgumentException if the id is malformed, or the asset
     *         is not disabled and has no kind or an {@code href} that is not
     *         a path on this server
     */
    public synchronized void register(SuiAsset asset) {
        String problem = problem(asset);
        if (problem != null) throw new IllegalArgumentException(problem);
        runtime.put(asset.id(), new Declaration(asset, Source.RUNTIME, "runtime"));
        changed();
    }

    /**
     * Removes the run-time declaration of {@code id}. The jars' and beans'
     * declarations stay — to take one of those off the page, register a
     * disabling declaration instead.
     *
     * @return whether there was one to remove
     */
    public synchronized boolean unregister(String id) {
        if (runtime.remove(id) == null) return false;
        changed();
        return true;
    }

    private void changed() {
        generation++;
        resolved = resolveAndLog();
    }

    // ── For the browser ─────────────────────────────────────────────────────

    /**
     * The {@code <link rel="stylesheet">} tags for every css asset, each
     * marked with {@code data-sui-asset} so the module's {@code linkStyles()}
     * leaves it alone. For a host that renders its own page head.
     *
     * @param contextPath the servlet context path ({@code ""} at the root)
     */
    public String headTags(String contextPath) {
        StringBuilder out = new StringBuilder();
        for (SuiAsset asset : resolved) {
            if (asset.kind() != SuiAsset.Kind.CSS) continue;
            out.append("<link rel=\"stylesheet\" href=\"").append(escape(url(contextPath, asset.href())))
                    .append("\" data-sui-asset=\"").append(escape(asset.id())).append("\">");
        }
        return out.toString();
    }

    /** {@link #headTags(String)} for an app at the root of its server. */
    public String headTags() {
        return headTags("");
    }

    /**
     * The resolved assets as the browser needs them: {@code id}, {@code kind},
     * {@code href} as declared, {@code url} under the context path, and
     * {@code order}. What {@code GET /sui/assets} returns.
     */
    public List<Map<String, Object>> describe(String contextPath) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SuiAsset asset : resolved) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", asset.id());
            row.put("kind", asset.kind().json());
            row.put("href", asset.href());
            row.put("url", url(contextPath, asset.href()));
            row.put("order", asset.order());
            if (asset.kind() == SuiAsset.Kind.ICONS) row.put("prefix", asset.prefix());
            out.add(row);
        }
        return out;
    }

    /**
     * The ES module served as {@code /sui/assets.js}: the resolved assets
     * baked in, with {@code installAll(renderer, bus)}, {@code linkStyles()}
     * and {@code assets} exported.
     */
    public String moduleScript(String contextPath) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : describe(contextPath)) {
            Map<String, Object> slim = new LinkedHashMap<>();
            slim.put("id", row.get("id"));
            slim.put("kind", row.get("kind"));
            slim.put("url", row.get("url"));
            if (row.containsKey("prefix")) slim.put("prefix", row.get("prefix"));
            rows.add(slim);
        }
        String json;
        try {
            // "<" escaped so the list cannot close a script element should
            // anyone ever inline the module.
            json = mapper.writeValueAsString(rows).replace("<", "\\u003c");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return template.replace(ASSETS_PLACEHOLDER, json)
                .replace(GENERATION_PLACEHOLDER, Long.toString(generation));
    }

    /**
     * An entity tag for a response built from the registry: changes with the
     * content and with every run-time change. {@code variant} tells two
     * responses apart ({@code "js"}, {@code "json"}).
     */
    public String etag(String contextPath, String variant) {
        String basis = variant + "\n" + generation + "\n" + moduleScript(contextPath);
        return "\"" + variant + "-" + generation + "-" + sha256(basis).substring(0, 20) + "\"";
    }

    // ── Checking ────────────────────────────────────────────────────────────

    /** Whether {@code href} is a path on this server: {@code /…}, no scheme, host or suspicious character. */
    public static boolean sameOrigin(String href) {
        return href != null && HREF.matcher(href).matches();
    }

    /** What is wrong with a declaration, or null when nothing is. */
    public static String problem(SuiAsset asset) {
        if (asset == null) return "no asset";
        if (asset.id() == null || !ID.matcher(asset.id()).matches()) {
            return "asset id must be letters, digits, '.', '_' or '-' (at most 100): " + asset.id();
        }
        if (asset.disabled()) return null;
        if (asset.kind() == null) return "asset " + asset.id() + " has no kind (css, module, extension or icons)";
        if (asset.kind() == SuiAsset.Kind.ICONS && (asset.prefix() == null || !PREFIX.matcher(asset.prefix()).matches()
                || asset.prefix().length() > 64)) {
            return "icon set " + asset.id() + " needs a prefix in lowercase-kebab ending in '-', e.g. \"brand-\": " + asset.prefix();
        }
        if (!sameOrigin(asset.href())) {
            return "asset " + asset.id() + " must load from this server — an href starting with a single '/': " + asset.href();
        }
        return null;
    }

    // ── Resolving ───────────────────────────────────────────────────────────

    /**
     * Picks one declaration per id and orders the winners. Pure: the same
     * declarations give the same result, whatever order they come in.
     */
    public static Resolution resolve(List<Declaration> declarations) {
        Map<String, List<Declaration>> byId = new LinkedHashMap<>();
        for (Declaration d : declarations) byId.computeIfAbsent(d.asset().id(), k -> new ArrayList<>()).add(d);
        Comparator<Declaration> rank = Comparator
                .comparingInt((Declaration d) -> d.asset().order())
                .thenComparing(d -> d.source().ordinal())
                .thenComparing(Declaration::origin);
        List<SuiAsset> winners = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (var group : byId.entrySet()) {
            List<Declaration> candidates = new ArrayList<>(group.getValue());
            candidates.sort(rank);
            Declaration winner = candidates.get(candidates.size() - 1);
            List<Declaration> tied = candidates.stream()
                    .filter(d -> d.asset().order() == winner.asset().order() && !d.asset().equals(winner.asset()))
                    .toList();
            if (!tied.isEmpty()) {
                StringBuilder w = new StringBuilder("sui asset '").append(group.getKey())
                        .append("' is declared more than once with order ").append(winner.asset().order()).append(": ");
                for (Declaration d : tied) w.append(describe(d)).append(", ");
                w.append("and ").append(describe(winner)).append(" — the last wins. Give one a higher order to decide.");
                warnings.add(w.toString());
            }
            if (!winner.asset().disabled()) winners.add(winner.asset());
        }
        dropShadowedIconSets(winners, warnings);
        winners.sort(Comparator.comparingInt(SuiAsset::order).thenComparing(SuiAsset::id));
        return new Resolution(List.copyOf(winners), List.copyOf(warnings));
    }

    /**
     * Of two icon sets with one prefix only one can serve it: the higher
     * order, on a tie the id that sorts last (with a warning). The other
     * leaves the page, as a losing declaration of an id does.
     */
    private static void dropShadowedIconSets(List<SuiAsset> winners, List<String> warnings) {
        Map<String, List<SuiAsset>> byPrefix = new LinkedHashMap<>();
        for (SuiAsset a : winners) {
            if (a.kind() == SuiAsset.Kind.ICONS) byPrefix.computeIfAbsent(a.prefix(), k -> new ArrayList<>()).add(a);
        }
        Comparator<SuiAsset> rank = Comparator.comparingInt(SuiAsset::order).thenComparing(SuiAsset::id);
        for (var group : byPrefix.entrySet()) {
            List<SuiAsset> sets = group.getValue();
            if (sets.size() < 2) continue;
            sets.sort(rank);
            SuiAsset winner = sets.get(sets.size() - 1);
            List<SuiAsset> losers = sets.subList(0, sets.size() - 1);
            List<SuiAsset> tied = losers.stream().filter(a -> a.order() == winner.order()).toList();
            if (!tied.isEmpty()) {
                StringBuilder w = new StringBuilder("sui icon sets ");
                for (SuiAsset a : tied) w.append('\'').append(a.id()).append("', ");
                w.append("and '").append(winner.id()).append("' all serve prefix '").append(group.getKey())
                        .append("' with order ").append(winner.order())
                        .append(" — '").append(winner.id()).append("' wins. Give one a higher order to decide.");
                warnings.add(w.toString());
            }
            winners.removeAll(losers);
        }
    }

    /** The icon sets among {@code assets}, longest prefix first, then by prefix. */
    private static List<SuiAsset> byLongestPrefix(List<SuiAsset> assets) {
        return assets.stream()
                .filter(a -> a.kind() == SuiAsset.Kind.ICONS)
                .sorted(Comparator.comparingInt((SuiAsset a) -> -a.prefix().length()).thenComparing(SuiAsset::prefix))
                .toList();
    }

    private static String describe(Declaration d) {
        return d.asset().id() + " → " + (d.asset().disabled() ? "disabled" : d.asset().href())
                + " (" + d.source().name().toLowerCase() + " " + d.origin() + ")";
    }

    private List<SuiAsset> resolveAndLog() {
        List<Declaration> all = new ArrayList<>(fixed);
        all.addAll(runtime.values());
        Resolution resolution = resolve(all);
        for (String warning : resolution.warnings()) LOG.log(System.Logger.Level.WARNING, warning);
        return resolution.assets();
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    private List<Declaration> fromClasspath(ClassLoader loader) {
        List<Declaration> out = new ArrayList<>();
        if (loader == null) return out;
        List<URL> urls;
        try {
            urls = Collections.list(loader.getResources(RESOURCE));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "cannot list " + RESOURCE + " on the classpath", e);
            return out;
        }
        urls.sort(Comparator.comparing(URL::toString));
        for (URL url : urls) {
            String origin = url.toString();
            try (InputStream in = url.openStream()) {
                JsonNode root = mapper.readTree(in);
                JsonNode list = root != null && root.isObject() ? root.get("assets") : root;
                if (list == null || !list.isArray()) {
                    LOG.log(System.Logger.Level.WARNING, origin + ": expected an array of assets, or {\"assets\": [...]}");
                    continue;
                }
                for (JsonNode node : list) {
                    SuiAsset asset;
                    try {
                        asset = mapper.treeToValue(node, SuiAsset.class);
                    } catch (IOException | IllegalArgumentException e) {
                        LOG.log(System.Logger.Level.WARNING, origin + ": skipping an asset that cannot be read: "
                                + node + " — " + firstLine(e.getMessage()));
                        continue;
                    }
                    accept(asset, Source.CLASSPATH, origin, out);
                }
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, origin + ": cannot be read, skipped — " + firstLine(e.getMessage()));
            }
        }
        return out;
    }

    private static void accept(SuiAsset asset, Source source, String origin, List<Declaration> into) {
        String problem = problem(asset);
        if (problem != null) {
            LOG.log(System.Logger.Level.WARNING, origin + ": skipped — " + problem);
            return;
        }
        into.add(new Declaration(asset, source, origin));
    }

    private static String readTemplate() {
        try (InputStream in = SuiAssetRegistry.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) throw new IllegalStateException("missing " + TEMPLATE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── Small helpers ───────────────────────────────────────────────────────

    private static String firstLine(String message) {
        if (message == null) return "";
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }

    private static String url(String contextPath, String href) {
        String ctx = contextPath == null ? "" : contextPath;
        if (ctx.endsWith("/")) ctx = ctx.substring(0, ctx.length() - 1);
        return ctx + href;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
