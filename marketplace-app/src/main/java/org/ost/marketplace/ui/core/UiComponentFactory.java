package org.ost.marketplace.ui.core;

import lombok.NonNull;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;

public class UiComponentFactory<T extends Configurable<T, P>, P> extends ComponentFactory<T> {

    public UiComponentFactory(@NonNull ObjectProvider<T> provider) {
        super(provider);
    }

    public T build(@NonNull P params) {
        return get().configure(params);
    }
}
