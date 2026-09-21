package ai.mindconnect.ui.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.ResolvableSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.util.NameTransformer;

import java.io.IOException;

/**
 * Writes a {@link UiCustom} with its own type name where Jackson would write
 * the class's: {@code {"type":"chat-widget","id":…,…props}}. The fields
 * themselves are written by Jackson's ordinary bean serializer for the class —
 * the node's standard fields and, through the any-getter, its properties — so
 * a field added to {@link UiNode} later is written here too.
 */
final class UiCustomSerializer extends StdSerializer<UiCustom> {

    UiCustomSerializer() {
        super(UiCustom.class);
    }

    @Override
    public void serialize(UiCustom value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject(value);
        gen.writeStringField("type", value.getType());
        fields(provider).serialize(value, gen, provider);
        gen.writeEndObject();
    }

    @Override
    public void serializeWithType(UiCustom value, JsonGenerator gen, SerializerProvider provider,
                                  TypeSerializer typeSer) throws IOException {
        WritableTypeId typeId = typeSer.typeId(value, JsonToken.START_OBJECT);
        typeId.id = value.getType();
        typeSer.writeTypePrefix(gen, typeId);
        fields(provider).serialize(value, gen, provider);
        typeSer.writeTypeSuffix(gen, typeId);
    }

    /**
     * The fields only — no braces — from the bean serializer Jackson would
     * build for the class were it not for this one.
     */
    @SuppressWarnings("unchecked")
    private static JsonSerializer<Object> fields(SerializerProvider provider) throws JsonMappingException {
        JavaType type = provider.constructType(UiCustom.class);
        BeanDescription description = provider.getConfig().introspect(type);
        JsonSerializer<Object> bean = Beans.INSTANCE.beanSerializer(provider, type, description);
        if (bean instanceof ResolvableSerializer resolvable) resolvable.resolve(provider);
        return bean.unwrappingSerializer(NameTransformer.NOP);
    }

    /** Opens up the factory method that builds a plain bean serializer, bypassing the class's {@code @JsonSerialize}. */
    private static final class Beans extends BeanSerializerFactory {
        static final Beans INSTANCE = new Beans();

        private Beans() {
            super(null);
        }

        @SuppressWarnings("unchecked")
        JsonSerializer<Object> beanSerializer(SerializerProvider provider, JavaType type, BeanDescription description)
                throws JsonMappingException {
            return (JsonSerializer<Object>) constructBeanOrAddOnSerializer(provider, type, description,
                    provider.isEnabled(com.fasterxml.jackson.databind.MapperFeature.USE_STATIC_TYPING));
        }
    }
}
