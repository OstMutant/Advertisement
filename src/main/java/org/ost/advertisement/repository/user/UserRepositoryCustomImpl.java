package org.ost.advertisement.repository.user;

import static org.ost.advertisement.meta.fields.SqlDtoFieldRelationBuilder.id;
import static org.ost.advertisement.meta.fields.SqlDtoFieldRelationBuilder.instant;
import static org.ost.advertisement.meta.fields.SqlDtoFieldRelationBuilder.str;

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
import org.ost.advertisement.meta.fields.SqlDtoFieldRelation;
import org.ost.advertisement.repository.RepositoryCustom;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilter>
	implements UserRepositoryCustom {

	private static final UserMapper USER_MAPPER = new UserMapper();
	private static final UserFilterApplier USER_CONDITIONS_RULES = new UserFilterApplier();
	private static final UserEmailConditionsRule USER_EMAIL_CONDITIONS_RULE = new UserEmailConditionsRule();

	public UserRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, USER_MAPPER, USER_CONDITIONS_RULES);
	}

	@Override
	public Optional<User> findByEmail(String email) {
		return find(USER_EMAIL_CONDITIONS_RULE, email);
	}

	public static class UserFilterApplier extends FilterApplier<UserFilter> {

		public UserFilterApplier() {
			relations.addAll(List.of(
				of("name", Fields.NAME, (f, fc, self) -> self.like(f.getName(), fc)),
				of("email", Fields.EMAIL, (f, fc, self) -> self.like(f.getEmail(), fc)),
				of("role", Fields.ROLE, (f, fc, self) -> self.equalsTo(
					f.getRole() != null ? f.getRole().name() : null, fc)),
				of("createdAt_start", Fields.CREATED_AT, (f, fc, self) -> self.after(f.getCreatedAtStart(), fc)),
				of("createdAt_end", Fields.CREATED_AT, (f, fc, self) -> self.before(f.getCreatedAtEnd(), fc)),
				of("updatedAt_start", Fields.UPDATED_AT, (f, fc, self) -> self.after(f.getUpdatedAtStart(), fc)),
				of("updatedAt_end", Fields.UPDATED_AT, (f, fc, self) -> self.before(f.getUpdatedAtEnd(), fc)),
				of("startId", Fields.ID, (f, fc, self) -> self.after(f.getStartId(), fc)),
				of("endId", Fields.ID, (f, fc, self) -> self.before(f.getEndId(), fc))
			));
		}

		@Override
		public String apply(MapSqlParameterSource params, UserFilter filter) {
			return applyRelations(params, filter);
		}
	}

	public static class UserEmailConditionsRule extends FilterApplier<String> {

		public UserEmailConditionsRule() {
			relations.add(of("email", Fields.EMAIL, (email, fc, self) -> self.equalsTo(email, fc)));
		}

		@Override
		public String apply(MapSqlParameterSource params, String email) {
			return applyRelations(params, email);
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

		public static final SqlDtoFieldRelation<Long> ID = id("id", "u.id");
		public static final SqlDtoFieldRelation<String> NAME = str("name", "u.name");
		public static final SqlDtoFieldRelation<String> EMAIL = str("email", "u.email");
		public static final SqlDtoFieldRelation<String> ROLE = str("role", "u.role");
		public static final SqlDtoFieldRelation<String> PASSWORD_HASH = str("passwordHash", "u.password_hash");
		public static final SqlDtoFieldRelation<Instant> CREATED_AT = instant("createdAt", "u.created_at");
		public static final SqlDtoFieldRelation<Instant> UPDATED_AT = instant("updatedAt", "u.updated_at");
		public static final SqlDtoFieldRelation<String> LOCALE = str("locale", "u.locale");

		public static final SqlDtoFieldRelation<?>[] ALL = {
			ID, NAME, EMAIL, ROLE, PASSWORD_HASH, CREATED_AT, UPDATED_AT, LOCALE
		};
	}
}
