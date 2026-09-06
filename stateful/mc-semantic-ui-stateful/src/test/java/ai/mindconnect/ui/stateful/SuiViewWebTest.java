package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.stateful.spring.SuiViewController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The endpoint end to end through Spring MVC: the views of
 * {@link SuiViewEngineTest} are picked up by the classpath scan because the
 * test application sits in the same package.
 */
@SpringBootTest(classes = SuiViewWebTest.App.class)
@AutoConfigureMockMvc
class SuiViewWebTest {

    @SpringBootApplication
    static class App {
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    private final MockHttpSession session = new MockHttpSession();

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private String open() throws Exception {
        MvcResult r = mvc.perform(get("/counter").accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(header().string(SuiViewController.VERSION_HEADER, "1"))
                .andReturn();
        String navigate = json(r).get("navigate").asText();
        assertThat(navigate).startsWith("/counter?_v=");
        return navigate.substring(navigate.indexOf("_v=") + 3);
    }

    @Test
    void theSpaGetsAPageWhoseAddressNamesTheInstance() throws Exception {
        String id = open();
        MvcResult again = mvc.perform(get("/counter").param("_v", id).accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(again).get("navigate").asText()).isEqualTo("/counter?_v=" + id);
        assertThat(json(again).at("/node/children/0/text").asText()).isEqualTo("Clicked 0 times");
    }

    @Test
    void aBrowserWithoutScriptIsRedirectedToTheInstance() throws Exception {
        mvc.perform(get("/counter").accept(MediaType.TEXT_HTML).session(session))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/counter?_v=")));
    }

    @Test
    void aJsonEventAnswersWithAPatch() throws Exception {
        String id = open();
        MvcResult r = mvc.perform(post("/sui/s/" + id + "/inc/click")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(header().string(SuiViewController.VERSION_HEADER, "2"))
                .andReturn();
        JsonNode patch = json(r);
        assertThat(patch.get("patches")).hasSize(1);
        assertThat(patch.at("/patches/0/op").asText()).isEqualTo("MERGE");
        assertThat(patch.at("/patches/0/attributes/text").asText()).isEqualTo("Clicked 1 times");
    }

    @Test
    void aFormPostIsAppliedAndRedirectedBack_toastsWaitForThePage() throws Exception {
        String id = open();
        mvc.perform(post("/sui/s/" + id + "/save-note/click")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("note", "hi there")
                        .accept(MediaType.TEXT_HTML).session(session))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "/counter?_v=" + id));
        MvcResult page = mvc.perform(get("/counter").param("_v", id).accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(page).at("/toasts/0/message").asText()).isEqualTo("saved");
        assertThat(json(page).at("/node/children/6/fields/0/value").asText()).isEqualTo("hi there");
    }

    @Test
    void placeholdersInTheQueryReachTheHandlerAsPayload() throws Exception {
        MvcResult opened = mvc.perform(get("/other").accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk()).andReturn();
        String navigate = json(opened).get("navigate").asText();
        String id = navigate.substring(navigate.indexOf("_v=") + 3);
        MvcResult r = mvc.perform(post("/sui/s/" + id + "/pick/click").param("row", "p-carol")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk()).andReturn();
        // A tiny page: the row swap outweighs the budget, so a page comes back — showing the new name.
        JsonNode body = json(r);
        String text = body.has("patches")
                ? body.toString()
                : body.at("/node/children/0/text").asText();
        assertThat(text).contains("p-carol");
    }

    @Test
    void anotherSessionCannotTouchTheInstance() throws Exception {
        String id = open();
        mvc.perform(post("/sui/s/" + id + "/inc/click")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(new MockHttpSession()))
                .andExpect(status().isGone());
    }

    @Test
    void anExpiredInstanceIsReopenedFromTheReferer() throws Exception {
        MvcResult r = mvc.perform(post("/sui/s/does-not-exist/inc/click")
                        .header(HttpHeaders.REFERER, "http://localhost/counter?_v=does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(r).get("navigate").asText()).startsWith("/counter?_v=");
        assertThat(json(r).at("/toasts/0/message").asText()).contains("expired");
    }

    @Test
    void anUnboundEventIsABadRequest() throws Exception {
        String id = open();
        mvc.perform(post("/sui/s/" + id + "/nothing/click")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aFailingHandlerReportsAnErrorToastAndKeepsTheState() throws Exception {
        String id = open();
        MvcResult r = mvc.perform(post("/sui/s/" + id + "/boom/click")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(r).at("/toasts/0/level").asText()).isEqualTo("ERROR");
        assertThat(json(r).at("/toasts/0/message").asText()).isEqualTo("kaboom");
        MvcResult page = mvc.perform(get("/counter").param("_v", id).accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(page).at("/node/children/0/text").asText()).isEqualTo("Clicked 0 times");
    }
}
