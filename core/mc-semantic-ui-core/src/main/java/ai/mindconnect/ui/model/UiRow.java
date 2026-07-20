package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row in a {@link UiTable}. Inherits {@link UiNode#getId()} which doubles
 * as the row's stable identity (used by {@code selectedRowId} on UiTable and
 * for row-action triggers). The actual cell data lives in {@link #data}, keyed
 * by {@link UiColumn#getDataKey()}.
 *
 * <p>Modelling rows as a UiNode means the editor can add / delete / select
 * them in the same way as any other tree child — no special-case for "data
 * objects". Production callers keep writing {@code .row(Map.of(...))}; the
 * convenience factory below packs the map into a UiRow on the way in.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UiRow extends UiNode {

    /** Column-key → cell value. LinkedHashMap so iteration order matches insertion. */
    private Map<String, Object> data = new LinkedHashMap<>();

    public UiRow put(String dataKey, Object value) {
        this.data.put(dataKey, value);
        return this;
    }

    public static UiRow of(Map<String, Object> data) {
        var r = new UiRow();
        // We *copy* into our own LinkedHashMap so callers using Map.of(…) —
        // which is immutable — can still feed us, and so later .put() calls
        // don't blow up.
        r.data = new LinkedHashMap<>(data);
        // Promote a row's "id" entry to the UiNode id when present. That way
        // {@code row.id} in templates keeps resolving to the same value it
        // used to (the renderer + selectedRowId logic relies on this).
        Object idVal = r.data.get("id");
        if (idVal != null) r.setId(idVal.toString());
        return r;
    }


}
