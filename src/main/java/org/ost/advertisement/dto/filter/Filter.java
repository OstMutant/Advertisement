package org.ost.advertisement.dto.filter;

public interface Filter<T extends Filter<T>> {

	void clear();

	void copyFrom(T f);

	T copy();

	boolean isValid();
}
