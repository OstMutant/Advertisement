package org.ost.advertisement.repository.user;

import static org.ost.advertisement.meta.fields.SqlDtoFieldDefinitionBuilder.id;
import static org.ost.advertisement.meta.fields.SqlDtoFieldDefinitionBuilder.instant;
import static org.ost.advertisement.meta.fields.SqlDtoFieldDefinitionBuilder.str;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.meta.fields.SqlDtoFieldDefinition;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.query.filter.FilterApplier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilter>
	implements UserRepositoryCustom {

	private static final UserMapper USER_MAPPER = new UserMapper();
	private static final UserFilterApplier USER_FILTER_APPLIER = new UserFilterApplier();
	private static final UserEmailFilterApplier USER_EMAIL_FILTER_APPLIER = new UserEmailFilterApplier();

	public UserRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, USER_MAPPER, USER_FILTER_APPLIER);
	}

	@Override
	public Optional<User> findByEmail(String email) {
		return find(USER_EMAIL_FILTER_APPLIER, email);
	}

	public static class UserFilterApplier extends FilterApplier<UserFilter> {

		public UserFilterApplier() {
			relations.addAll(List.of(
				of("name", Fields.NAME, (f, fc, r) -> r.like(f.getName(), fc)),
				of("email", Fields.EMAIL, (f, fc, r) -> r.like(f.getEmail(), fc)),
				of("role", Fields.ROLE, (f, fc, r) -> r.equalsTo(f.getRole() != null ? f.getRole().name() : null, fc)),
				of("createdAt_start", Fields.CREATED_AT, (f, fc, r) -> r.after(f.getCreatedAtStart(), fc)),
				of("createdAt_end", Fields.CREATED_AT, (f, fc, r) -> r.before(f.getCreatedAtEnd(), fc)),
				of("updatedAt_start", Fields.UPDATED_AT, (f, fc, r) -> r.after(f.getUpdatedAtStart(), fc)),
				of("updatedAt_end", Fields.UPDATED_AT, (f, fc, r) -> r.before(f.getUpdatedAtEnd(), fc)),
				of("startId", Fields.ID, (f, fc, r) -> r.after(f.getStartId(), fc)),
				of("endId", Fields.ID, (f, fc, r) -> r.before(f.getEndId(), fc))
			));
		}
	}

	public static class UserEmailFilterApplier extends FilterApplier<String> {

		public UserEmailFilterApplier() {
			relations.add(of("email", Fields.EMAIL, (email, fc, r) -> r.equalsTo(email, fc)));
		}
	}

	public static class UserMapper extends FieldRelations<User> {

		public UserMapper() {
			super(Fields.ALL, "user_information u");
		}

		@Override
		public User mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
			return User.builder()
				.id(Fields.ID.extract(rs))
				.name(Fields.NAME.extract(rs))
				.email(Fields.EMAIL.extract(rs))
				.role(Role.valueOf(Fields.ROLE.extract(rs)))
				.passwordHash(Fields.PASSWORD_HASH.extract(rs))
				.createdAt(Fields.CREATED_AT.extract(rs))
				.updatedAt(Fields.UPDATED_AT.extract(rs))
				.locale(Fields.LOCALE.extract(rs))
				.build();
		}
	}

	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static class Fields {

		public static final SqlDtoFieldDefinition<Long> ID = id("id", "u.id");
		public static final SqlDtoFieldDefinition<String> NAME = str("name", "u.name");
		public static final SqlDtoFieldDefinition<String> EMAIL = str("email", "u.email");
		public static final SqlDtoFieldDefinition<String> ROLE = str("role", "u.role");
		public static final SqlDtoFieldDefinition<String> PASSWORD_HASH = str("passwordHash", "u.password_hash");
		public static final SqlDtoFieldDefinition<Instant> CREATED_AT = instant("createdAt", "u.created_at");
		public static final SqlDtoFieldDefinition<Instant> UPDATED_AT = instant("updatedAt", "u.updated_at");
		public static final SqlDtoFieldDefinition<String> LOCALE = str("locale", "u.locale");

		public static final SqlDtoFieldDefinition<?>[] ALL = {
			ID, NAME, EMAIL, ROLE, PASSWORD_HASH, CREATED_AT, UPDATED_AT, LOCALE
		};
	}
}
