package org.ost.advertisement.ui.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ost.advertisement.entities.Role;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class UserEditDto {

	private Long id;

	private String name;
	private String email;
	private String passwordHash;

	private Role role;

	private Instant createdAt;
	private Instant updatedAt;

	private String locale;

}
