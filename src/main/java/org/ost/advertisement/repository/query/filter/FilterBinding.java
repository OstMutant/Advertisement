package org.ost.advertisement.repository.query.filter;

public interface FilterBinding<F, R> extends FilterMapping {

	SqlCondition<R> getCondition(F value);
}

