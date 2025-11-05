package org.ost.advertisement.repository.user;

import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.createdAtEnd;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.createdAtStart;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.email;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.endId;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.name;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.role;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.startId;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.updatedAtEnd;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.updatedAtStart;
import static org.ost.advertisement.entities.User.Fields;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.id;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.instant;
import static org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinitionBuilder.str;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.RepositoryCustom;
import org.ost.advertisement.repository.query.filter.FilterApplier;
import org.ost.advertisement.repository.query.mapping.FieldRelations;
import org.ost.advertisement.repository.query.meta.SqlDtoFieldDefinition;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepositoryCustomImpl extends RepositoryCustom<User, UserFilterDto>
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

	public static class UserFilterApplier extends FilterApplier<UserFilterDto> {

		public UserFilterApplier() {
			relations.addAll(List.of(
				of(name, UserMapper.NAME, (f, fc, r) -> r.like(f.getName(), fc)),
				of(email, UserMapper.EMAIL, (f, fc, r) -> r.like(f.getEmail(), fc)),
				of(role, UserMapper.ROLE,
					(f, fc, r) -> r.equalsTo(f.getRole() != null ? f.getRole().name() : null, fc)),
				of(createdAtStart, UserMapper.CREATED_AT, (f, fc, r) -> r.after(f.getCreatedAtStart(), fc)),
				of(createdAtEnd, UserMapper.CREATED_AT, (f, fc, r) -> r.before(f.getCreatedAtEnd(), fc)),
				of(updatedAtStart, UserMapper.UPDATED_AT, (f, fc, r) -> r.after(f.getUpdatedAtStart(), fc)),
				of(updatedAtEnd, UserMapper.UPDATED_AT, (f, fc, r) -> r.before(f.getUpdatedAtEnd(), fc)),
				of(startId, UserMapper.ID, (f, fc, r) -> r.after(f.getStartId(), fc)),
				of(endId, UserMapper.ID, (f, fc, r) -> r.before(f.getEndId(), fc))
			));
		}
	}

	public static class UserEmailFilterApplier extends FilterApplier<String> {

		public UserEmailFilterApplier() {
			relations.add(of("email", UserMapper.EMAIL, (email, fc, r) -> r.equalsTo(email, fc)));
		}
	}

	public static class UserMapper extends FieldRelations<User> {

		public static final SqlDtoFieldDefinition<Long> ID = id(Fields.id, "u.id");
		public static final SqlDtoFieldDefinition<String> NAME = str(Fields.name, "u.name");
		public static final SqlDtoFieldDefinition<String> EMAIL = str(Fields.email, "u.email");
		public static final SqlDtoFieldDefinition<String> ROLE = str(Fields.role, "u.role");
		public static final SqlDtoFieldDefinition<String> PASSWORD_HASH = str(Fields.passwordHash, "u.password_hash");
		public static final SqlDtoFieldDefinition<Instant> CREATED_AT = instant(Fields.createdAt, "u.created_at");
		public static final SqlDtoFieldDefinition<Instant> UPDATED_AT = instant(Fields.updatedAt, "u.updated_at");
		public static final SqlDtoFieldDefinition<String> LOCALE = str(Fields.locale, "u.locale");

		public UserMapper() {
			super(new SqlDtoFieldDefinition<?>[]{
				ID, NAME, EMAIL, ROLE, PASSWORD_HASH, CREATED_AT, UPDATED_AT, LOCALE
			}, "user_information u");
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
}
