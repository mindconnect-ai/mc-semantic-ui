package ai.mindconnect.ui.stateful.spring;

import ai.mindconnect.ui.stateful.SuiView;
import ai.mindconnect.ui.stateful.ViewFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

/**
 * Creates a view per request with constructor injection, the way Spring
 * would create a prototype bean — the view class itself needs no annotation.
 */
public class SpringViewFactory implements ViewFactory {

    private final AutowireCapableBeanFactory beanFactory;

    public SpringViewFactory(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public <V extends SuiView<?>> V create(Class<V> viewClass) {
        return beanFactory.createBean(viewClass);
    }
}
