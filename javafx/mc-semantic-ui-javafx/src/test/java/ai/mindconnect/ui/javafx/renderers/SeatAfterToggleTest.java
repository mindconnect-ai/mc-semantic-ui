package ai.mindconnect.ui.javafx.renderers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The row-ordering rule of an orderable checkbox group, checked against the
 * fixtures the web renderer's {@code seatAfterToggle} is tested with
 * ({@code core/mc-semantic-ui-core/src/test/resources/fixtures/choice-seats.json}).
 * Both clients follow one rule, so a server re-render lands on the order the
 * user already sees — read from the core module's sources, not copied, so the
 * two cannot drift.
 */
class SeatAfterToggleTest {

    private static final Path SEATS = Path.of("../../core/mc-semantic-ui-core/src/test/resources/fixtures/choice-seats.json");

    @Test
    void followsTheSharedFixtures() throws Exception {
        var fixtures = new ObjectMapper().readTree(SEATS.toFile());
        assertThat(fixtures.size()).isPositive();
        for (var fixture : fixtures) {
            var checked = new ArrayList<Boolean>();
            fixture.get("checked").forEach(c -> checked.add(c.asBoolean()));
            var index = new ArrayList<Integer>();
            fixture.get("index").forEach(i -> index.add(i.asInt()));

            assertThat(FieldRenderer.seatAfterToggle(List.copyOf(checked), List.copyOf(index), fixture.get("at").asInt()))
                    .as(fixture.get("name").asText())
                    .isEqualTo(fixture.get("seat").asInt());
        }
    }
}
