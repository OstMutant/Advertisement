package org.ost.advertisement.dto;

public interface Filter<T extends Filter<T>> {

	void clear();

	void copyFrom(T f);

	T copy();
}
