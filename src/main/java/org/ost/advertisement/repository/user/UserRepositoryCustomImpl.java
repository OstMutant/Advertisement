package org.ost.advertisement.repository.user;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.meta.fields.FieldDefinition.ValueExtractor;
import org.ost.advertisement.meta.fields.SqlDtoFieldRelation;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.user.UserRepositoryCustomImpl.UserMapper.UserFieldRelations;
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
				of("name", UserFieldRelations.NAME, (f, fc, self) -> self.like(f.getName(), fc)),
				of("email", UserFieldRelations.EMAIL, (f, fc, self) -> self.like(f.getEmail(), fc)),
				of("role", UserFieldRelations.ROLE,
					(f, fc, self) -> self.equalsTo(f.getRole() != null ? f.getRole().name() : null, fc)),
				of("createdAt_start", UserFieldRelations.CREATED_AT,
					(f, fc, self) -> self.after(f.getCreatedAtStart(), fc)),
				of("createdAt_end", UserFieldRelations.CREATED_AT,
					(f, fc, self) -> self.before(f.getCreatedAtEnd(), fc)),
				of("updatedAt_start", UserFieldRelations.UPDATED_AT,
					(f, fc, self) -> self.after(f.getUpdatedAtStart(), fc)),
				of("updatedAt_end", UserFieldRelations.UPDATED_AT,
					(f, fc, self) -> self.before(f.getUpdatedAtEnd(), fc)),
				of("startId", UserFieldRelations.ID, (f, fc, self) -> self.after(f.getStartId(), fc)),
				of("endId", UserFieldRelations.ID, (f, fc, self) -> self.before(f.getEndId(), fc))
			));
		}

		@Override
		public String apply(MapSqlParameterSource params, UserFilter filter) {
			return applyRelations(params, filter);
		}
	}

	public static class UserEmailConditionsRule extends FilterApplier<String> {

		public UserEmailConditionsRule() {
			relations.add(
				of("email", UserFieldRelations.EMAIL, (email, fc, self) -> self.equalsTo(email, fc))
			);
		}

		@Override
		public String apply(MapSqlParameterSource params, String email) {
			return applyRelations(params, email);
		}
	}

	public static class UserMapper extends FieldRelations<User> {

		@AllArgsConstructor
		public enum UserFieldRelations implements SqlDtoFieldRelation {
			ID("u.id", "id", (rs, alias) -> rs.getObject(alias, Long.class)),
			NAME("u.name", "name", ResultSet::getString),
			EMAIL("u.email", "email", ResultSet::getString),
			ROLE("u.role", "role", ResultSet::getString),
			PASSWORD_HASH("u.password_hash", "passwordHash", ResultSet::getString),
			CREATED_AT("u.created_at", "createdAt", (rs, alias) -> toInstant(rs.getTimestamp(alias))),
			UPDATED_AT("u.updated_at", "updatedAt", (rs, alias) -> toInstant(rs.getTimestamp(alias))),
			LOCALE("u.locale", "locale", ResultSet::getString);

			@Getter
			private final String sqlField;
			@Getter
			private final String dtoField;
			@Getter
			private final ValueExtractor<?> extractorLogic;
		}

		public UserMapper() {
			super(UserFieldRelations.values(), "user_information u");
		}

		@Override
		public User mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
			return User.builder()
				.id(UserFieldRelations.ID.extract(rs))
				.name(UserFieldRelations.NAME.extract(rs))
				.email(UserFieldRelations.EMAIL.extract(rs))
				.role(Role.valueOf(UserFieldRelations.ROLE.extract(rs)))
				.passwordHash(UserFieldRelations.PASSWORD_HASH.extract(rs))
				.createdAt(UserFieldRelations.CREATED_AT.extract(rs))
				.updatedAt(UserFieldRelations.UPDATED_AT.extract(rs))
				.locale(UserFieldRelations.LOCALE.extract(rs))
				.build();
		}
	}
}

