package org.ost.advertisement.repository.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ost.advertisement.dto.filter.UserFilter;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.RepositoryCustom.FieldRelations.SqlDtoFieldRelation;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryCustomImpl extends
	RepositoryCustom<User, UserFilter> implements UserRepositoryCustom {

	private static final UserMapper USER_MAPPER = new UserMapper();
	private static final UserFieldConditionsRules USER_CONDITIONS_RULES = new UserFieldConditionsRules();
	private static final UserEmailConditionsRule USER_EMAIL_CONDITIONS_RULE = new UserEmailConditionsRule();

	public UserRepositoryCustomImpl(NamedParameterJdbcTemplate jdbc) {
		super(jdbc, USER_MAPPER, USER_CONDITIONS_RULES);
	}

	@Override
	public Optional<User> findByEmail(String email) {
		return find(USER_EMAIL_CONDITIONS_RULE, email);
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
			super(EnumSet.allOf(UserFieldRelations.class), "user_information u");
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

	public static class UserFieldConditionsRules extends FieldConditionsRules<UserFilter> {

		@AllArgsConstructor
		public enum FilterFieldRelations implements Relation {
			NAME("name", UserMapper.UserFieldRelations.NAME),
			EMAIL("email", UserMapper.UserFieldRelations.EMAIL),
			ROLE("role", UserMapper.UserFieldRelations.ROLE),
			CREATED_AT_START("createdAt_start", UserMapper.UserFieldRelations.CREATED_AT),
			CREATED_AT_END("createdAt_end", UserMapper.UserFieldRelations.CREATED_AT),
			UPDATED_AT_START("updatedAt_start", UserMapper.UserFieldRelations.UPDATED_AT),
			UPDATED_AT_END("updatedAt_end", UserMapper.UserFieldRelations.UPDATED_AT),
			START_ID("startId", UserMapper.UserFieldRelations.ID),
			END_ID("endId", UserMapper.UserFieldRelations.ID);

			@Getter
			private final String filterField;
			@Getter
			private final SqlDtoFieldRelation sqlDtoFieldRelation;
		}

		public UserFieldConditionsRules() {
			super(EnumSet.allOf(FilterFieldRelations.class));
		}

		@Override
		public String apply(MapSqlParameterSource params, UserFilter filter) {
			FieldConditions<UserFilter> fieldConditions = new FieldConditions<>(filter);
			for (Relation relation : relations) {
				switch (relation) {
					case FilterFieldRelations.NAME -> like(relation, filter.getName(), fieldConditions);
					case FilterFieldRelations.EMAIL -> like(relation, filter.getEmail(), fieldConditions);
					case FilterFieldRelations.ROLE ->
						equalsTo(relation, filter.getRole() != null ? filter.getRole().name() : null, fieldConditions);
					case FilterFieldRelations.CREATED_AT_START ->
						after(relation, filter.getCreatedAtStart(), fieldConditions);
					case FilterFieldRelations.CREATED_AT_END ->
						before(relation, filter.getCreatedAtEnd(), fieldConditions);
					case FilterFieldRelations.UPDATED_AT_START ->
						after(relation, filter.getUpdatedAtStart(), fieldConditions);
					case FilterFieldRelations.UPDATED_AT_END ->
						before(relation, filter.getUpdatedAtEnd(), fieldConditions);
					case FilterFieldRelations.START_ID -> after(relation, filter.getStartId(), fieldConditions);
					case FilterFieldRelations.END_ID -> before(relation, filter.getEndId(), fieldConditions);
					default -> throw new IllegalStateException("Unexpected relation: " + relation);
				}
			}
			params.addValues(fieldConditions.toParams());
			return fieldConditions.toSqlApplyingAnd();
		}
	}

	public static class UserEmailConditionsRule extends FieldConditionsRules<String> {

		@AllArgsConstructor
		public enum FilterFieldRelations implements Relation {
			EMAIL("email", UserMapper.UserFieldRelations.EMAIL);

			@Getter
			private final String filterField;
			@Getter
			private final SqlDtoFieldRelation sqlDtoFieldRelation;
		}

		public UserEmailConditionsRule() {
			super(EnumSet.allOf(FilterFieldRelations.class));
		}

		@Override
		public String apply(MapSqlParameterSource params, String email) {
			FieldConditions<String> fieldConditions = new FieldConditions<>(email);
			equalsTo(FilterFieldRelations.EMAIL, email, fieldConditions);
			params.addValues(fieldConditions.toParams());
			return fieldConditions.toSqlApplyingAnd();
		}
	}
}
