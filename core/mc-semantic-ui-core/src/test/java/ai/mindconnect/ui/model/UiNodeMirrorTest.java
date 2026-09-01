package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps {@code model.ts} honest about {@code UiNode}.
 *
 * <p>The TypeScript union is written by hand and mirrors the Java model — there
 * is no generator. That was survivable while both sides only ever met inside
 * this repo. It stopped being survivable when the client became an npm package:
 * consumers now compile against these declarations, so a field Java sends and
 * TypeScript has never heard of is their compile error, and a field typed
 * differently on the two sides is a runtime surprise nobody can trace back
 * here.
 *
 * <p>What it checks, from the Java side outwards — Java is the wire format's
 * source of truth, so anything Jackson serialises has to exist in the mirror:
 *
 * <ol>
 *   <li>every {@code @JsonSubTypes.Type} has an interface of the same name,</li>
 *   <li>that interface declares the same {@code type} discriminator,</li>
 *   <li>every property Jackson serialises appears as a field on it.</li>
 * </ol>
 *
 * <p>It deliberately does <em>not</em> compare types. Doing that means teaching
 * a test the whole Java-to-TypeScript mapping, and it would fail on the places
 * where the mirror is intentionally wider than the model — a string union
 * opened up with {@code (string & {})} for apps that register their own
 * behaviours, for instance. Names catch the drift that actually happens:
 * somebody adds a field on one side and forgets the other.
 *
 * <p>The reverse direction is unchecked on purpose: TypeScript may carry things
 * Java does not, because the renderers work with shapes the server never sends.
 */
class UiNodeMirrorTest {

    private static final Path MODEL_TS = Path.of("src/main/ts/model.ts");

    /**
     * Divergence that exists today, written down so it is visible instead of
     * silent. The test's job from here on is to stop the list growing — every
     * entry is a field a Java server sends that a TypeScript consumer cannot
     * name, and shrinking it is a separate piece of work with a public type
     * surface to consider.
     *
     * <p>Two patterns account for nearly all of it. Several leaf interfaces
     * (UiText, UiIcon, UiAction, UiField, UiLink, and the table's column and
     * row) are declared standalone rather than {@code extends UiNodeBase}, so
     * they miss whatever UiNode gives every node — {@code title},
     * {@code display}, the event triggers. And a handful of genuinely absent
     * fields: {@code UiAction.href}, {@code UiMenuItem.style} /
     * {@code appearance} / {@code loading}.
     */
    private static final Map<String, Set<String>> ACCEPTED_GAPS = Map.ofEntries(
            Map.entry("UiColumn",   Set.of("title", "display")),
            Map.entry("UiRow",      Set.of("title", "display")),
            Map.entry("UiText",     Set.of("title", "display")),
            Map.entry("UiIcon",     Set.of("onClick", "onDblClick", "onHover",
                                           "onLeave", "onChange", "onInput", "display")),
            Map.entry("UiSpinner",  Set.of("display")),
            Map.entry("UiProgress", Set.of("display")),
            Map.entry("UiMenuItem", Set.of("style", "appearance", "loading")),
            Map.entry("UiAction",   Set.of("title", "cssClass", "display", "href")),
            Map.entry("UiField",    Set.of("title", "cssClass", "display")),
            Map.entry("UiLink",     Set.of("title", "display")),
            // UiPage is the one real disagreement rather than an omission: Java
            // models it as a UiNode subtype, TypeScript as a plain envelope
            // with no discriminator at all. Reconciling that is a decision
            // about the wire format, not a missing line.
            Map.entry("UiPage",     Set.of("id", "title", "cssClass", "display", "onClick",
                                           "onDblClick", "onHover", "onLeave", "onChange", "onInput")));

    /** Types whose TypeScript shape deliberately carries no discriminator. */
    private static final Set<String> NO_DISCRIMINATOR = Set.of("UiPage");

