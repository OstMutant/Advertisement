package org.ost.advertisement.repository.query.filter;

import org.ost.advertisement.repository.query.meta.SqlFieldDefinition;

public record SimpleFilterRelation<F>(
	String filterField,
	SqlFieldDefinition<?> relation,
	FilterApplierFunction<F> fn
) implements FilterRelation<F> {

	public static <F> SimpleFilterRelation<F> of(
		String filterField,
		SqlFieldDefinition<?> relation,
		FilterApplierFunction<F> fn
	) {
		return new SimpleFilterRelation<>(filterField, relation, fn);
	}

	@Override
	public String getFilterField() {
		return filterField;
	}

	@Override
	public String getSqlField() {
		return relation.sqlExpression();
	}

	@Override
	public void applyConditions(F filter, FieldsConditions fc) {
		fn.apply(filter, fc, this);
	}

	@FunctionalInterface
	public interface FilterApplierFunction<F> {

		void apply(F filter, FieldsConditions fc, FilterRelation<F> relation);
	}
}
