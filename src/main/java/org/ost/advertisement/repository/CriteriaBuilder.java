package org.ost.advertisement.repository;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.relational.core.query.Criteria;

public class CriteriaBuilder {

	private final List<Criteria> criteriaList = new ArrayList<>();

	public CriteriaBuilder like(String column, String value) {
		if (value != null && !value.isBlank()) {
			criteriaList.add(Criteria.where(column).like("%" + value.toLowerCase() + "%").ignoreCase(true));
		}
		return this;
	}

	public CriteriaBuilder equal(String column, String value) {
		if (value != null && !value.isBlank()) {
			criteriaList.add(Criteria.where(column).is(value).ignoreCase(true));
		}
		return this;
	}

	public CriteriaBuilder range(String column, Comparable<?> start, Comparable<?> end) {
		if (start != null) {
			criteriaList.add(Criteria.where(column).greaterThanOrEquals(start));
		}
		if (end != null) {
			criteriaList.add(Criteria.where(column).lessThanOrEquals(end));
		}
		return this;
	}

	public Criteria build() {
		return criteriaList.stream().reduce(Criteria.empty(), Criteria::and);
	}
}


