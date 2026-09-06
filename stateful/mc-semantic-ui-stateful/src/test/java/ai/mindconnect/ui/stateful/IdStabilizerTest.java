package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdStabilizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void factoryColumnsGetTableScopedIdsAndKeepThemAcrossRenders() {
        JsonNode first = IdStabilizer.stabilize(mapper.valueToTree(UiPage.of("/x", table())));
        JsonNode second = IdStabilizer.stabilize(mapper.valueToTree(UiPage.of("/x", table())));
        assertThat(first.at("/node/columns/0/id").asText()).isEqualTo("products-col-sku");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void explicitColumnIdsAreLeftAlone() {
        UiColumn c = UiColumn.of("sku", "SKU");
        c.setId("my-sku-column");
        UiTable t = UiTable.of("products", "P").column(c);
        JsonNode page = IdStabilizer.stabilize(mapper.valueToTree(UiPage.of("/x", t)));
        assertThat(page.at("/node/columns/0/id").asText()).isEqualTo("my-sku-column");
    }

    private static UiTable table() {
        return UiTable.of("products", "P").column(UiColumn.of("sku", "SKU")).column(UiColumn.of("name", "Name"));
    }
}
