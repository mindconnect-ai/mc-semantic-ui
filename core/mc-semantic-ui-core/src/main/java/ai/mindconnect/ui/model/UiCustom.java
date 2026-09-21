package ai.mindconnect.ui.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A node whose type is set at run time — for a node type a plugin renders in
 * the browser with no Java class of its own. The plugin's extension module
 * calls {@code renderer.register("chat-widget", fn)}; the server sends
 *
 * <pre>{@code
 * UiCustom.of("chat-widget")
 *         .id("draft-chat")
 *         .prop("session", sid)
 *         .prop("api", "/chat-api/sessions");
 * }</pre>
 *
 * which goes over the wire flat, like any node:
 * {@code {"type":"chat-widget","id":"draft-chat","session":"…","api":"…"}}.
 *
 * <p>The fields every node has ({@code id}, {@code title}, {@code cssClass},
 * the triggers, {@code display}) work as usual; a property cannot overwrite
 * one of them. The type is lowercase-kebab and not one of the core types.
 *
 * <p>Reading JSON back, any type Jackson does not know becomes a
 * {@code UiCustom} with the other fields as its properties ({@link UiNode}
 * names it the {@code defaultImpl}), so the editor, JavaFX and tests can hold
 * a plugin's node without its class. Known types are read as before.
 *
 * <p>Server rendering picks {@code templates/sui/<type>.hbs} when a jar ships
 * one; without it the node renders as an empty
 * {@code <div class="sui-custom-missing" data-type="…">}, which the plugin's
 * browser renderer replaces once the page is live.
 */
@JsonSerialize(using = UiCustomSerializer.class)
// UiNode ignores the "type" property the type id makes visible; a custom
// node is the one that reads it.
@JsonIgnoreProperties(value = {})
public class UiCustom extends UiNode {

    private static final Pattern TYPE = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

    /** The type names of the core nodes — a custom node may not take one. */
    public static final Set<String> CORE_TYPES = coreTypes();

    /** The names of the fields every node has, plus {@code type}: not usable as properties. */
    public static final Set<String> RESERVED = reserved();

    private String type;
    private final Map<String, Object> props = new LinkedHashMap<>();

    /** For Jackson. */
    public UiCustom() { }

    /**
     * A node of the type the client renderer is registered for.
     *
     * @throws IllegalArgumentException if the type is not lowercase-kebab or
     *         is a core type
     */
    public static UiCustom of(String type) {
        UiCustom node = new UiCustom();
        node.setType(type);
        return node;
    }

    /** A node of {@code type} with {@code id}. */
    public static UiCustom of(String id, String type) {
        return of(type).id(id);
    }

    /** The type name the node is sent with. */
    @JsonIgnore
    public String getType() {
        return type;
    }

    /**
     * Sets the type name.
     *
     * @throws IllegalArgumentException if it is not lowercase-kebab or is a core type
     */
    @JsonProperty("type")
    public void setType(String type) {
        if (type == null || !TYPE.matcher(type).matches() || type.length() > 64) {
            throw new IllegalArgumentException("custom node type must be lowercase-kebab, e.g. \"chat-widget\": " + type);
        }
        if (CORE_TYPES.contains(type)) {
            throw new IllegalArgumentException("custom node type \"" + type + "\" is a core type; pick another name");
        }
        this.type = type;
    }

    /**
     * Sets a property; {@code null} removes it.
     *
     * @throws IllegalArgumentException if the name is empty or one of {@link #RESERVED}
     */
    @JsonAnySetter
    public UiCustom prop(String name, Object value) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("a property needs a name");
        if (RESERVED.contains(name)) {
            throw new IllegalArgumentException("\"" + name + "\" is a field every node has; set it with its own method, not as a property");
        }
        if (value == null) props.remove(name);
        else props.put(name, value);
        return this;
    }

    /** One property, or null. */
    public Object prop(String name) {
        return props.get(name);
    }

    /** The properties, in the order they were set — written flat next to the node's fields. */
    @JsonAnyGetter
    public Map<String, Object> getProps() {
        return Collections.unmodifiableMap(props);
    }

    /** Fluent {@link #setId}. */
    public UiCustom id(String id) {
        setId(id);
        return this;
    }

    /** Fluent {@link #setTitle}. */
    public UiCustom title(String title) {
        setTitle(title);
        return this;
    }

    /** Fluent {@link #setCssClass}. */
    public UiCustom cssClass(String cssClass) {
        setCssClass(cssClass);
        return this;
    }

    /** Fluent {@link #setOnClick}. */
    public UiCustom onClick(UiTrigger trigger) {
        setOnClick(trigger);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UiCustom other && super.equals(o)
                && java.util.Objects.equals(type, other.type) && props.equals(other.props);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), type, props);
    }

    @Override
    public String toString() {
        return "UiCustom(type=" + type + ", id=" + getId() + ", props=" + props + ")";
    }

    private static Set<String> coreTypes() {
        Set<String> names = new LinkedHashSet<>();
        JsonSubTypes subTypes = UiNode.class.getAnnotation(JsonSubTypes.class);
        if (subTypes != null) for (JsonSubTypes.Type t : subTypes.value()) names.add(t.name());
        return Collections.unmodifiableSet(names);
    }

    private static Set<String> reserved() {
        Set<String> names = new LinkedHashSet<>();
        names.add("type");
        for (Field f : UiNode.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) names.add(f.getName());
        }
        return Collections.unmodifiableSet(names);
    }
}
