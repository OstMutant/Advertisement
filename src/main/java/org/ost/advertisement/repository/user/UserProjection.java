package org.ost.advertisement.repository.user;

import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.query.projection.SqlFieldDefinition;
import org.ost.advertisement.repository.query.projection.SqlProjection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.advertisement.entities.User.Fields.*;
import static org.ost.advertisement.entities.User.Fields.id;
import static org.ost.advertisement.repository.query.projection.SqlFieldBuilder.*;
import static org.ost.advertisement.repository.query.projection.SqlFieldBuilder.id;

public class UserProjection extends SqlProjection<User> {

    public static final SqlFieldDefinition<Long> ID = id("u.id", id);
    public static final SqlFieldDefinition<String> NAME = str("u.name", name);
    public static final SqlFieldDefinition<String> EMAIL = str("u.email", email);
    public static final SqlFieldDefinition<String> ROLE = str("u.role", role);
    public static final SqlFieldDefinition<String> PASSWORD_HASH = str("u.password_hash", passwordHash);
    public static final SqlFieldDefinition<Instant> CREATED_AT = instant("u.created_at", createdAt);
    public static final SqlFieldDefinition<Instant> UPDATED_AT = instant("u.updated_at", updatedAt);
    public static final SqlFieldDefinition<String> LOCALE = str("u.locale", locale);

    public UserProjection() {
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
