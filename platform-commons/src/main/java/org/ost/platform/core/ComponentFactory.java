package org.ost.platform.core;

import lombok.NonNull;
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
