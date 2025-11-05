package org.ost.advertisement.repository.user.filter;

import static org.ost.advertisement.repository.user.mapping.UserMapper.EMAIL;

import org.ost.advertisement.repository.query.filter.FilterApplier;

public class UserEmailFilterApplier extends FilterApplier<String> {

	public UserEmailFilterApplier() {
		relations.add(of("email", EMAIL, (email, fc, r) -> r.equalsTo(email, fc)));
	}
}
