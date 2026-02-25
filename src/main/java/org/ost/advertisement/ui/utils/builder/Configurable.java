package org.ost.advertisement.ui.utils.builder;

public interface Configurable<T, P> {
    T configure(P params);
}
