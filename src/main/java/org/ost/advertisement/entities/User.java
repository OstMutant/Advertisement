package org.ost.advertisement.entities;

import java.time.Instant;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ost.advertisement.security.UserIdMarker;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@Table("user_information")
@Getter
@Setter
public class User implements UserIdMarker {

	@Id
	private Long id;

	private String name;
	private String email;
	private String passwordHash;

	private Role role;

	private Instant createdAt;
	private Instant updatedAt;

	private String locale;

	@Override
	public Long getUserId() {
		return id;
	}

	public Locale getLocaleAsObject() {
		return locale != null ? Locale.forLanguageTag(locale) : Locale.getDefault();
	}

	public void setLocale(Locale locale) {
		this.locale = locale != null ? locale.toLanguageTag() : null;
	}
}
