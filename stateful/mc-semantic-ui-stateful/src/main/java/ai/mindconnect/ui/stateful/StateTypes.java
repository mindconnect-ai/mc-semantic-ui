package ai.mindconnect.ui.stateful;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Finds the {@code S} of a concrete {@code SuiView<S>} subclass, so the
 * engine can deserialise the state without asking the view to spell its
 * state type twice.
 */
final class StateTypes {

    private StateTypes() {
    }

    /** The resolved state type, or a helpful exception when it cannot be resolved. */
    static Type resolve(Class<?> viewClass) {
        Class<?> c = viewClass;
        while (c != null && c != Object.class) {
            Type generic = c.getGenericSuperclass();
            if (generic instanceof ParameterizedType p && p.getRawType() == SuiView.class) {
                Type arg = p.getActualTypeArguments()[0];
                if (arg instanceof TypeVariable<?>) {
                    break;
                }
                return arg;
            }
            c = c.getSuperclass();
        }
        throw new IllegalArgumentException(viewClass.getName()
                + " must extend SuiView<S> with a concrete S so its state can be (de)serialised");
    }
}
