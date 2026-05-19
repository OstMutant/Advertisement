package org.ost.marketplace.repository.user;

import org.jetbrains.annotations.NotNull;
import org.ost.marketplace.dto.UserProfileDto;
import org.ost.marketplace.entities.Role;
import org.ost.marketplace.entities.User;
import org.ost.sqlengine.SqlEntityDescriptor;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.ost.sqlengine.read.SqlSelectField;
import org.ost.sqlengine.write.SqlEntityWriter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.marketplace.entities.User.Fields.*;
import static org.ost.sqlengine.read.SqlSelectFieldFactory.*;
import static org.ost.sqlengine.write.SqlWriteFieldFactory.field;
import static org.ost.sqlengine.write.SqlWriteFieldFactory.fieldExpr;

public final class UserDescriptor implements SqlEntityDescriptor {

    public static final String TABLE  = "user_information";
    public static final String ALIAS  = "u";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlSelectField<Long>    ID            = longVal(ALIAS + ".id",            id);
    public static final SqlSelectField<String>  NAME          = str(ALIAS + ".name",          name);
    public static final SqlSelectField<String>  EMAIL         = str(ALIAS + ".email",         email);
    public static final SqlSelectField<String>  ROLE          = str(ALIAS + ".role",          role);
    public static final SqlSelectField<String>  PASSWORD_HASH = str(ALIAS + ".password_hash", passwordHash);
    public static final SqlSelectField<Instant> CREATED_AT    = instant(ALIAS + ".created_at", createdAt);
    public static final SqlSelectField<Instant> UPDATED_AT    = instant(ALIAS + ".updated_at", updatedAt);
    public static final SqlSelectField<String>  LOCALE        = str(ALIAS + ".locale",         locale);

    public static final class Read {
        private Read() {}

        public static final SqlEntityProjection<User> PROJECTION = new SqlEntityProjection<>(
                List.of(ID, NAME, EMAIL, ROLE, PASSWORD_HASH, CREATED_AT, UPDATED_AT, LOCALE), SOURCE) {
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
        };
    }

    public static final class Write {
        private Write() {}
        public static final String TABLE    = UserDescriptor.TABLE;
        public static final String SETTINGS = "settings";

        public static final SqlEntityWriter<UserProfileDto> PROFILE_WRITER = SqlEntityWriter.of(
                TABLE,
                field("name",       UserProfileDto::name),
                field("role",       u -> u.role().name()),
                fieldExpr("updated_at", "NOW()")
        );

        public static final SqlEntityWriter<String> LOCALE_WRITER = SqlEntityWriter.of(
                TABLE,
                field("locale", s -> s)
        );
    }

    private UserDescriptor() {}
}
