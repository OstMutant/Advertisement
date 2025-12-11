package org.ost.advertisement.ui.views.components.query.filter.meta;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.ost.advertisement.services.ValidationService;

public record FilterFieldMeta<I, F, R>(
	String name,
	Function<F, R> getter,
	BiConsumer<F, I> setter,
	BiPredicate<ValidationService<F>, F> validation) {

	public static <I, F, T> FilterFieldMeta<I, F, T> of(
		String name,
		Function<F, T> getter,
		BiConsumer<F, I> setter) {
		return of(name, getter, setter, (validation, dto) -> validation.isValidProperty(dto, name));
	}

	public static <I, F, T> FilterFieldMeta<I, F, T> of(
		String name,
		Function<F, T> getter,
		BiConsumer<F, I> setter,
		BiPredicate<ValidationService<F>, F> validation) {
		return new FilterFieldMeta<>(name, getter, setter, validation);
	}

	public boolean hasValue(F filter) {
		R value = getter().apply(filter);
		return value != null && !(value instanceof String s && s.isBlank());
	}

	public String getValueAsString(F filter) {
		R value = getter().apply(filter);
		return value == null ? "" : value.toString();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof FilterFieldMeta<?, ?, ?> that && Objects.equals(this.name, that.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}
}
