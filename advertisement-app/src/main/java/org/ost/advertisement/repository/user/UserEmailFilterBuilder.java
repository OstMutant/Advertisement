package org.ost.advertisement.repository.user;

import org.ost.sqlengine.filter.FilterBuilder;
import org.ost.sqlengine.filter.SqlCondition;

import static org.ost.sqlengine.filter.DefaultFilterBinding.of;
import static org.ost.advertisement.repository.user.UserProjection.EMAIL;

public class UserEmailFilterBuilder extends FilterBuilder<String> {

    public UserEmailFilterBuilder() {
        relations.add(of("email", EMAIL, SqlCondition::equalsTo));
    }
}
