package org.ost.platform.ui;

public interface Configurable<T, P> {
    T configure(P params);
}
