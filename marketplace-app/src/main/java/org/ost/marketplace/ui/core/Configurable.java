package org.ost.marketplace.ui.core;

public interface Configurable<T, P> {
    T configure(P params);
}
