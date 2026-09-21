package ai.mindconnect.ui.assets;

import ai.mindconnect.ui.assets.SuiAssetRegistry.Declaration;
import ai.mindconnect.ui.assets.SuiAssetRegistry.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The asset registry: what it reads from jars and beans, how it picks one
 * declaration per id, what it refuses, and what it hands the browser.
 */
class SuiAssetRegistryTest {

    @TempDir
    Path tmp;

    /** A jar holding one META-INF/sui/assets.json with {@code json}. */
    private URL jar(String name, String json) throws IOException {
        Path file = tmp.resolve(name + ".jar");
        try (OutputStream out = Files.newOutputStream(file); JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry(SuiAssetRegistry.RESOURCE));
            jar.write(json.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return file.toUri().toURL();
    }

    private static List<String> ids(SuiAssetRegistry registry) {
        return registry.assets().stream().map(a -> a.id() + "@" + a.href()).toList();
    }

    // ── Classpath ───────────────────────────────────────────────────────────

    @Test
    void aJarOnTheClasspathIsReadWithNoFurtherConfiguration() throws Exception {
        URL calendar = jar("calendar", """
                [{"id": "calendar.css", "kind": "css", "href": "/sui-ext/calendar/calendar.css"},
                 {"id": "calendar", "kind": "extension", "href": "/sui-ext/calendar/extension.js"}]""");
        try (var loader = new URLClassLoader(new URL[]{calendar}, null)) {
            var registry = new SuiAssetRegistry(loader, List.of());
            assertEquals(List.of("calendar@/sui-ext/calendar/extension.js", "calendar.css@/sui-ext/calendar/calendar.css"), ids(registry));
            assertEquals("<link rel=\"stylesheet\" href=\"/sui-ext/calendar/calendar.css\" data-sui-asset=\"calendar.css\">",
                    registry.headTags());
        }
    }

    @Test
    void anObjectWithAnAssetsArrayWorksToo() throws Exception {
        URL a = jar("a", "{\"assets\": [{\"id\": \"a\", \"kind\": \"module\", \"href\": \"/a.js\"}]}");
        try (var loader = new URLClassLoader(new URL[]{a}, null)) {
            assertEquals(List.of("a@/a.js"), ids(new SuiAssetRegistry(loader, List.of())));
        }
    }

    @Test
    void aSecondJarWithTheSameIdAndAHigherOrderReplacesTheFirst() throws Exception {
        URL shipped = jar("shipped", "[{\"id\": \"calendar\", \"kind\": \"extension\", \"href\": \"/sui-ext/calendar/extension.js\"}]");
        URL plugin = jar("plugin", "[{\"id\": \"calendar\", \"kind\": \"extension\", \"href\": \"/plugin/calendar.js\", \"order\": 10}]");
        // Both orders of the jars on the classpath give the same answer.
        for (URL[] urls : new URL[][]{{shipped, plugin}, {plugin, shipped}}) {
            try (var loader = new URLClassLoader(urls, null)) {
                assertEquals(List.of("calendar@/plugin/calendar.js"), ids(new SuiAssetRegistry(loader, List.of())));
            }
        }
    }

    @Test
    void aDisablingDeclarationWithAHigherOrderTakesTheAssetOffThePage() throws Exception {
        URL shipped = jar("shipped", """
                [{"id": "kanban", "kind": "extension", "href": "/sui-ext/kanban/extension.js"},
                 {"id": "kanban.css", "kind": "css", "href": "/sui-ext/kanban/kanban.css"}]""");
        URL host = jar("host", "[{\"id\": \"kanban\", \"disabled\": true, \"order\": 1}]");
        try (var loader = new URLClassLoader(new URL[]{shipped, host}, null)) {
            assertEquals(List.of("kanban.css@/sui-ext/kanban/kanban.css"), ids(new SuiAssetRegistry(loader, List.of())));
        }
    }

    @Test
    void brokenDeclarationsAreSkippedAndTheRestStays() throws Exception {
        URL mixed = jar("mixed", """
                [{"id": "ok", "kind": "css", "href": "/ok.css"},
                 {"id": "evil", "kind": "extension", "href": "https://evil.example/x.js"},
                 {"id": "nokind", "href": "/x.js"},
                 {"id": "odd", "kind": "stylesheet", "href": "/x.css"},
                 {"kind": "css", "href": "/noid.css"}]""");
        URL garbage = jar("garbage", "not json");
        try (var loader = new URLClassLoader(new URL[]{mixed, garbage}, null)) {
            assertEquals(List.of("ok@/ok.css"), ids(new SuiAssetRegistry(loader, List.of())));
        }
    }

    // ── Resolving ───────────────────────────────────────────────────────────

    @Test
    void assetsLoadByOrderThenId() {
        var r = SuiAssetRegistry.resolve(List.of(
                new Declaration(SuiAsset.extension("b", "/b.js").withOrder(5), Source.CLASSPATH, "x"),
                new Declaration(SuiAsset.extension("c", "/c.js"), Source.CLASSPATH, "x"),
                new Declaration(SuiAsset.css("a", "/a.css"), Source.CLASSPATH, "x"),
                new Declaration(SuiAsset.module("z", "/z.js").withOrder(-1), Source.CLASSPATH, "x")));
        assertEquals(List.of("z", "a", "c", "b"), r.assets().stream().map(SuiAsset::id).toList());
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void aTieIsResolvedTheSameWayEveryTimeAndWarnedAbout() {
        var fromJarA = new Declaration(SuiAsset.css("theme", "/a/theme.css"), Source.CLASSPATH, "jar:a");
        var fromJarB = new Declaration(SuiAsset.css("theme", "/b/theme.css"), Source.CLASSPATH, "jar:b");
        var fromBean = new Declaration(SuiAsset.css("theme", "/bean/theme.css"), Source.BEAN, "com.example.Theme");
        List<Declaration> all = new ArrayList<>(List.of(fromJarA, fromJarB, fromBean));
        for (int round = 0; round < 6; round++) {
            Collections.shuffle(all);
            var r = SuiAssetRegistry.resolve(all);
            // A bean beats a jar on a tie; between jars the later origin wins.
            assertEquals("/bean/theme.css", r.assets().get(0).href());
            assertEquals(1, r.warnings().size());
            assertTrue(r.warnings().get(0).contains("theme"), r.warnings().get(0));
        }
        var jarsOnly = SuiAssetRegistry.resolve(List.of(fromJarB, fromJarA));
        assertEquals("/b/theme.css", jarsOnly.assets().get(0).href());
    }

    @Test
    void theSameDeclarationTwiceIsNoTie() {
        var once = new Declaration(SuiAsset.css("x", "/x.css"), Source.CLASSPATH, "jar:a");
        var twice = new Declaration(SuiAsset.css("x", "/x.css"), Source.CLASSPATH, "jar:b");
        assertTrue(SuiAssetRegistry.resolve(List.of(once, twice)).warnings().isEmpty());
    }

    // ── Beans and run time ──────────────────────────────────────────────────

    @Test
    void aBeanContributesAndCanOverride() throws Exception {
        URL shipped = jar("shipped", "[{\"id\": \"calendar.css\", \"kind\": \"css\", \"href\": \"/sui-ext/calendar/calendar.css\"}]");
        SuiAssetContribution theme = () -> List.of(
                SuiAsset.css("calendar.css", "/theme/calendar.css").withOrder(10),
                SuiAsset.module("analytics", "/theme/analytics.js"));
        try (var loader = new URLClassLoader(new URL[]{shipped}, null)) {
            var registry = new SuiAssetRegistry(loader, List.of(theme));
            assertEquals(List.of("analytics@/theme/analytics.js", "calendar.css@/theme/calendar.css"), ids(registry));
        }
    }

    @Test
    void registeringAtRunTimeReplacesAndUnregisteringRestores() throws Exception {
        URL shipped = jar("shipped", "[{\"id\": \"chart\", \"kind\": \"extension\", \"href\": \"/sui-ext/chart/extension.js\"}]");
        try (var loader = new URLClassLoader(new URL[]{shipped}, null)) {
            var registry = new SuiAssetRegistry(loader, List.of());
            String js0 = registry.etag("", "js"), json0 = registry.etag("", "json");

            registry.register(SuiAsset.extension("chart", "/market/chart-pro.js").withOrder(100));
            assertEquals(List.of("chart@/market/chart-pro.js"), ids(registry));
            assertEquals(1, registry.generation());
            String js1 = registry.etag("", "js");
            assertNotEquals(js0, js1);
            assertNotEquals(json0, registry.etag("", "json"));
            assertTrue(registry.moduleScript("").contains("/market/chart-pro.js"));

            registry.register(SuiAsset.disabled("chart", 200));
            assertEquals(List.of(), ids(registry));

            assertTrue(registry.unregister("chart"));
            assertFalse(registry.unregister("chart"));
            assertEquals(List.of("chart@/sui-ext/chart/extension.js"), ids(registry));
            assertNotEquals(js1, registry.etag("", "js"));
            // Same content as at the start, but a browser must still revalidate after a change.
            assertNotEquals(js0, registry.etag("", "js"));
        }
    }

    @Test
    void onlyPathsOnThisServerAreAccepted() {
        var registry = new SuiAssetRegistry(null, List.of());
        for (String bad : new String[]{"https://evil.example/x.js", "//evil.example/x.js", "/\\evil.example/x.js",
                "javascript:alert(1)", "data:text/javascript,alert(1)", "x.js", "../x.js", "", " /x.js",
                "/x.js\"><script>", "/x js", "/x.js\n", "/x.js'"}) {
            assertFalse(SuiAssetRegistry.sameOrigin(bad), bad);
            assertThrows(IllegalArgumentException.class, () -> registry.register(SuiAsset.module("m", bad)), bad);
        }
        for (String ok : new String[]{"/x.js", "/sui-ext/calendar/extension.js", "/a/b.css?v=2", "/a/../b.js"}) {
            assertTrue(SuiAssetRegistry.sameOrigin(ok), ok);
        }
        assertThrows(IllegalArgumentException.class, () -> registry.register(SuiAsset.module("bad id!", "/x.js")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new SuiAsset("k", null, "/x.js", 0, false)));
        assertEquals(0, registry.generation(), "a refused registration changes nothing");
    }

    // ── For the browser ─────────────────────────────────────────────────────

    @Test
    void theModuleCarriesTheAssetsUnderTheContextPath() {
        var registry = new SuiAssetRegistry(null, List.<SuiAssetContribution>of(() -> List.of(
                SuiAsset.css("c.css", "/c.css"), SuiAsset.extension("e", "/e.js"))));
        String js = registry.moduleScript("/admin");
        assertTrue(js.contains("const ASSETS = [{\"id\":\"c.css\",\"kind\":\"css\",\"url\":\"/admin/c.css\"},"
                + "{\"id\":\"e\",\"kind\":\"extension\",\"url\":\"/admin/e.js\"}];"), js);
        assertTrue(js.contains("export async function installAll(renderer, bus, options = {})"), js);
        assertFalse(js.contains("/*SUI_ASSETS*/"), js);
        assertTrue(registry.headTags("/admin/").contains("href=\"/admin/c.css\""));
        assertEquals("/admin/e.js", registry.describe("/admin").get(1).get("url"));
        assertNotEquals(registry.etag("", "js"), registry.etag("/admin", "js"));
    }

    // ── Icon sets ───────────────────────────────────────────────────────────

    private static ai.mindconnect.ui.assets.SuiAsset icons(String id, String prefix, String href, int order) {
        return SuiAsset.icons(id, prefix, href).withOrder(order);
    }

    @Test
    void twoJarsWithAnIconSetEachBothRenderAndTheStandardIconsToo() throws Exception {
        URL brand = jar("brand", "[{\"id\": \"brand-icons\", \"kind\": \"icons\", \"prefix\": \"brand-\", \"href\": \"/sui-ext/brand/brand-icons.svg\"}]");
        URL acme = jar("acme", "[{\"id\": \"acme-icons\", \"kind\": \"icons\", \"prefix\": \"acme-\", \"href\": \"/sui-ext/acme/acme-icons.svg\"}]");
        try (var loader = new URLClassLoader(new URL[]{brand, acme}, null)) {
            var registry = new SuiAssetRegistry(loader, List.of());
            assertEquals(java.util.Map.of("brand-", "/sui-ext/brand/brand-icons.svg", "acme-", "/sui-ext/acme/acme-icons.svg"),
                    registry.iconSprites(""));
            assertEquals(List.of("brand-", "acme-"), List.copyOf(registry.iconSprites("").keySet()), "longest prefix first");
            assertEquals("/app/sui-ext/acme/acme-icons.svg", registry.iconSprites("/app").get("acme-"));

            ai.mindconnect.ui.ssr.IconRenderer.setIconSets(() -> registry.iconSprites(""));
            try {
                assertTrue(ai.mindconnect.ui.ssr.IconRenderer.render("brand-microsoft").contains("/sui-ext/brand/brand-icons.svg#brand-microsoft"));
                assertTrue(ai.mindconnect.ui.ssr.IconRenderer.render("acme-rocket").contains("/sui-ext/acme/acme-icons.svg#acme-rocket"));
                assertTrue(ai.mindconnect.ui.ssr.IconRenderer.render("trash").contains("/sui/icons.svg#trash"));
            } finally {
                ai.mindconnect.ui.ssr.IconRenderer.setIconSets(null);
            }

            // The browser gets the prefix with each set, nothing else changes.
            String module = registry.moduleScript("");
            assertTrue(module.contains("{\"id\":\"brand-icons\",\"kind\":\"icons\",\"url\":\"/sui-ext/brand/brand-icons.svg\",\"prefix\":\"brand-\"}"), module);
            assertTrue(registry.describe("").stream().allMatch(row -> row.containsKey("prefix")));
        }
    }

    @Test
    void theLongestPrefixWins() {
        var registry = new SuiAssetRegistry(null, List.<SuiAssetContribution>of(() -> List.of(
                SuiAsset.icons("acme", "acme-", "/acme.svg"),
                SuiAsset.icons("acme-logos", "acme-logo-", "/logos.svg"))));
        assertEquals(List.of("acme-logo-", "acme-"), List.copyOf(registry.iconSprites("").keySet()));
        ai.mindconnect.ui.ssr.IconRenderer.setIconSets(() -> registry.iconSprites(""));
        try {
            assertEquals("/logos.svg", ai.mindconnect.ui.ssr.IconRenderer.spriteUrlFor("acme-logo-main"));
            assertEquals("/acme.svg", ai.mindconnect.ui.ssr.IconRenderer.spriteUrlFor("acme-logout"));
            assertEquals("/sui/icons.svg", ai.mindconnect.ui.ssr.IconRenderer.spriteUrlFor("acme"));
        } finally {
            ai.mindconnect.ui.ssr.IconRenderer.setIconSets(null);
        }
    }

    @Test
    void oneSetPerPrefixTheHigherOrderThenTheLastIdWins() {
        var byOrder = SuiAssetRegistry.resolve(List.of(
                new Declaration(icons("brand-a", "brand-", "/a.svg", 5), Source.BEAN, "x"),
                new Declaration(icons("brand-b", "brand-", "/b.svg", 0), Source.BEAN, "x")));
        assertEquals(List.of("brand-a"), byOrder.assets().stream().map(SuiAsset::id).toList());
        assertTrue(byOrder.warnings().isEmpty(), byOrder.warnings().toString());

        var tie = SuiAssetRegistry.resolve(List.of(
                new Declaration(icons("brand-b", "brand-", "/b.svg", 0), Source.BEAN, "x"),
                new Declaration(icons("brand-a", "brand-", "/a.svg", 0), Source.BEAN, "x")));
        assertEquals(List.of("brand-b"), tie.iconSets().stream().map(SuiAsset::id).toList());
        assertEquals(1, tie.warnings().size());
        assertTrue(tie.warnings().get(0).contains("prefix 'brand-'"), tie.warnings().get(0));
    }

    @Test
    void aDisabledIconSetLeavesItsTokensToTheStandardSprite() throws Exception {
        var registry = new SuiAssetRegistry(null, List.<SuiAssetContribution>of(() -> List.of(SuiAsset.icons("brand-icons", "brand-", "/brand.svg"))));
        assertEquals(1, registry.iconSprites("").size());
        registry.register(SuiAsset.disabled("brand-icons", 10));
        assertTrue(registry.iconSprites("").isEmpty());
        assertFalse(registry.moduleScript("").contains("brand-icons"));
        registry.unregister("brand-icons");
        assertEquals("/brand.svg", registry.iconSprites("").get("brand-"));
    }

    @Test
    void anIconSetNeedsAKebabPrefixEndingInADash() {
        for (String bad : new String[]{null, "", "brand", "Brand-", "brand--", "-", "1brand-", "brand-\"x-"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SuiAssetRegistry.fromClasspath().register(SuiAsset.icons("b", bad, "/b.svg")), String.valueOf(bad));
        }
        assertThrows(IllegalArgumentException.class,
                () -> SuiAssetRegistry.fromClasspath().register(SuiAsset.icons("b", "brand-", "https://evil.example/b.svg")));
        SuiAssetRegistry.fromClasspath().register(SuiAsset.icons("b", "acme-logo-", "/b.svg"));
    }
}
