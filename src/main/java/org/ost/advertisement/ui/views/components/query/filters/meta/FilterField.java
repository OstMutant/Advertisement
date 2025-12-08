package org.ost.advertisement.ui.views.components.query.filters.meta;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.ost.advertisement.services.ValidationService;

public record FilterField<I, F, R>(
	String name,
	Function<F, R> getter,
	BiConsumer<F, I> setter,
	BiPredicate<ValidationService<F>, F> validation) {

	public static <I, F, T> FilterField<I, F, T> of(
		String name,
		Function<F, T> getter,
		BiConsumer<F, I> setter) {
		return of(name, getter, setter, (validation, dto) -> validation.isValidProperty(dto, name));
	}

	public static <I, F, T> FilterField<I, F, T> of(
		String name,
		Function<F, T> getter,
		BiConsumer<F, I> setter,
		BiPredicate<ValidationService<F>, F> validation) {
		return new FilterField<>(name, getter, setter, validation);
	}

	public boolean hasValue(F filter) {
		R value = getter().apply(filter);
		return value != null && !(value instanceof String s && s.isBlank());
	}

	public String getValueAsString(F filter) {
		R value = getter().apply(filter);
		return value == null ? "" : value.toString();
	}
}
