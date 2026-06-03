package org.ost.platform.ui;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.function.Consumer;

public class ComponentFactory {

    private final ConfigurableListableBeanFactory beanFactory;

    public ComponentFactory(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public <T> T get(Class<T> type) {
        return beanFactory.getBean(type);
    }

    public <T extends Configurable<T, P>, P> T build(Class<T> type, P params) {
        return beanFactory.getBean(type).configure(params);
    }

    public <T> T getIfAvailable(Class<T> type) {
        return beanFactory.getBeanProvider(type).getIfAvailable();
    }

    public <T> void ifAvailable(Class<T> type, Consumer<T> consumer) {
        beanFactory.getBeanProvider(type).ifAvailable(consumer);
    }
}
