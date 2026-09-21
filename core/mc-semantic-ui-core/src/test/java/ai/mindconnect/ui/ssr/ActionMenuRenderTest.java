package ai.mindconnect.ui.ssr;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiActionMenu;
import ai.mindconnect.ui.model.UiField;
import ai.mindconnect.ui.model.UiForm;
import ai.mindconnect.ui.model.UiMenuItem;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiTrigger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A menu in a form's button bar, on the server: the browser's markup, in its place, never the form's submit. */
class ActionMenuRenderTest {

    private final SuiServerRenderer renderer = new SuiServerRenderer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void theSameMarkupAsTheBrowser() throws Exception {
        // action-menu.test.mjs renders the same case through renderActionMenu().
        JsonNode c = mapper.readTree(getClass().getResourceAsStream("/menu/action-menu-case.json"));
        UiNode menu = mapper.treeToValue(c.get("menu"), UiNode.class);
        assertEquals(c.get("html").asText(), renderer.render(menu).trim());
    }

    @Test
    void inTheButtonBarInTheOrderAddedAndNeverTheSubmit() {
        UiForm form = UiForm.of("compose", "New mail")
                .field(UiField.text("subject", "Subject", "Hi").asEditable())
                .action(UiAction.primary("send", "Send").onClick(UiTrigger.api("POST", "/mail/send", "compose")))
                .action(UiAction.secondary("attach", "Attach").onClick(UiTrigger.api("POST", "/mail/attach", "compose")))
                .action(UiAction.menu("ai", "AI",
                        UiMenuItem.of("draft", "Draft with AI").onClick(UiTrigger.api("POST", "/ai/draft", "compose")))
                        .icon("sparkles"))
                .action(UiAction.secondary("check", "Check").onClick(UiTrigger.api("POST", "/mail/check", "compose")));
        String html = renderer.render(form);
        String footer = html.substring(html.indexOf("sui-form-footer"));
        int send = footer.indexOf("id=\"send\""), attach = footer.indexOf("id=\"attach\""),
                ai = footer.indexOf("id=\"ai\""), check = footer.indexOf("id=\"check\"");
        assertTrue(send > 0 && send < attach && attach < ai && ai < check, footer);
        assertTrue(footer.contains("<details class=\"sui-menu-button sui-menu-button--action sui-menu-button--align-start\" id=\"ai\""), footer);
        // The form submits to Send, whatever the order.
        assertTrue(html.contains("method=\"POST\" action=\"/mail/send\"") || html.contains("method=\"post\" action=\"/mail/send\""), html);
    }

    @Test
    void aFormWhoseOnlyButtonIsAMenuStillShowsIt() {
        UiForm form = UiForm.of("f", "F")
                .action(UiAction.menu("m", "More", UiMenuItem.of("x", "X").onClick(UiTrigger.api("POST", "/x", "f"))));
        String html = renderer.render(form);
        assertTrue(html.contains("id=\"m\" data-sui=\"menu-button\""), html);
        assertFalse(html.contains("action=\"/x\""), "a menu entry is never the form's submit: " + html);
    }

    @Test
    void goesOverTheWireAndBack() throws Exception {
        UiForm form = UiForm.of("compose", "New mail")
                .action(UiAction.menu("ai", "AI",
                        UiMenuItem.of("draft", "Draft with AI").onClick(UiTrigger.api("POST", "/ai/draft", "compose")),
                        UiMenuItem.divider(),
                        UiMenuItem.heading("Quick actions"))
                        .icon("sparkles"));
        JsonNode json = mapper.valueToTree(form);
        JsonNode menu = json.get("actions").get(0);
        assertEquals("action-menu", menu.get("type").asText());
        assertEquals("sparkles", menu.get("icon").asText());
        assertEquals("SECONDARY", menu.get("style").asText());
        assertEquals("compose", menu.get("items").get(0).get("onClick").get("payload").asText());
        assertTrue(menu.get("items").get(1).get("divider").asBoolean());
        assertTrue(menu.get("items").get(2).get("heading").asBoolean());
        assertEquals("Quick actions", menu.get("items").get(2).get("label").asText());
        assertFalse(menu.get("items").get(0).has("heading"), "heading=false stays off the wire");

        UiForm back = (UiForm) mapper.treeToValue(json, UiNode.class);
        UiActionMenu read = (UiActionMenu) back.getActions().get(0);
        assertEquals(3, read.getItems().size());
        assertTrue(read.getItems().get(2).isHeading());
    }

    @Test
    void theDocumentedExampleBuildsAndChains() {
        // website/docs/semantic-ui/elements/form.md, "A menu in the button bar".
        UiActionMenu ai = UiAction.menu("ai", "AI",
                        UiMenuItem.of("draft", "Draft with AI").icon("wand-sparkles")
                                .onClick(UiTrigger.api("POST", "/ai/draft", "compose")),
                        UiMenuItem.divider(),
                        UiMenuItem.heading("Quick actions"),
                        UiMenuItem.of("shorten", "Shorten").icon("scissors")
                                .onClick(UiTrigger.api("POST", "/ai/shorten")),
                        UiMenuItem.of("translate", "Translate").disabled("No translation service"))
                .icon("sparkles")
                .item(UiMenuItem.of("more", "More…").onClick(UiTrigger.api("GET", "/ai/more")));
        assertEquals(6, ai.getItems().size());
        assertEquals("sparkles", ai.getIcon());
        assertFalse(ai.getItems().get(4).isEnabled());
        String html = renderer.render(ai);
        assertTrue(html.contains("<a class=\"sui-menu-button-item\" id=\"translate\" data-id=\"translate\" role=\"menuitem\" aria-disabled=\"true\" title=\"No translation service\">"),
                "a disabled entry without a trigger is a link to nowhere: " + html);
        assertTrue(renderer.render(UiAction.menu("x", "X").disabled("Off")).contains("aria-disabled=\"true\" title=\"Off\""),
                "a disabled menu is shut");
    }
}
