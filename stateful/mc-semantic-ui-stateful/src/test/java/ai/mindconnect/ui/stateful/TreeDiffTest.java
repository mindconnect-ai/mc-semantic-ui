package ai.mindconnect.ui.stateful;

import ai.mindconnect.ui.model.UiAction;
import ai.mindconnect.ui.model.UiColumn;
import ai.mindconnect.ui.model.UiDialog;
import ai.mindconnect.ui.model.UiNode;
import ai.mindconnect.ui.model.UiPage;
import ai.mindconnect.ui.model.UiPatch;
import ai.mindconnect.ui.model.UiStack;
import ai.mindconnect.ui.model.UiTable;
import ai.mindconnect.ui.model.UiText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TreeDiffTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TreeDiff diff = new TreeDiff(mapper, 1.0); // never gives up on a patch
    private final JsonPatchApplier applier = new JsonPatchApplier(mapper);

    private JsonNode page(UiNode root, UiDialog... dialogs) {
        UiPage p = UiPage.of("/x", root);
        for (UiDialog d : dialogs) p.dialog(d);
        return mapper.valueToTree(p);
    }

    private static UiStack stack(String id, UiNode... children) {
        UiStack s = UiStack.of(id);
        for (UiNode c : children) s.child(c);
        return s;
    }

    private static UiDialog dialog(String id, String title) {
        UiDialog d = UiDialog.of(title, null, UiText.of(id + "-body", "hi"));
        d.setId(id);
        return d;
    }

    /** The oracle: applying the diff to the old page must give the new page. */
    private TreeDiff.Result diffAndCheck(JsonNode oldPage, JsonNode newPage) {
        TreeDiff.Result r = diff.diff(oldPage, newPage);
        if (!r.fullPage()) {
            JsonNode applied = applier.apply(oldPage, r.patch());
            // navigate/title are page attributes the diff does not carry; compare node + dialogs.
            assertThat(applied.get("node")).isEqualTo(newPage.get("node"));
            // A closed last dialog leaves an empty list where the page has none.
            JsonNode appliedDialogs = applied.path("dialogs");
            JsonNode expectedDialogs = newPage.path("dialogs");
            if (appliedDialogs.isMissingNode() || appliedDialogs.isEmpty()) {
                assertThat(expectedDialogs.isMissingNode() || expectedDialogs.isEmpty()).isTrue();
            } else {
                assertThat(appliedDialogs).isEqualTo(expectedDialogs);
            }
        }
        return r;
    }

    private static List<UiPatch.Op> ops(TreeDiff.Result r) {
        return r.patch().getPatches().stream().map(UiPatch.Operation::getOp).toList();
    }

    @Test
    void identicalPagesGiveAnEmptyPatch() {
        JsonNode p = page(stack("root", UiText.of("t", "hello")));
        TreeDiff.Result r = diffAndCheck(p, p.deepCopy());
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    void changedAttributeBecomesOneMergeNamingOnlyThatAttribute() {
        JsonNode a = page(stack("root", UiText.of("t", "hello"), UiAction.primary("b", "Go")));
        JsonNode b = page(stack("root", UiText.of("t", "bye"), UiAction.primary("b", "Go")));
        TreeDiff.Result r = diffAndCheck(a, b);
        assertThat(ops(r)).containsExactly(UiPatch.Op.MERGE);
        UiPatch.Operation op = r.patch().getPatches().get(0);
        assertThat(op.getTargetId()).isEqualTo("t");
        assertThat(op.getAttributes()).containsExactly(Map.entry("text", "bye"));
    }

    @Test
    void clearedAttributeIsSentAsExplicitNull() {
        UiAction on = UiAction.primary("b", "Go").disabled("busy");
        UiAction off = UiAction.primary("b", "Go");
        TreeDiff.Result r = diffAndCheck(page(stack("root", on)), page(stack("root", off)));
        UiPatch.Operation op = r.patch().getPatches().get(0);
        assertThat(op.getOp()).isEqualTo(UiPatch.Op.MERGE);
        assertThat(op.getAttributes()).containsEntry("enabled", true);
        assertThat(op.getAttributes()).containsKey("disabledReason");
        assertThat(op.getAttributes().get("disabledReason")).isNull();
    }

    @Test
    void appendedStackChildIsAppended() {
        JsonNode a = page(stack("root", UiText.of("t1", "one")));
        JsonNode b = page(stack("root", UiText.of("t1", "one"), UiText.of("t2", "two")));
        TreeDiff.Result r = diffAndCheck(a, b);
        assertThat(ops(r)).containsExactly(UiPatch.Op.APPEND);
        assertThat(r.patch().getPatches().get(0).getTargetId()).isEqualTo("root");
    }

    @Test
    void removedStackChildIsRemoved() {
        JsonNode a = page(stack("root", UiText.of("t1", "one"), UiText.of("t2", "two"), UiText.of("t3", "three")));
        JsonNode b = page(stack("root", UiText.of("t1", "one"), UiText.of("t3", "three")));
        TreeDiff.Result r = diffAndCheck(a, b);
        assertThat(ops(r)).containsExactly(UiPatch.Op.REMOVE);
        assertThat(r.patch().getPatches().get(0).getTargetId()).isEqualTo("t2");
    }

    @Test
    void removalAndChangeInOneListBothTravel() {
        JsonNode a = page(stack("root", UiText.of("t1", "one"), UiText.of("t2", "two")));
        JsonNode b = page(stack("root", UiText.of("t2", "zwei")));
        TreeDiff.Result r = diffAndCheck(a, b);
        assertThat(ops(r)).containsExactlyInAnyOrder(UiPatch.Op.MERGE, UiPatch.Op.REMOVE);
    }

    @Test
    void reorderedChildrenReplaceTheParent() {
        JsonNode a = page(stack("root", UiText.of("t1", "one"), UiText.of("t2", "two")));
        JsonNode b = page(stack("root", UiText.of("t2", "two"), UiText.of("t1", "one")));
        TreeDiff.Result r = diffAndCheck(a, b);
        assertThat(ops(r)).containsExactly(UiPatch.Op.REPLACE);
        assertThat(r.patch().getPatches().get(0).getTargetId()).isEqualTo("root");
    }

    @Test
    void insertInTheMiddleReplacesTheParent() {
        JsonNode a = page(stack("root", UiText.of("t1", "one"), UiText.of("t3", "three")));
        JsonNode b = page(stack("root", UiText.of("t1", "one"), UiText.of("t2", "two"), UiText.of("t3", "three")));
        TreeDiff.Result r = diffAndCheck(a, b);
        assertThat(ops(r)).containsExactly(UiPatch.Op.REPLACE);
    }

    @Test
    void childWithoutIdForcesParentReplace() {
        JsonNode a = page(stack("root", UiText.of("one")));
        JsonNode b = page(stack("root", UiText.of("two")));
        TreeDiff.Result r = diffAndCheck(a, b);
        assertThat(ops(r)).containsExactly(UiPatch.Op.REPLACE);
        assertThat(r.patch().getPatches().get(0).getTargetId()).isEqualTo("root");
    }

    @Test
    void appendedTableRowIsAppendedButAppendedColumnReplacesTheTable() {
        UiTable t1 = UiTable.of("tbl", "T").column(col("a")).row(Map.of("id", "r1", "a", "1"));
        UiTable t2 = UiTable.of("tbl", "T").column(col("a")).row(Map.of("id", "r1", "a", "1")).row(Map.of("id", "r2", "a", "2"));
        TreeDiff.Result rows = diffAndCheck(page(t1), page(t2));
        assertThat(ops(rows)).containsExactly(UiPatch.Op.APPEND);
        assertThat(rows.patch().getPatches().get(0).getTargetId()).isEqualTo("tbl");

        UiTable t3 = UiTable.of("tbl", "T").column(col("a")).column(col("b")).row(Map.of("id", "r1", "a", "1"));
        TreeDiff.Result cols = diffAndCheck(page(t1), page(t3));
        assertThat(ops(cols)).containsExactly(UiPatch.Op.REPLACE);
        assertThat(cols.patch().getPatches().get(0).getTargetId()).isEqualTo("tbl");
    }

    @Test
    void numbersThatWentThroughTheStoreStillCompareEqual() throws Exception {
        // valueToTree says LongNode for a long, readTree says IntNode for the
        // same digits; a diff that told them apart merged every paginated
        // table on every event.
        UiTable t = UiTable.of("tbl", "T").column(col("a")).row(Map.of("id", "r1", "a", "1")).paginate(1, 8, 25L);
        JsonNode fresh = page(t);
        JsonNode stored = mapper.readTree(mapper.writeValueAsString(fresh));
        assertThat(diff.diff(stored, fresh).isEmpty()).isTrue();
    }

    @Test
    void changedRowDataIsAMergeOnTheRow() {
        UiTable t1 = UiTable.of("tbl", "T").column(col("a")).row(Map.of("id", "r1", "a", "1"));
        UiTable t2 = UiTable.of("tbl", "T").column(col("a")).row(Map.of("id", "r1", "a", "2"));
        TreeDiff.Result r = diffAndCheck(page(t1), page(t2));
        assertThat(ops(r)).containsExactly(UiPatch.Op.MERGE);
        assertThat(r.patch().getPatches().get(0).getTargetId()).isEqualTo("r1");
    }

    @Test
    void rootWithNewIdIsReplacedByTheOldId() {
        TreeDiff.Result r = diffAndCheck(page(stack("a", UiText.of("t", "x"))), page(stack("b", UiText.of("t", "x"))));
        assertThat(ops(r)).containsExactly(UiPatch.Op.REPLACE);
        assertThat(r.patch().getPatches().get(0).getTargetId()).isEqualTo("a");
    }

    @Test
    void rootWithoutIdMeansFullPage() {
        TreeDiff.Result r = diff.diff(page(UiText.of("x")), page(UiText.of("y")));
        assertThat(r.fullPage()).isTrue();
    }

    @Test
    void noPreviousPageMeansFullPage() {
        assertThat(diff.diff(null, page(stack("root"))).fullPage()).isTrue();
    }

    @Test
    void dialogsOpenAndCloseThroughTheHost() {
        JsonNode closed = page(stack("root"));
        JsonNode open = page(stack("root"), dialog("dlg", "Hi"));
        TreeDiff.Result opened = diffAndCheck(closed, open);
        assertThat(ops(opened)).containsExactly(UiPatch.Op.APPEND);
        assertThat(opened.patch().getPatches().get(0).getTargetId()).isEqualTo(TreeDiff.DIALOG_HOST_ID);

        TreeDiff.Result shut = diffAndCheck(open, closed);
        assertThat(ops(shut)).containsExactly(UiPatch.Op.REMOVE);
        assertThat(shut.patch().getPatches().get(0).getTargetId()).isEqualTo("dlg");
    }

    @Test
    void changedDialogTitleIsAMerge() {
        TreeDiff.Result r = diffAndCheck(page(stack("root"), dialog("dlg", "Hi")), page(stack("root"), dialog("dlg", "Bye")));
        assertThat(ops(r)).containsExactly(UiPatch.Op.MERGE);
        assertThat(r.patch().getPatches().get(0).getTargetId()).isEqualTo("dlg");
    }

    @Test
    void oversizedPatchGivesWayToTheFullPage() {
        TreeDiff strict = new TreeDiff(mapper, 0.0);
        TreeDiff.Result r = strict.diff(page(stack("root", UiText.of("t", "a"))), page(stack("root", UiText.of("t", "b"))));
        assertThat(r.fullPage()).isTrue();
        assertThat(r.patchBytes()).isGreaterThan(0);
    }

    @Test
    void randomEditsRoundTripThroughTheOracle() {
        Random rnd = new Random(42);
        for (int i = 0; i < 300; i++) {
            List<UiNode> items = new ArrayList<>();
            int n = rnd.nextInt(6);
            for (int k = 0; k < n; k++) items.add(UiText.of("t" + k, "v" + rnd.nextInt(3)));
            UiStack before = stack("root", items.toArray(UiNode[]::new));

            List<UiNode> edited = new ArrayList<>(items);
            switch (rnd.nextInt(5)) {
                case 0 -> { if (!edited.isEmpty()) edited.remove(rnd.nextInt(edited.size())); }
                case 1 -> edited.add(UiText.of("t" + n, "new"));
                case 2 -> { if (!edited.isEmpty()) { int j = rnd.nextInt(edited.size()); edited.set(j, UiText.of("t" + j, "changed")); } }
                case 3 -> java.util.Collections.shuffle(edited, rnd);
                default -> { }
            }
            UiStack after = stack("root", edited.toArray(UiNode[]::new));
            diffAndCheck(page(before), page(after));
        }
    }

    private static UiColumn col(String key) {
        UiColumn c = UiColumn.of(key, key.toUpperCase());
        c.setId("col-" + key);
        return c;
    }
}
