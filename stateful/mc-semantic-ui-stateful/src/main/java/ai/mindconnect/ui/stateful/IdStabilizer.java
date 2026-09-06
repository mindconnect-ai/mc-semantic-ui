package ai.mindconnect.ui.stateful;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.regex.Pattern;

/**
 * Makes ids the core hands out per call deterministic per render.
 *
 * <p>{@code UiColumn.of(dataKey, label)} stamps its column with a fresh nonce
 * so two tables with a {@code "name"} column do not collide. Harmless for a
 * stateless controller, fatal for a diff: every render is then a table with
 * all-new column ids, and every event replaces every table. Here a column
 * that still wears the factory's nonce is renamed after its table and data
 * key — unique across tables, identical across renders. A column the view
 * named itself is left alone.
 *
 * <p>Runs on the JSON, so it applies to what the client receives and to what
 * is stored alike; no Java object is touched.
 */
final class IdStabilizer {

    private static final Pattern NONCED_COLUMN = Pattern.compile("^col-(.+)-[0-9a-z]{4}$");

    private IdStabilizer() {
    }

    static JsonNode stabilize(JsonNode page) {
        walk(page);
        return page;
    }

    private static void walk(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            if ("table".equals(text(node, "type"))) stabilizeTable((ObjectNode) node);
            node.fields().forEachRemaining(e -> walk(e.getValue()));
        } else if (node.isArray()) {
            node.forEach(IdStabilizer::walk);
        }
    }

    private static void stabilizeTable(ObjectNode table) {
        String tableId = text(table, "id");
        JsonNode columns = table.get("columns");
        if (tableId == null || columns == null || !columns.isArray()) return;
        for (JsonNode c : columns) {
            if (!c.isObject() || !"column".equals(text(c, "type"))) continue;
            String id = text(c, "id");
            String dataKey = text(c, "dataKey");
            if (id == null || dataKey == null) continue;
            var m = NONCED_COLUMN.matcher(id);
            if (m.matches() && m.group(1).equals(dataKey)) {
                ((ObjectNode) c).put("id", tableId + "-col-" + dataKey);
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }
}
