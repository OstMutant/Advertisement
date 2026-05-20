package org.ost.platform.core.ui;

import org.springframework.beans.factory.ObjectProvider;

public interface Provider<T> {
    ObjectProvider<T> getProvider();

    default T build() {
        return getProvider().getObject();
    }
}
