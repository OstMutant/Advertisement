package org.ost.advertisement.mappers.filters;

public interface FilterMapper<T> {

    void update(T target, T source);

    T copy(T source);
}
