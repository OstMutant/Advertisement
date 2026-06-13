package org.ost.query.ui.filter;

public interface FilterMapper<T> {

    void update(T target, T source);

    T copy(T source);
}
