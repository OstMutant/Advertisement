package org.ost.advertisement.entities;

import java.time.Instant;
import java.util.Locale;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Value
@Builder
@FieldNameConstants
@Table("user_information")
public class User implements EntityMarker {

	@Id
	Long id;
	String name;
	String email;
	String passwordHash;
	Role role;

	@CreatedDate
	Instant createdAt;

	@LastModifiedDate
	Instant updatedAt;

	String locale;

	@Override
	public Long getOwnerUserId() {
		return id;
	}

	public Locale getLocaleAsObject() {
		return locale != null ? Locale.forLanguageTag(locale) : Locale.getDefault();
	}

	public User withLocale(Locale newLocale) {
		return new User(
			id, name, email, passwordHash, role, createdAt, updatedAt,
			newLocale != null ? newLocale.toLanguageTag() : null
		);
	}
}
