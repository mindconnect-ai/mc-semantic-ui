package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPatch;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedClassResolver;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns "the page the client has" and "the page it should have" into a
 * {@link UiPatch} made of the core's own operations.
 *
 * <p>The comparison runs on the JSON shape, not on the Java classes: a node
 * is an object whose {@code type} names a registered node type, a child
 * property is one holding such an object or a list of them, everything else
 * is an attribute. That is what makes the diff work for every node type —
 * the extensions' included, and ones written after this — with no per-type
 * code, and it is also exactly what the client renders from, so "unchanged
 * JSON" reliably means "unchanged DOM".
 *
 * <p>Rules, from the root down:
 * <ol>
 *   <li>Same {@code id} and {@code type}: attributes that differ become one
 *       {@code MERGE} (an attribute gone on the new side is sent as an
 *       explicit {@code null}); then the children are compared.</li>
 *   <li>A single-node child that changed identity or type is
 *       {@code REPLACE}d by the old child's id; one that disappeared is
 *       {@code REMOVE}d. A new one where there was none needs the parent.</li>
 *   <li>A child list with the same id sequence is compared pairwise. Ids
 *       removed in place → {@code REMOVE} each. Ids appended → {@code APPEND}
 *       each, but only where the client appends into the right element
 *       (a {@code stack}'s children, a {@code table}'s rows). Anything else —
 *       a reorder, an insert in the middle, a child without an id —
 *       {@code REPLACE}s the parent.</li>
 *   <li>Dialogs are matched by id: gone → {@code REMOVE}, new →
 *       {@code APPEND} into the {@code sui-dialogs} host, kept → compared.</li>
 *   <li>If the patch would weigh more than {@code fullPageThreshold} of the
 *       new page, the caller is told to send the page instead.</li>
 * </ol>
 *
 * <p>Ids must be unique within a page and stable across renders; the diff
 * treats a changed id as a different node.
 */
public final class TreeDiff {

    /** Where the core client mounts dialogs; an {@code APPEND} here opens one. */
    public static final String DIALOG_HOST_ID = "sui-dialogs";

    /** Parent type → child property whose new tail may be {@code APPEND}ed by the parent's id. */
    private static final Map<String, String> APPENDABLE = Map.of(
            "stack", "children",
            "table", "rows");

    private final ObjectMapper mapper;
    private final Set<String> nodeTypes;
    private final double fullPageThreshold;

    /**
     * @param mapper            knows every node type (core and registered extensions)
     * @param fullPageThreshold patch size / page size above which the page is sent
     *                          instead; {@code 0.6} is a reasonable default,
     *                          {@code >= 1} never gives up on a patch
     */
    public TreeDiff(ObjectMapper mapper, double fullPageThreshold) {
        this.mapper = mapper;
        this.nodeTypes = registeredNodeTypes(mapper);
        this.fullPageThreshold = fullPageThreshold;
    }

    public TreeDiff(ObjectMapper mapper) {
        this(mapper, 0.6);
    }

    /** The outcome: either operations to apply, or the advice to send the whole page. */
    public record Result(UiPatch patch, boolean fullPage, int patchBytes, int pageBytes) {

        public boolean isEmpty() {
            return !fullPage && patch.getPatches().isEmpty();
        }
    }

    /**
     * @param oldPage the {@code UiPage} JSON the client last received
     * @param newPage the {@code UiPage} JSON it should show now
     */
    public Result diff(JsonNode oldPage, JsonNode newPage) {
        // Both through text first. A tree built by valueToTree says LongNode
        // where one read back from the store says IntNode, and JsonNode.equals
        // tells them apart — which made every paginated table look changed.
        oldPage = normalize(oldPage);
        newPage = normalize(newPage);
        List<UiPatch.Operation> ops = new ArrayList<>();
        JsonNode oldRoot = oldPage == null ? null : oldPage.get("node");
        JsonNode newRoot = newPage.get("node");

        boolean full = false;
        if (oldRoot == null || oldRoot.isNull()) {
            full = newRoot != null && !newRoot.isNull();
        } else if (newRoot == null || newRoot.isNull()) {
            full = true;
        } else if (!diffNode(oldRoot, newRoot, ops)) {
            String oldId = idOf(oldRoot);
            if (oldId == null) {
                full = true;
            } else {
                ops.add(UiPatch.Operation.replace(oldId, toNode(newRoot)));
            }
        }
        if (full) {
            return new Result(null, true, 0, sizeOf(newPage));
        }

        diffDialogs(oldPage == null ? null : oldPage.get("dialogs"), newPage.get("dialogs"), ops);

        UiPatch patch = UiPatch.of();
        ops.forEach(patch::patch);
        int patchBytes = sizeOf(patch);
        int pageBytes = sizeOf(newPage);
        if (!ops.isEmpty() && fullPageThreshold < 1 && patchBytes > pageBytes * fullPageThreshold) {
            return new Result(null, true, patchBytes, pageBytes);
        }
        return new Result(patch, false, patchBytes, pageBytes);
    }

