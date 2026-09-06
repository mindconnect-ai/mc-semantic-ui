package ai.mindconnect.ui.stateful;

/**
 * Creates a view object for one request. Under Spring the implementation
 * autowires the constructor; the default here needs a public no-arg
 * constructor.
 */
@FunctionalInterface
public interface ViewFactory {

    <V extends SuiView<?>> V create(Class<V> viewClass);

    /** Reflection, no injection. */
    static ViewFactory reflective() {
        return new ViewFactory() {
            @Override
            public <V extends SuiView<?>> V create(Class<V> viewClass) {
                try {
                    return viewClass.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("cannot instantiate " + viewClass.getName()
                            + " — give it a public no-arg constructor or supply a ViewFactory", e);
                }
            }
        };
    }
}
