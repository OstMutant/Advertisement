package org.ost.advertisement.repository.query.filter;

public interface FilterRelation<F, R> extends FilterProjection {

	Condition<R> getCondition(F value);
}

