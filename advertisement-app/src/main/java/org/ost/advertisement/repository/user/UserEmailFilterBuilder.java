package org.ost.advertisement.repository.user;

import org.ost.sqlengine.filter.FilterBuilder;
import org.ost.sqlengine.filter.SqlCondition;

import java.util.List;

import static org.ost.sqlengine.filter.DefaultFilterBinding.of;
import static org.ost.advertisement.repository.user.UserProjection.EMAIL;

public class UserEmailFilterBuilder extends FilterBuilder<String> {

    public UserEmailFilterBuilder() {
        super(List.of(of("email", EMAIL, SqlCondition::equalsTo)));
    }
}
