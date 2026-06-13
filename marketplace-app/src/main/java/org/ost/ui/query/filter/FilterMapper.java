package org.ost.ui.query.filter;

public interface FilterMapper<T> {

    void update(T target, T source);

    T copy(T source);
}