    /**
     * Where the mirror deliberately picked another name. Both are table-only
     * shapes, and the TypeScript name says so where the Java one does not.
     */
    private static final Map<String, String> RENAMED = Map.of(
            "UiColumn", "UiTableColumn",
            "UiRow", "UiTableRow");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyJavaSubtypeIsMirroredInModelTs() throws IOException {
        String ts = Files.readString(MODEL_TS, StandardCharsets.UTF_8);
        Map<String, String> interfaces = parseInterfaces(ts);
        Map<String, String> extendsOf = parseExtends(ts);
        assertTrue(interfaces.size() > 20,
                "parsed only " + interfaces.size() + " interfaces from " + MODEL_TS
                        + " — the parser, not the model, is probably what broke");

        List<String> problems = new ArrayList<>();

        for (JsonSubTypes.Type subtype : UiNode.class.getAnnotation(JsonSubTypes.class).value()) {
            String javaName = subtype.value().getSimpleName();
            String tsName = RENAMED.getOrDefault(javaName, javaName);
            String discriminator = subtype.name();

            String body = interfaces.get(tsName);
            if (body == null) {
                problems.add(javaName + " (type \"" + discriminator + "\") has no `export interface "
                        + tsName + "` in model.ts");
                continue;
            }

            if (!NO_DISCRIMINATOR.contains(javaName)
                    && !body.contains("type: \"" + discriminator + "\"")) {
                problems.add(tsName + " should declare `type: \"" + discriminator
                        + "\"` — that is the discriminator Jackson writes");
            }

            Set<String> declared = fieldsIncludingInherited(tsName, interfaces, extendsOf);
            Set<String> accepted = ACCEPTED_GAPS.getOrDefault(javaName, Set.of());
            for (String property : serialisedProperties(subtype.value())) {
                if (!declared.contains(property) && !accepted.contains(property)) {
                    problems.add(javaName + "." + property
                            + " is serialised by Jackson but missing from the interface");
                }
            }
        }

        if (!problems.isEmpty()) {
            fail("model.ts has drifted from the Java model (" + problems.size() + "):\n  - "
                    + String.join("\n  - ", problems)
                    + "\n\nUpdate src/main/ts/model.ts. Consumers compile against these"
                    + " declarations, so a gap here is their problem, not ours.");
        }
    }

    /** Property names Jackson would write for a node class, discriminator aside. */
    private Set<String> serialisedProperties(Class<?> type) {
        BeanDescription description = mapper.getSerializationConfig()
                .introspect(mapper.constructType(type));
        Set<String> names = new LinkedHashSet<>();
        description.findProperties().forEach(p -> names.add(p.getName()));
        return names;
    }

    /**
     * The fields an interface offers, its own plus everything it inherits.
     * Java repeats nothing either — {@code UiStack extends UiNode} — so the
     * comparison has to be made on the flattened shape or every node type
     * reports the whole of {@code UiNodeBase} as missing.
     */
    private static Set<String> fieldsIncludingInherited(
            String name, Map<String, String> interfaces, Map<String, String> extendsOf) {
        Set<String> names = new LinkedHashSet<>();
        for (String current = name; current != null; current = extendsOf.get(current)) {
            String body = interfaces.get(current);
            if (body == null) break;          // extends something declared elsewhere
            names.addAll(parseFieldNames(body));
        }
        return names;
    }

    /** Interface name to the single interface it extends, where there is one. */
    private static Map<String, String> parseExtends(String ts) {
        Map<String, String> found = new LinkedHashMap<>();
        Matcher m = Pattern.compile("^export interface (\\w+)\\s+extends\\s+(\\w+)",
                Pattern.MULTILINE).matcher(ts);
        while (m.find()) found.put(m.group(1), m.group(2));
        return found;
    }

    /**
     * Interface name to body. Bodies run to the first closing brace in column
     * one, which holds because the file is formatted that way and is checked by
     * the interface-count assertion above.
     */
    private static Map<String, String> parseInterfaces(String ts) {
        Map<String, String> found = new LinkedHashMap<>();
        Matcher m = Pattern.compile("^export interface (\\w+)[^{]*\\{", Pattern.MULTILINE).matcher(ts);
        while (m.find()) {
            int end = ts.indexOf("\n}", m.end());
            found.put(m.group(1), end < 0 ? ts.substring(m.end()) : ts.substring(m.end(), end));
        }
        return found;
    }

    /**
     * Field names in an interface body: an identifier that opens a line and is
     * followed by an optional {@code ?} and a colon. Nested object literals
     * indent further and are picked up too, which only ever makes this more
     * forgiving — a name that exists somewhere in the body is not the drift
     * this test is looking for.
     */
    private static Set<String> parseFieldNames(String body) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = Pattern.compile("^\\s+(\\w+)\\??\\s*:", Pattern.MULTILINE).matcher(body);
        while (m.find()) names.add(m.group(1));
        return names;
    }
}
