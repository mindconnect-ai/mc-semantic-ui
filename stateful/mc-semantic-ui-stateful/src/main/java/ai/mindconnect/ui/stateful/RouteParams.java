package ai.mindconnect.ui.stateful;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the address carried when a view was opened: the query parameters of
 * the {@code GET} that created the instance. Handed to
 * {@link SuiView#initialState(RouteParams)} and to nothing else — after that
 * the view has its state, and the address only names the instance.
 */
public final class RouteParams {

    private static final RouteParams EMPTY = new RouteParams(Map.of());

    private final Map<String, List<String>> values;

    private RouteParams(Map<String, List<String>> values) {
        var copy = new LinkedHashMap<String, List<String>>();
        values.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.values = Collections.unmodifiableMap(copy);
    }

    public static RouteParams empty() {
        return EMPTY;
    }

    public static RouteParams of(Map<String, List<String>> values) {
        return values == null || values.isEmpty() ? EMPTY : new RouteParams(values);
    }

    /** From a servlet-style parameter map ({@code String[]} values). */
    public static RouteParams ofArrays(Map<String, String[]> values) {
        if (values == null || values.isEmpty()) return EMPTY;
        var map = new LinkedHashMap<String, List<String>>();
        values.forEach((k, v) -> map.put(k, v == null ? List.of() : List.of(v)));
        return new RouteParams(map);
    }

    /** First value of {@code name}, or {@code null}. */
    public String query(String name) {
        List<String> v = values.get(name);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    /** First value of {@code name}, or {@code fallback} when absent or blank. */
    public String query(String name, String fallback) {
        String v = query(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** First value of {@code name} as an int, or {@code fallback} when absent or malformed. */
    public int queryInt(String name, int fallback) {
        String v = query(name);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** All values of {@code name}; empty when absent. */
    public List<String> queryAll(String name) {
        return values.getOrDefault(name, List.of());
    }

    /** Every parameter, in arrival order. */
    public Map<String, List<String>> all() {
        return values;
    }
}
