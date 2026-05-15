package org.ost.advertisement.ui.rules;

public interface Configurable<T, P> {
    T configure(P params);
}
