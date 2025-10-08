package org.ost.advertisement.repository.user;


import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.RepositoryCustom;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryCustomImpl extends
	RepositoryCustom<User, UserFilter> implements UserRepositoryCustom {

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
				of("name", UserMapper.UserFieldRelations.NAME,
					(f, fc, self) -> self.like(f.getName(), fc)),
				of("email", UserMapper.UserFieldRelations.EMAIL,
					(f, fc, self) -> self.like(f.getEmail(), fc)),
				of("role", UserMapper.UserFieldRelations.ROLE,
					(f, fc, self) -> self.equalsTo(f.getRole() != null ? f.getRole().name() : null, fc)),
				of("createdAt_start", UserMapper.UserFieldRelations.CREATED_AT,
					(f, fc, self) -> self.after(f.getCreatedAtStart(), fc)),
				of("createdAt_end", UserMapper.UserFieldRelations.CREATED_AT,
					(f, fc, self) -> self.before(f.getCreatedAtEnd(), fc)),
				of("updatedAt_start", UserMapper.UserFieldRelations.UPDATED_AT,
					(f, fc, self) -> self.after(f.getUpdatedAtStart(), fc)),
				of("updatedAt_end", UserMapper.UserFieldRelations.UPDATED_AT,
					(f, fc, self) -> self.before(f.getUpdatedAtEnd(), fc)),
				of("startId", UserMapper.UserFieldRelations.ID,
					(f, fc, self) -> self.after(f.getStartId(), fc)),
				of("endId", UserMapper.UserFieldRelations.ID,
					(f, fc, self) -> self.before(f.getEndId(), fc))
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
				of("email", UserMapper.UserFieldRelations.EMAIL,
					(email, fc, self) -> self.equalsTo(email, fc))
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
			ID("u.id", "id"),
			NAME("u.name", "name"),
			EMAIL("u.email", "email"),
			ROLE("u.role", "role"),
			PASSWORD_HASH("u.password_hash", "passwordHash"),
			CREATED_AT("u.created_at", "createdAt"),
			UPDATED_AT("u.updated_at", "updatedAt"),
			LOCALE("u.locale", "locale");

			@Getter
			private final String sqlField;
			@Getter
			private final String dtoField;
		}

		public UserMapper() {
			super(UserFieldRelations.values(), "user_information u");
		}

		@Override
		public User mapRow(ResultSet rs, int rowNum) throws SQLException {
			return User.builder()
				.id(rs.getObject(UserFieldRelations.ID.getDtoField(), Long.class))
				.name(rs.getString(UserFieldRelations.NAME.getDtoField()))
				.email(rs.getString(UserFieldRelations.EMAIL.getDtoField()))
				.role(Role.valueOf(rs.getString(UserFieldRelations.ROLE.getDtoField())))
				.passwordHash(rs.getString(UserFieldRelations.PASSWORD_HASH.getDtoField()))
				.createdAt(toInstant(rs.getTimestamp(UserFieldRelations.CREATED_AT.getDtoField())))
				.updatedAt(toInstant(rs.getTimestamp(UserFieldRelations.UPDATED_AT.getDtoField())))
				.locale(rs.getString(UserFieldRelations.LOCALE.getDtoField()))
				.build();
		}
	}
}
