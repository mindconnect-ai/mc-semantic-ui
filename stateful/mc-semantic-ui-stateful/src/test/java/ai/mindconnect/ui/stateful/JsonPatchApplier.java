package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiPatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

/**
 * A model of what the browser client does with a patch, on JSON: the test
 * oracle for {@link TreeDiff}. If {@code apply(old, diff(old, new))} is not
 * {@code new}, the diff is wrong.
 */
final class JsonPatchApplier {

    private final ObjectMapper mapper;

    JsonPatchApplier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    JsonNode apply(JsonNode page, UiPatch patch) {
        ObjectNode out = page.deepCopy();
        for (UiPatch.Operation op : patch.getPatches()) {
            switch (op.getOp()) {
                case MERGE -> merge(out, op.getTargetId(), op.getAttributes());
                case REPLACE -> replace(out, op.getTargetId(), mapper.valueToTree(op.getNode()));
                case APPEND -> append(out, op.getTargetId(), mapper.valueToTree(op.getNode()));
                case REMOVE -> remove(out, op.getTargetId());
                case CLEAR -> throw new UnsupportedOperationException("diff never emits CLEAR");
            }
        }
        return out;
    }

    private void merge(ObjectNode page, String id, Map<String, Object> attributes) {
        ObjectNode target = (ObjectNode) find(page, id);
        if (target == null) throw new AssertionError("MERGE target not found: " + id);
        attributes.forEach((k, v) -> {
            if (v == null) target.remove(k);
            else target.set(k, mapper.valueToTree(v));
        });
    }

    private void replace(ObjectNode page, String id, JsonNode node) {
        if (!replaceIn(page, id, node)) throw new AssertionError("REPLACE target not found: " + id);
    }

    private void append(ObjectNode page, String id, JsonNode node) {
        if (TreeDiff.DIALOG_HOST_ID.equals(id)) {
            ArrayNode dialogs = page.withArray("dialogs");
            dialogs.add(node);
            return;
        }
        ObjectNode target = (ObjectNode) find(page, id);
        if (target == null) throw new AssertionError("APPEND target not found: " + id);
        String type = target.get("type").asText();
        String prop = switch (type) {
            case "stack" -> "children";
            case "table" -> "rows";
            default -> throw new AssertionError("client cannot APPEND into a " + type);
        };
        target.withArray(prop).add(node);
    }

    private void remove(ObjectNode page, String id) {
        if (!removeIn(page, id)) throw new AssertionError("REMOVE target not found: " + id);
    }

    // ── traversal ──────────────────────────────────────────────────────────

    static JsonNode find(JsonNode node, String id) {
        if (node == null) return null;
        if (node.isObject()) {
            JsonNode nid = node.get("id");
            if (nid != null && nid.isTextual() && nid.asText().equals(id) && node.has("type")) return node;
            for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
                JsonNode found = find(it.next(), id);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = find(child, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean replaceIn(JsonNode parent, String id, JsonNode replacement) {
        if (parent.isObject()) {
            ObjectNode obj = (ObjectNode) parent;
            for (Iterator<Map.Entry<String, JsonNode>> it = obj.fields(); it.hasNext(); ) {
                var e = it.next();
                if (isNodeWithId(e.getValue(), id)) {
                    obj.set(e.getKey(), replacement);
                    return true;
                }
                if (replaceIn(e.getValue(), id, replacement)) return true;
            }
        } else if (parent.isArray()) {
            ArrayNode arr = (ArrayNode) parent;
            for (int i = 0; i < arr.size(); i++) {
                if (isNodeWithId(arr.get(i), id)) {
                    arr.set(i, replacement);
                    return true;
                }
                if (replaceIn(arr.get(i), id, replacement)) return true;
            }
        }
        return false;
    }

    private static boolean removeIn(JsonNode parent, String id) {
        if (parent.isObject()) {
            ObjectNode obj = (ObjectNode) parent;
            for (Iterator<Map.Entry<String, JsonNode>> it = obj.fields(); it.hasNext(); ) {
                var e = it.next();
                if (isNodeWithId(e.getValue(), id)) {
                    obj.remove(e.getKey());
                    return true;
                }
                if (removeIn(e.getValue(), id)) return true;
            }
        } else if (parent.isArray()) {
            ArrayNode arr = (ArrayNode) parent;
            for (int i = 0; i < arr.size(); i++) {
                if (isNodeWithId(arr.get(i), id)) {
                    arr.remove(i);
                    return true;
                }
                if (removeIn(arr.get(i), id)) return true;
            }
        }
        return false;
    }

    private static boolean isNodeWithId(JsonNode n, String id) {
        return n != null && n.isObject() && n.has("type") && n.has("id") && n.get("id").asText().equals(id);
    }
}
