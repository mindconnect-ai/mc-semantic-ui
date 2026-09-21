package ai.mindconnect.sui.demo.explorer;

import ai.mindconnect.ui.assets.SuiAsset;
import ai.mindconnect.ui.assets.SuiAssetRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The asset registry in a real Spring Boot app: the calendar and kanban jars
 * are on the classpath with their assets.json and nothing else wires them.
 */
@SpringBootTest(properties = "explorer.root=target/explorer-root-test")
@AutoConfigureMockMvc
class AssetRegistryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SuiAssetRegistry registry;

    @AfterEach
    void forgetRuntimeRegistrations() {
        registry.unregister("kanban");
    }

    @Test
    void theJarsOnTheClasspathAreThereWithNoConfiguration() throws Exception {
        mvc.perform(get("/sui/assets"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$[?(@.id == 'calendar')].url").value("/sui-ext/calendar/extension.js"))
                .andExpect(jsonPath("$[?(@.id == 'kanban')].kind").value("extension"))
                .andExpect(jsonPath("$[?(@.id == 'kanban.css')].url").value("/sui-ext/kanban/kanban.css"))
                // The demo's bean overrides the calendar jar's stylesheet with order 10.
                .andExpect(jsonPath("$[?(@.id == 'calendar.css')].url").value("/explorer-calendar.css"));
    }

    @Test
    void theModuleInstallsThemAndRevalidates() throws Exception {
        MvcResult first = mvc.perform(get("/sui/assets.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/javascript")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(content().string(containsString("export async function installAll(renderer, bus")))
                .andExpect(content().string(containsString("\"url\":\"/sui-ext/calendar/extension.js\"")))
                .andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);
        mvc.perform(get("/sui/assets.js").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());

        // A change at run time — a marketplace swapping the kanban — changes the ETag.
        registry.register(SuiAsset.extension("kanban", "/market/kanban-pro.js").withOrder(50));
        MvcResult changed = mvc.perform(get("/sui/assets.js").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/market/kanban-pro.js")))
                .andExpect(content().string(not(containsString("/sui-ext/kanban/extension.js"))))
                .andReturn();
        assertNotEquals(etag, changed.getResponse().getHeader(HttpHeaders.ETAG));
    }

    @Test
    void aServerRenderedPageLinksTheStylesheetsAndRendersTheNodes() throws Exception {
        mvc.perform(get("/agenda").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "<link rel=\"stylesheet\" href=\"/explorer-calendar.css\" data-sui-asset=\"calendar.css\">")))
                .andExpect(content().string(containsString("data-sui-asset=\"kanban.css\"")))
                .andExpect(content().string(containsString("data-sui-asset=\"explorer.css\"")))
                .andExpect(content().string(containsString("<sui-calendar class=\"sui-calendar sui-calendar--week\"")))
                .andExpect(content().string(containsString("<sui-kanban class=\"sui-kanban\"")));
    }
}
