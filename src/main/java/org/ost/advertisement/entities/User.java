package org.ost.advertisement.entities;

import java.time.Instant;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Table("user_information")
public class User implements EntityMarker {

	@Id
	private Long id;

	private String name;
	private String email;
	private String passwordHash;

	private Role role;

	@CreatedDate
	private Instant createdAt;
	@LastModifiedDate
	private Instant updatedAt;

	private String locale;

	@Override
	public Long getOwnerUserId() {
		return id;
	}

	public Locale getLocaleAsObject() {
		return locale != null ? Locale.forLanguageTag(locale) : Locale.getDefault();
	}

	public void setLocale(Locale locale) {
		this.locale = locale != null ? locale.toLanguageTag() : null;
	}
}
