package ai.mindconnect.ui.editor;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build-time exporter that turns the {@link NodeRegistry} into two static JSON
 * files the backend-free (standalone) editor consumes instead of the
 * {@code /editor/api/schema} and {@code /editor/api/default/{type}} endpoints:
 *
 * <ul>
 *   <li>{@code schema.json}   — the node catalogue ({@code registry.all()}),
 *       byte-compatible with what {@code GET /editor/api/schema} returns, so
 *       the standalone backend can reuse the same list→map conversion.</li>
 *   <li>{@code defaults.json} — a {@code {type → default instance}} map, one
 *       fresh node per registered type, matching what
 *       {@code GET /editor/api/default/{type}} would return.</li>
 * </ul>
 *
 * <p>Keeping this a plain {@code main} (invoked from the standalone module's
 * build via exec-maven-plugin) means the {@code NodeRegistry} stays the single
 * source of truth — the standalone app never hand-mirrors the Java catalogue.
 *
 * <p>Usage: {@code SchemaDump <output-dir>} (defaults to {@code target/sui-editor-data}).
 */
public final class SchemaDump {

    private SchemaDump() {}

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args.length > 0 ? args[0] : "target/sui-editor-data");
        Files.createDirectories(outDir);
        write(outDir);
        System.out.println("SchemaDump: wrote schema.json + defaults.json to " + outDir.toAbsolutePath());
    }

    /** Writes {@code schema.json} and {@code defaults.json} into {@code outDir}. */
    public static void write(Path outDir) throws IOException {
        var registry = new NodeRegistry();

        // NON_NULL so the emitted defaults stay compact — the renderer only
        // reads the fields it knows about, and absent-vs-null is identical to
        // it, so dropping nulls just trims noise from the bundled JSON.
        var mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var pretty = mapper.writerWithDefaultPrettyPrinter();

        // schema.json — the NodeMeta list (factory field is @JsonIgnore'd).
        pretty.writeValue(outDir.resolve("schema.json").toFile(), registry.all());

        // defaults.json — one factory-built instance per type, keyed by type.
        //
        // Each node must be serialised with UiNode as the *declared* root type
        // so Jackson's @JsonTypeInfo emits the "type" discriminator the
        // renderer keys on. Writing a Map<String,UiNode> would erase that
        // static type and drop the discriminator, so we serialise each node
        // through writerFor(UiNode.class) and reassemble the tree by hand.
        ObjectWriter uiWriter = mapper.writerFor(UiNode.class);
        ObjectNode defaults = mapper.createObjectNode();
        for (var meta : registry.all()) {
            UiNode node = meta.getFactory().get();
            defaults.set(meta.getType(), mapper.readTree(uiWriter.writeValueAsString(node)));
        }
        pretty.writeValue(outDir.resolve("defaults.json").toFile(), defaults);
    }
}
