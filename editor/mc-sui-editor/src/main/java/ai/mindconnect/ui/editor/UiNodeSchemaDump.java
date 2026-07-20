package ai.mindconnect.ui.editor;

import ai.mindconnect.ui.model.UiNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build-time generator: derives a JSON Schema straight from the real
 * {@link UiNode} classes and writes it to {@code ui-node.schema.json}. The
 * Monaco JSON editor in the property panel loads it and validates the selected
 * node against it (types, enum values, polymorphic {@code type} subtypes).
 *
 * <p>Using the Jackson module means the schema honours exactly what the wire
 * format is: {@code @JsonSubTypes} become the {@code type}-discriminated
 * variants, Java enums become {@code enum} constraints, and {@code @JsonInclude}
 * / property naming match the serialised shape. Because it is generated from
 * the classes, it can never drift from the model.
 *
 * <p>Draft-07 is emitted deliberately — it is the dialect Monaco's JSON
 * language service validates most completely.
 *
 * <p>Usage: {@code UiNodeSchemaDump <output-dir>} (defaults to
 * {@code target/sui-editor-data}); writes {@code ui-node.schema.json} there.
 */
public final class UiNodeSchemaDump {

    private UiNodeSchemaDump() {}

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args.length > 0 ? args[0] : "target/sui-editor-data");
        Files.createDirectories(outDir);
        write(outDir);
        System.out.println("UiNodeSchemaDump: wrote ui-node.schema.json to " + outDir.toAbsolutePath());
    }

    /** Generates {@code ui-node.schema.json} into {@code outDir}. */
    public static void write(Path outDir) throws IOException {
        var jackson = new JacksonModule(
                JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);

        SchemaGeneratorConfig config = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON)
                .with(jackson)
                .build();

        JsonNode schema = new SchemaGenerator(config).generateSchema(UiNode.class);

        Files.writeString(outDir.resolve("ui-node.schema.json"),
                schema.toPrettyString(), StandardCharsets.UTF_8);
    }
}
