package org.ost.sqlengine.filter;

public interface SqlFilterBinding<F, R> extends SqlFilterMapping {

    SqlCondition<R> getCondition(F value);
}
