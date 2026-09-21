package ai.mindconnect.ui.assets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * One file a page needs from a jar: a stylesheet, an ES module, or an
 * extension — an ES module whose {@code install(renderer, { bus })} registers
 * node types on the page's renderer.
 *
 * <p>Declared statically in a jar's {@code META-INF/sui/assets.json}, by a
 * {@link SuiAssetContribution} bean, or at run time through
 * {@link SuiAssetRegistry#register}. Several declarations may share an
 * {@link #id}; the one with the highest {@link #order} wins, and a winner that
 * is {@link #disabled} takes the asset off the page. See
 * {@link SuiAssetRegistry} for the rules.
 *
 * @param id       unique name, e.g. {@code calendar} or {@code calendar.css}
 * @param kind     what the file is; may be null only for a disabling entry
 * @param href     where the browser gets it — a path on this server, starting
 *                 with {@code /}; may be null only for a disabling entry
 * @param order    load order, and the rank among declarations of the same id
 *                 (higher wins); 0 by default
 * @param disabled when true, this declaration takes the asset off the page
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuiAsset(
        @JsonProperty("id") String id,
        @JsonProperty("kind") Kind kind,
        @JsonProperty("href") String href,
        @JsonProperty("order") int order,
        @JsonProperty("disabled") boolean disabled) {

    /** What a file is, and so what the page does with it. */
    public enum Kind {
        /** A stylesheet: linked in the page's head. */
        CSS,
        /** An ES module: imported, nothing more. */
        MODULE,
        /** An ES module exporting {@code install(renderer, { bus })}: imported, then installed. */
        EXTENSION;

        @JsonValue
        public String json() {
            return name().toLowerCase(Locale.ROOT);
        }

        @JsonCreator
        public static Kind of(String value) {
            if (value == null) return null;
            return Kind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    @JsonCreator
    public SuiAsset {
    }

    /** A stylesheet at {@code href}. */
    public static SuiAsset css(String id, String href) {
        return new SuiAsset(id, Kind.CSS, href, 0, false);
    }

    /** An ES module at {@code href}, imported and nothing more. */
    public static SuiAsset module(String id, String href) {
        return new SuiAsset(id, Kind.MODULE, href, 0, false);
    }

    /** An extension module at {@code href}: imported, then {@code install(renderer, { bus })}. */
    public static SuiAsset extension(String id, String href) {
        return new SuiAsset(id, Kind.EXTENSION, href, 0, false);
    }

    /** Takes the asset {@code id} off the page — when this declaration's order is the highest for it. */
    public static SuiAsset disabled(String id, int order) {
        return new SuiAsset(id, null, null, order, true);
    }

    /** The same asset with another order. */
    public SuiAsset withOrder(int newOrder) {
        return new SuiAsset(id, kind, href, newOrder, disabled);
    }
}
