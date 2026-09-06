package ai.mindconnect.ui.stateful;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses a raw query string into {@link RouteParams}. */
final class QueryStrings {

    private QueryStrings() {
    }

    static RouteParams parse(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return RouteParams.empty();
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
        }
        return RouteParams.of(out);
    }
}
