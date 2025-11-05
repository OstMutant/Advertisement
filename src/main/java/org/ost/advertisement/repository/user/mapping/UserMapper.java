package org.ost.advertisement.repository.user.mapping;

import static org.ost.advertisement.entities.User.Fields.createdAt;
import static org.ost.advertisement.entities.User.Fields.email;
import static org.ost.advertisement.entities.User.Fields.id;
import static org.ost.advertisement.entities.User.Fields.locale;
import static org.ost.advertisement.entities.User.Fields.name;
import static org.ost.advertisement.entities.User.Fields.passwordHash;
import static org.ost.advertisement.entities.User.Fields.role;
import static org.ost.advertisement.entities.User.Fields.updatedAt;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.id;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.instant;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.str;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.query.mapping.FieldRelations;
import org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinition;

public class UserMapper extends FieldRelations<User> {

	public static final SqlDtoFieldDefinition<Long> ID = id(id, "u.id");
	public static final SqlDtoFieldDefinition<String> NAME = str(name, "u.name");
	public static final SqlDtoFieldDefinition<String> EMAIL = str(email, "u.email");
	public static final SqlDtoFieldDefinition<String> ROLE = str(role, "u.role");
	public static final SqlDtoFieldDefinition<String> PASSWORD_HASH = str(passwordHash, "u.password_hash");
	public static final SqlDtoFieldDefinition<Instant> CREATED_AT = instant(createdAt, "u.created_at");
	public static final SqlDtoFieldDefinition<Instant> UPDATED_AT = instant(updatedAt, "u.updated_at");
	public static final SqlDtoFieldDefinition<String> LOCALE = str(locale, "u.locale");

	public UserMapper() {
		super(List.of(ID, NAME, EMAIL, ROLE, PASSWORD_HASH, CREATED_AT, UPDATED_AT, LOCALE), "user_information u");
	}

	@Override
	public User mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
		return User.builder()
			.id(ID.extract(rs))
			.name(NAME.extract(rs))
			.email(EMAIL.extract(rs))
			.role(Role.valueOf(ROLE.extract(rs)))
			.passwordHash(PASSWORD_HASH.extract(rs))
			.createdAt(CREATED_AT.extract(rs))
			.updatedAt(UPDATED_AT.extract(rs))
			.locale(LOCALE.extract(rs))
			.build();
	}
}
