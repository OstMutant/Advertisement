package org.ost.advertisement.dto.filter;

public interface FilterValidation<T extends FilterValidation<T>> {

	boolean isValid();
}
