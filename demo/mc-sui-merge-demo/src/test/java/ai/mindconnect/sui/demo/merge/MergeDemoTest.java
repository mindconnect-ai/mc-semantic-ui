package ai.mindconnect.sui.demo.merge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo makes a claim on its own page — that hiding the panel sends the one
 * attribute and nothing about the panel. These tests hold it to it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MergeDemoTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DemoState state;

    @BeforeEach
    void freshState() {
        state.reset();
    }

    @Test
    void hidingThePanelSendsTheAttributeAndNothingElse() throws Exception {
        var patch = patchFrom("/api/advanced/hidden");

        var merge = patch.get("patches").get(0);
        assertThat(merge.get("op").asText()).isEqualTo("MERGE");
        assertThat(merge.get("targetId").asText()).isEqualTo("advanced-card");
        assertThat(merge.get("attributes").properties())
                .describedAs("one attribute, and it is the display state")
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getKey()).isEqualTo("display");
                    assertThat(e.getValue().asText()).isEqualTo("HIDDEN");
                });

        // The claim the page makes in so many words: none of the panel is sent.
        assertThat(merge.toString()).doesNotContain("api.example.com", "Timeout", "eu-central");
    }

    @Test
    void theWireLogReportsWhatWasActuallySent() throws Exception {
        patchFrom("/api/advanced/hidden");

        var exchange = state.wire().get(0);
        assertThat(exchange.what()).isEqualTo("hide(\"advanced-card\")");
        // Not a compression claim — but on this panel the difference is an
        // order of magnitude, and the page prints both numbers.
        assertThat(exchange.sentBytes()).isLessThan(exchange.insteadBytes() / 10);
        assertThat(exchange.instead()).contains("api.example.com");
    }

    @Test
    void theTwoWaysToHideAreDifferentOperations() throws Exception {
        assertThat(attribute(patchFrom("/api/advanced/hidden"), "display")).isEqualTo("HIDDEN");
        assertThat(attribute(patchFrom("/api/advanced/blank"), "display")).isEqualTo("BLANK");
        // show() clears the field rather than omitting it — omitting it would
        // be a no-op, and there would be no way back from hidden.
        assertThat(patchFrom("/api/advanced/visible")
                .get("patches").get(0).get("attributes").get("display").isNull()).isTrue();
    }

    @Test
    void togglingTheButtonMergesBothNodesItChanges() throws Exception {
        var patch = patchFrom("/api/notifications/toggle");
        var ops = patch.get("patches");

        assertThat(ops.get(0).get("targetId").asText()).isEqualTo("notify-toggle");
        assertThat(ops.get(1).get("targetId").asText()).isEqualTo("notify-status");
        // The log update comes last: a patch means what it means in order.
        assertThat(ops.get(2).get("op").asText()).isEqualTo("REPLACE");
        assertThat(ops.get(2).get("targetId").asText()).isEqualTo("wire-log");

        // What was never named is never sent — the trigger and the id above all,
        // or a merged button would come back unclickable.
        assertThat(ops.get(0).get("attributes").properties())
                .extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("label", "style", "icon");
    }

    @Test
    void thePageCarriesItsOwnModelForTheClientToMergeAgainst() throws Exception {
        var html = mvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Without this the SPA has a page it did not build and no idea what any
        // of it is, so the first merge would have nothing to merge into.
        assertThat(html).contains("id=\"sui-model\"");
        assertThat(html).contains("advanced-card");
    }

    private String attribute(JsonNode patch, String field) {
        return patch.get("patches").get(0).get("attributes").get(field).asText();
    }

    /** Named for what it returns, and so as not to shadow the request builder. */
    private JsonNode patchFrom(String url) throws Exception {
        var body = mvc.perform(post(url).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }
}
