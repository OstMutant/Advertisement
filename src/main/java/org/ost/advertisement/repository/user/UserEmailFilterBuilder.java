package org.ost.advertisement.repository.user;

import static org.ost.advertisement.repository.query.filter.DefaultFilterBinding.of;
import static org.ost.advertisement.repository.user.UserProjection.EMAIL;

import org.ost.advertisement.repository.query.filter.SqlCondition;
import org.ost.advertisement.repository.query.filter.FilterBuilder;

public class UserEmailFilterBuilder extends FilterBuilder<String> {

	public UserEmailFilterBuilder() {
		relations.add(of("email", EMAIL, SqlCondition::equalsTo));
	}
}
