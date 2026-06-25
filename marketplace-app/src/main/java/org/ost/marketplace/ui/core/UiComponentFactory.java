package org.ost.marketplace.ui.core;

import lombok.NonNull;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;

public class UiComponentFactory<T> extends ComponentFactory<T> {

    public UiComponentFactory(@NonNull ObjectProvider<T> provider) {
        super(provider);
    }

    @SuppressWarnings("unchecked")
    public <P> T build(@NonNull P params) {
        return ((Configurable<T, P>) get()).configure(params);
    }
}
