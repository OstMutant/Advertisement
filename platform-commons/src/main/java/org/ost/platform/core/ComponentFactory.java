package org.ost.platform.core;

import lombok.NonNull;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.function.Consumer;

/** Typed access to an optional starter's port or hook: the absent-safe reads answer null or an empty result, {@link #get()} fails with a typed exception when nothing is installed. */
public class ComponentFactory<T> {

    private final ObjectProvider<T> provider;

    public ComponentFactory(@NonNull ObjectProvider<T> provider) {
        this.provider = provider;
    }

    /** The component, or {@link OptionalComponentUnavailableException} naming its type when none is installed; several candidates stay Spring's own exception. */
    public T get() {
        try {
            return provider.getObject();
        } catch (NoSuchBeanDefinitionException e) {
            if (e instanceof NoUniqueBeanDefinitionException) {
                throw e;
            }
            throw new OptionalComponentUnavailableException(e.getResolvableType().toClass(), e);
        }
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
