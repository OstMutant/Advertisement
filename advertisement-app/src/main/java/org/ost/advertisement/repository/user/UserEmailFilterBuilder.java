package org.ost.advertisement.repository.user;

import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.filter.SqlCondition;

import java.util.List;

import static org.ost.sqlengine.filter.SqlBoundFilter.of;
import static org.ost.advertisement.repository.user.UserDescriptor.EMAIL;

public class UserEmailFilterBuilder extends SqlFilterBuilder<String> {

    public UserEmailFilterBuilder() {
        super(List.of(of("email", EMAIL, SqlCondition::equalsTo)));
    }
}
