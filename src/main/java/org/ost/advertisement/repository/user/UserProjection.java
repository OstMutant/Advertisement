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
import static org.ost.advertisement.repository.query.projection.SqlFieldBuilder.*;

public class UserProjection extends SqlProjection<User> {

    public static final SqlFieldDefinition<Long>    ID            = id(UserTable.ID,         id);
    public static final SqlFieldDefinition<String>  NAME          = str(UserTable.NAME,       name);
    public static final SqlFieldDefinition<String>  EMAIL         = str(UserTable.EMAIL,      email);
    public static final SqlFieldDefinition<String>  ROLE          = str(UserTable.ROLE,       role);
    public static final SqlFieldDefinition<String>  PASSWORD_HASH = str(UserTable.PASSWORD,   passwordHash);
    public static final SqlFieldDefinition<Instant> CREATED_AT    = instant(UserTable.CREATED_AT, createdAt);
    public static final SqlFieldDefinition<Instant> UPDATED_AT    = instant(UserTable.UPDATED_AT, updatedAt);
    public static final SqlFieldDefinition<String>  LOCALE        = str(UserTable.LOCALE,     locale);

    public UserProjection() {
        super(List.of(ID, NAME, EMAIL, ROLE, PASSWORD_HASH, CREATED_AT, UPDATED_AT, LOCALE), UserTable.SOURCE);
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