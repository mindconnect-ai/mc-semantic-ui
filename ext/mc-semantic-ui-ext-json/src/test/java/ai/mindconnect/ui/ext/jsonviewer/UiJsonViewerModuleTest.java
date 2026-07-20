package ai.mindconnect.ui.ext.jsonviewer;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiStack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code json-viewer} node lives in this module, not in the core, so it has
 * to teach Jackson about itself. These tests pin both halves of that contract:
 * the type is discoverable with no manual wiring, and it round-trips inside an
 * ordinary core tree.
 *
 * <p>Worth having as its own test rather than trusting the build: the
 * registration is a text file under {@code META-INF/services}, which no
 * compiler checks. A typo there fails at runtime, in the host application,
 * with a confusing "unknown type id" — not here.
 */
class UiJsonViewerModuleTest {

    private static UiJsonViewer sample() {
        return UiJsonViewer.of("payload", "{\"a\":1}").expandLevel(2);
    }

    @Test
    void serviceLoaderIsEnoughToRegisterTheSubtype() {
        // No Spring, no explicit module registration — exactly what a plain
        // Java consumer gets from findAndRegisterModules().
        var mapper = new ObjectMapper().findAndRegisterModules();

        String json = json(() -> mapper.writeValueAsString(sample()));
        assertTrue(json.contains("\"type\":\"json-viewer\""), json);

        UiNode back = node(() -> mapper.readValue(json, UiNode.class));
        assertInstanceOf(UiJsonViewer.class, back);
        assertEquals("payload", back.getId());
        assertEquals("{\"a\":1}", ((UiJsonViewer) back).getJson());
    }

    @Test
    void itNestsInsideAnOrdinaryCoreTree() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var stack = UiStack.of(sample());
        stack.setId("wrap");

        String json = json(() -> mapper.writeValueAsString(stack));
        UiNode back = node(() -> mapper.readValue(json, UiNode.class));

        var children = ((UiStack) back).getChildren();
        assertEquals(1, children.size());
        assertInstanceOf(UiJsonViewer.class, children.get(0));
    }

    @Test
    void theMarkdownTypeIsNotDraggedInWithIt() {
        // The point of the split: a host that wants a JSON viewer does not also
        // get the Markdown node. If these two ever end up in one JAR again,
        // this fails and says why.
        var mapper = new ObjectMapper().findAndRegisterModules();
        boolean markdownKnown;
        try {
            mapper.readValue("{\"type\":\"markdown\",\"id\":\"m\"}", UiNode.class);
            markdownKnown = true;
        } catch (Exception e) {
            markdownKnown = false;
        }
        assertEquals(false, markdownKnown,
                "markdown resolved from the json module's classpath — "
                        + "the two extensions are not independent after all");
    }

    // Small helpers so the tests read as assertions rather than try/catch.
    private interface JsonSupplier { String get() throws Exception; }
    private interface NodeSupplier { UiNode get() throws Exception; }

    private static String json(JsonSupplier s) {
        try { return s.get(); } catch (Exception e) { throw new AssertionError(e); }
    }
    private static UiNode node(NodeSupplier s) {
        try { return s.get(); } catch (Exception e) { throw new AssertionError(e); }
    }
}
