package org.ost.advertisement.repository.query.filter;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinition;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public abstract class FilterApplier<F> {

	protected final List<FilterRelation<F>> relations = new ArrayList<>();

	public String apply(MapSqlParameterSource params, @NotNull F filter) {
		FieldsConditions fc = new FieldsConditions();
		relations.forEach(r -> r.applyConditions(filter, fc));
		fc.toParams().forEach(params::addValue);
		return fc.toSqlApplyingAnd();
	}

	@FunctionalInterface
	public interface FilterApplierFunction<F> {

		void apply(F filter, FieldsConditions fc, FilterRelation<F> relation);
	}

	public static <F> FilterRelation<F> of(
		String filterField,
		SqlDtoFieldDefinition<?> relation,
		FilterApplierFunction<F> fn
	) {
		return new SimpleFilterRelation<>(filterField, relation, fn);
	}

	public record SimpleFilterRelation<F>(
		String filterField,
		SqlDtoFieldDefinition<?> relation,
		FilterApplierFunction<F> fn
	) implements FilterRelation<F> {

		@Override
		public String getFilterField() {
			return filterField;
		}

		@Override
		public String getSqlField() {
			return relation.sqlField();
		}

		@Override
		public void applyConditions(F filter, FieldsConditions fc) {
			fn.apply(filter, fc, this);
		}
	}
}
