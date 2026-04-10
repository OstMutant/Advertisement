package org.ost.sqlengine.filter;

public interface FilterBinding<F, R> extends FilterMapping {

    SqlCondition<R> getCondition(F value);
}

