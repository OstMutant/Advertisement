package org.ost.advertisement.repository.query.filter;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public abstract class FilterApplier<F> {

	protected final List<FilterRelation<F>> relations = new ArrayList<>();

	public String apply(MapSqlParameterSource params, @NotNull F filter) {
		FieldsConditions fc = new FieldsConditions();
		relations.forEach(r -> r.applyConditions(filter, fc));
		fc.toParams().forEach(params::addValue);
		return fc.toSqlApplyingAnd();
	}

}
