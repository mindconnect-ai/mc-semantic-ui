package ai.mindconnect.ui.editor;

import ai.mindconnect.ui.model.UiForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke-level coverage of the three editor endpoints. We boot a tiny
 * {@code @SpringBootApplication} stub inside the test sources so the
 * library's auto-configuration kicks in exactly the way a real host's
 * would — no manual bean wiring here.
 *
 * <p>The three checks (schema lists every registered type, PUT then GET
 * round-trips a UiNode, empty PUT yields an empty document) cover the wire
 * contract the frontend will rely on.
 */
@SpringBootTest(classes = EditorRestControllerTest.TestApp.class)
@AutoConfigureMockMvc
class EditorRestControllerTest {

    @SpringBootApplication
    static class TestApp {
        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void schemaListsEveryRegisteredType() throws Exception {
        mvc.perform(get("/editor/api/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='form')]").exists())
                .andExpect(jsonPath("$[?(@.type=='section')]").exists())
                .andExpect(jsonPath("$[?(@.type=='field')]").exists())
                .andExpect(jsonPath("$[?(@.type=='action')]").exists())
                .andExpect(jsonPath("$[?(@.type=='link')]").exists())
                .andExpect(jsonPath("$[?(@.type=='detail')]").exists())
                .andExpect(jsonPath("$[?(@.type=='list')]").exists())
                .andExpect(jsonPath("$[?(@.type=='table')]").exists())
                .andExpect(jsonPath("$[?(@.type=='header')]").exists())
                // Extension node types are NOT here: the editor depends on the
                // core alone, so its picker offers what the core can render.
                // chart and diagram ship with their own modules — an app that
                // wants them registers their metadata itself.
                .andExpect(jsonPath("$[?(@.type=='chart')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.type=='diagram')]").doesNotExist());
    }

    @Test
    void putThenGetRoundtripsTheTree() throws Exception {
        var form = UiForm.of("demo", "Demo form");
        var body = mapper.writeValueAsString(EditorContent.of(form));

        mvc.perform(put("/editor/api/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root.type").value("form"))
                .andExpect(jsonPath("$.root.id").value("demo"))
                .andExpect(jsonPath("$.root.title").value("Demo form"));

        mvc.perform(get("/editor/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.root.type").value("form"))
                .andExpect(jsonPath("$.root.id").value("demo"));
    }

    @Test
    void putEmptyBodyClearsTheDocument() throws Exception {
        // Empty body — Spring's @RequestBody(required=false) hands the controller a null
        // and we expect the store to be reset to EditorContent.empty().
        mvc.perform(put("/editor/api/state")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"root\""))));
    }
}
