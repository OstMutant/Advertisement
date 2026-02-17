package org.ost.advertisement.repository.user;

import org.ost.advertisement.repository.query.filter.FilterBuilder;
import org.ost.advertisement.repository.query.filter.SqlCondition;

import static org.ost.advertisement.repository.query.filter.DefaultFilterBinding.of;
import static org.ost.advertisement.repository.user.UserProjection.EMAIL;

public class UserEmailFilterBuilder extends FilterBuilder<String> {

    public UserEmailFilterBuilder() {
        relations.add(of("email", EMAIL, SqlCondition::equalsTo));
    }
}
