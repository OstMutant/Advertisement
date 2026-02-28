package org.ost.advertisement.ui.views.rules;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class ComponentBuilder<T extends Configurable<T, P>, P> implements Provider<T> {

    public T build(@NonNull P params) {
        return build().configure(params);
    }
}
