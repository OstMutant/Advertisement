package org.ost.advertisement.ui.views.rules;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;

@RequiredArgsConstructor
public abstract class ComponentBuilder<T extends Configurable<T, P>, P> {
    protected abstract ObjectProvider<T> getProvider();

    public T build(P params) {
        return getProvider().getObject().configure(params);
    }
}