    // ── nodes ──────────────────────────────────────────────────────────────

    /**
     * Emits the operations that turn {@code a} into {@code b}. Returns
     * {@code false} when that is not possible below the node itself — the
     * caller then replaces at its own level.
     */
    private boolean diffNode(JsonNode a, JsonNode b, List<UiPatch.Operation> ops) {
        if (a.equals(b)) return true;
        if (!isNode(a) || !isNode(b)) return false;
        String id = idOf(a);
        if (id == null || !id.equals(idOf(b))) return false;
        if (!typeOf(a).equals(typeOf(b))) return false;

        // Look at every op we would emit before committing any: a child that
        // forces the parent's replacement makes the sibling merges moot.
        List<UiPatch.Operation> pending = new ArrayList<>();
        Map<String, Object> attributes = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        a.fieldNames().forEachRemaining(keys::add);
        b.fieldNames().forEachRemaining(keys::add);

        for (String key : keys) {
            JsonNode va = a.get(key);
            JsonNode vb = b.get(key);
            if (java.util.Objects.equals(va, vb)) continue;
            if (isChildProperty(va, vb)) {
                if (!diffChildProperty(typeOf(a), id, key, va, vb, pending)) return false;
            } else {
                attributes.put(key, vb == null || vb.isNull() ? null : mapper.convertValue(vb, Object.class));
            }
        }
        if (!attributes.isEmpty()) {
            ops.add(UiPatch.Operation.merge(id, attributes));
        }
        ops.addAll(pending);
        return true;
    }

    private boolean diffChildProperty(String parentType, String parentId, String key,
                                      JsonNode va, JsonNode vb, List<UiPatch.Operation> ops) {
        boolean listA = va != null && va.isArray();
        boolean listB = vb != null && vb.isArray();
        if (listA || listB) {
            if ((va != null && !va.isNull() && !listA) || (vb != null && !vb.isNull() && !listB)) return false;
            return diffChildList(parentType, parentId, key, listA ? va : null, listB ? vb : null, ops);
        }
        // Single child.
        boolean hasA = va != null && !va.isNull();
        boolean hasB = vb != null && !vb.isNull();
        if (hasA && hasB) {
            if (diffNode(va, vb, ops)) return true;
            String oldId = idOf(va);
            if (oldId == null) return false;
            ops.add(UiPatch.Operation.replace(oldId, toNode(vb)));
            return true;
        }
        if (hasA) {
            String oldId = idOf(va);
            if (oldId == null) return false;
            ops.add(UiPatch.Operation.remove(oldId));
            return true;
        }
        // A child appeared where there was none: only the parent knows where.
        return false;
    }

    private boolean diffChildList(String parentType, String parentId, String key,
                                  JsonNode a, JsonNode b, List<UiPatch.Operation> ops) {
        List<JsonNode> oldItems = items(a);
        List<JsonNode> newItems = items(b);
        List<String> oldIds = ids(oldItems);
        List<String> newIds = ids(newItems);
        if (oldIds == null || newIds == null) return false;

        if (oldIds.equals(newIds)) {
            return pairwise(oldItems, newItems, ops);
        }
        // Append only: the old sequence is a prefix of the new one.
        if (newIds.size() > oldIds.size() && newIds.subList(0, oldIds.size()).equals(oldIds)) {
            if (!key.equals(APPENDABLE.get(parentType))) return false;
            if (!pairwise(oldItems, newItems.subList(0, oldItems.size()), ops)) return false;
            for (int i = oldItems.size(); i < newItems.size(); i++) {
                ops.add(UiPatch.Operation.append(parentId, toNode(newItems.get(i))));
            }
            return true;
        }
        // Removal only: the new sequence is the old one with some ids taken out.
        if (newIds.size() < oldIds.size() && new HashSet<>(oldIds).containsAll(newIds) && isSubsequence(newIds, oldIds)) {
            Set<String> kept = new HashSet<>(newIds);
            List<JsonNode> keptOld = oldItems.stream().filter(n -> kept.contains(idOf(n))).toList();
            if (!pairwise(keptOld, newItems, ops)) return false;
            for (String id : oldIds) {
                if (!kept.contains(id)) ops.add(UiPatch.Operation.remove(id));
            }
            return true;
        }
        return false;
    }

