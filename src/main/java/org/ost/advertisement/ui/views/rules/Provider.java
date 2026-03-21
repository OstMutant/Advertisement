package org.ost.advertisement.ui.views.rules;

import org.springframework.beans.factory.ObjectProvider;

public interface Provider<T> {
    ObjectProvider<T> getProvider();

    default T build() {
        return getProvider().getObject();
    }
}
