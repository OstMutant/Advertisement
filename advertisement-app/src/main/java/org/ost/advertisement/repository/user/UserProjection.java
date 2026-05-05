package org.ost.advertisement.repository.user;

import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.sqlengine.projection.SqlFieldDefinition;
import org.ost.sqlengine.projection.SqlProjection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.advertisement.entities.User.Fields.*;
import static org.ost.sqlengine.projection.SqlFieldBuilder.*;

public class UserProjection extends SqlProjection<User> {

    public static final String TABLE  = "user_information";
    public static final String ALIAS  = "u";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlFieldDefinition<Long>    ID            = id(ALIAS + ".id",            id);
    public static final SqlFieldDefinition<String>  NAME          = str(ALIAS + ".name",          name);
    public static final SqlFieldDefinition<String>  EMAIL         = str(ALIAS + ".email",         email);
    public static final SqlFieldDefinition<String>  ROLE          = str(ALIAS + ".role",          role);
    public static final SqlFieldDefinition<String>  PASSWORD_HASH = str(ALIAS + ".password_hash", passwordHash);
    public static final SqlFieldDefinition<Instant> CREATED_AT    = instant(ALIAS + ".created_at", createdAt);
    public static final SqlFieldDefinition<Instant> UPDATED_AT    = instant(ALIAS + ".updated_at", updatedAt);
    public static final SqlFieldDefinition<String>  LOCALE        = str(ALIAS + ".locale",         locale);

    public static final class Write {
        private Write() {}
        public static final String TABLE    = UserProjection.TABLE;
        public static final String SETTINGS = "settings";
    }

    public UserProjection() {
        super(List.of(ID, NAME, EMAIL, ROLE, PASSWORD_HASH, CREATED_AT, UPDATED_AT, LOCALE), SOURCE);
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
