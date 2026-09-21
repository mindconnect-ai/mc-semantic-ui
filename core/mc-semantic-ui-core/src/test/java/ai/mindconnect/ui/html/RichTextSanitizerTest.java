package ai.mindconnect.ui.html;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One policy in three places: this sanitiser, the browser's
 * {@code sanitizeRichText()} and what the RICHTEXT field renders from them.
 * Both test suites run the same cases, from
 * {@code src/test/resources/richtext/sanitize-cases.json}.
 */
class RichTextSanitizerTest {

    private static List<Map<String, String>> cases() throws Exception {
        try (InputStream in = Objects.requireNonNull(
                RichTextSanitizerTest.class.getResourceAsStream("/richtext/sanitize-cases.json"))) {
            return new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
    }

    @Test
    void everySharedCaseMatchesTheBrowser() throws Exception {
        for (var c : cases()) assertEquals(c.get("out"), RichTextSanitizer.sanitize(c.get("in")), c.get("name"));
    }

    @Test
    void cleaningCleanOutputChangesNothing() throws Exception {
        for (var c : cases()) assertEquals(c.get("out"), RichTextSanitizer.sanitize(c.get("out")), c.get("name"));
    }

    @Test
    void nullIsEmpty() {
        assertEquals("", RichTextSanitizer.sanitize(null));
    }

    @Test
    void linkSchemesAreCheckedAsABrowserReadsThem() {
        char tab = 9, soh = 1;
        assertFalse(RichTextSanitizer.safeHref("jav" + tab + "ascript:alert(1)"));
        assertFalse(RichTextSanitizer.safeHref(soh + "javascript:x"));
        assertFalse(RichTextSanitizer.safeHref("java script:x"));
        assertTrue(RichTextSanitizer.safeHref("https://example.com"));
        assertTrue(RichTextSanitizer.safeHref("/docs"));
    }

    @Test
    void onlyColourSyntaxPasses() {
        for (String ok : new String[]{"#4f6bed", "#fff", "red", "rgb(1, 2, 3)", "hsl(200 50% 40% / .5)", "var(--brand-1)"}) {
            assertEquals(ok, CssColor.orNull(ok), ok);
        }
        for (String bad : new String[]{"red;background:url(https://ev.il)", "url(x)", "expression(alert(1))", "red }", "", " red"}) {
            assertNull(CssColor.orNull(bad), bad);
        }
        assertNull(CssColor.orNull(null));
    }
}
