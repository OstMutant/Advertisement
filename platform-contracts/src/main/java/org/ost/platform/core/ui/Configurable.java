package org.ost.platform.core.ui;

public interface Configurable<T, P> {
    T configure(P params);
}
