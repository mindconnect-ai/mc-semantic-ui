package ai.mindconnect.ui.ssr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which stylesheets a server-rendered page links for its theme. An overlay
 * restyles on top of sui.css, so both have to be there; sbb replaces sui.css,
 * so only it is; anything else is the plain default.
 */
class UiPageThemeStylesheetsTest {

    private static final String BASE = "<link rel=\"stylesheet\" href=\"/sui/sui.css\">";

    @Test
    void anOverlayThemeStacksItsSheetOnTheBase() {
        for (String theme : new String[] {"dark", "compact", "clody", "gipiti", "sorbet", "amethyst"}) {
            assertEquals(BASE + "<link rel=\"stylesheet\" href=\"/sui/sui-" + theme + ".css\">",
                    UiPageHtmlMessageConverter.themeStylesheets(theme), theme);
        }
    }

    @Test
    void sbbReplacesTheBase() {
        assertEquals("<link rel=\"stylesheet\" href=\"/sui/sui-sbb.css\">",
                UiPageHtmlMessageConverter.themeStylesheets("sbb"));
    }

    @Test
    void lightOrAnUnknownNameIsTheBaseAlone() {
        assertEquals(BASE, UiPageHtmlMessageConverter.themeStylesheets("light"));
        // A name that is no theme must not become a stylesheet path.
        assertEquals(BASE, UiPageHtmlMessageConverter.themeStylesheets("../../etc"));
    }
}