    private boolean pairwise(List<JsonNode> oldItems, List<JsonNode> newItems, List<UiPatch.Operation> ops) {
        for (int i = 0; i < oldItems.size(); i++) {
            JsonNode x = oldItems.get(i);
            JsonNode y = newItems.get(i);
            if (x.equals(y)) continue;
            if (diffNode(x, y, ops)) continue;
            // Same id, different type: the client can still swap the element.
            ops.add(UiPatch.Operation.replace(idOf(x), toNode(y)));
        }
        return true;
    }

    // ── dialogs ────────────────────────────────────────────────────────────

    private void diffDialogs(JsonNode a, JsonNode b, List<UiPatch.Operation> ops) {
        Map<String, JsonNode> oldById = new LinkedHashMap<>();
        Map<String, JsonNode> newById = new LinkedHashMap<>();
        for (JsonNode d : items(a)) if (idOf(d) != null) oldById.put(idOf(d), d);
        for (JsonNode d : items(b)) if (idOf(d) != null) newById.put(idOf(d), d);
        for (var e : oldById.entrySet()) {
            if (!newById.containsKey(e.getKey())) ops.add(UiPatch.Operation.remove(e.getKey()));
        }
        for (var e : newById.entrySet()) {
            JsonNode old = oldById.get(e.getKey());
            if (old == null) {
                ops.add(UiPatch.Operation.append(DIALOG_HOST_ID, toNode(e.getValue())));
            } else if (!old.equals(e.getValue()) && !diffNode(old, e.getValue(), ops)) {
                ops.add(UiPatch.Operation.replace(e.getKey(), toNode(e.getValue())));
            }
        }
    }

    // ── shape helpers ──────────────────────────────────────────────────────

    private boolean isChildProperty(JsonNode va, JsonNode vb) {
        return isNodeOrNodeList(va) || isNodeOrNodeList(vb);
    }

    private boolean isNodeOrNodeList(JsonNode v) {
        if (v == null || v.isNull()) return false;
        if (v.isObject()) return isNode(v);
        if (v.isArray()) {
            if (v.isEmpty()) return false;
            for (JsonNode item : v) if (!isNode(item)) return false;
            return true;
        }
        return false;
    }

    private boolean isNode(JsonNode v) {
        if (v == null || !v.isObject()) return false;
        JsonNode type = v.get("type");
        if (type == null || !type.isTextual()) return false;
        return nodeTypes.isEmpty() || nodeTypes.contains(type.asText());
    }

    private static String idOf(JsonNode node) {
        JsonNode id = node == null ? null : node.get("id");
        return id != null && id.isTextual() && !id.asText().isBlank() ? id.asText() : null;
    }

    private static String typeOf(JsonNode node) {
        return node.get("type").asText();
    }

    private static List<JsonNode> items(JsonNode array) {
        if (array == null || !array.isArray()) return List.of();
        List<JsonNode> out = new ArrayList<>(array.size());
        array.forEach(out::add);
        return out;
    }

    /** Ids of a node list, or {@code null} when one is missing or repeated. */
    private static List<String> ids(List<JsonNode> nodes) {
        List<String> ids = new ArrayList<>(nodes.size());
        Set<String> seen = new HashSet<>();
        for (JsonNode n : nodes) {
            String id = idOf(n);
            if (id == null || !seen.add(id)) return null;
            ids.add(id);
        }
        return ids;
    }

    private static boolean isSubsequence(List<String> shorter, List<String> longer) {
        Iterator<String> it = longer.iterator();
        outer:
        for (String s : shorter) {
            while (it.hasNext()) {
                if (it.next().equals(s)) continue outer;
            }
            return false;
        }
        return true;
    }

    private JsonNode normalize(JsonNode json) {
        if (json == null || json.isNull()) return json;
        try {
            return mapper.readTree(mapper.writeValueAsBytes(json));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private UiNode toNode(JsonNode json) {
        try {
            return mapper.treeToValue(json, UiNode.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("page JSON does not deserialise as UiNode", e);
        }
    }

    private int sizeOf(Object value) {
        try {
            return mapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** The {@code type} names Jackson knows for {@link UiNode}: core's list plus registered modules. */
    static Set<String> registeredNodeTypes(ObjectMapper mapper) {
        try {
            var config = mapper.getSerializationConfig();
            AnnotatedClass ac = AnnotatedClassResolver.resolveWithoutSuperTypes(config, UiNode.class);
            var subtypes = mapper.getSubtypeResolver().collectAndResolveSubtypesByClass(config, ac);
            Set<String> names = new HashSet<>();
            for (NamedType t : subtypes) {
                if (t.hasName()) names.add(t.getName());
            }
            return Collections.unmodifiableSet(names);
        } catch (RuntimeException e) {
            return Set.of();
        }
    }
}
