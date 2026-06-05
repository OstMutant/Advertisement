package org.ost.platform.core;

import lombok.NonNull;
import org.ost.platform.ui.Configurable;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.function.Consumer;

public class ComponentFactory<T> {

    private final ObjectProvider<T> provider;

    public ComponentFactory(@NonNull ObjectProvider<T> provider) {
        this.provider = provider;
    }

    public T get() {
        return provider.getObject();
    }

    @SuppressWarnings("unchecked")
    public <P> T build(@NonNull P params) {
        return ((Configurable<T, P>) provider.getObject()).configure(params);
    }

    @SuppressWarnings("unchecked")
    public <P, R> R buildAs(@NonNull P params) {
        return (R) build(params);
    }

    public T getIfAvailable() {
        return provider.getIfAvailable();
    }

    public Optional<T> findIfAvailable() {
        return Optional.ofNullable(provider.getIfAvailable());
    }

    public void ifAvailable(@NonNull Consumer<T> consumer) {
        provider.ifAvailable(consumer);
    }
}
