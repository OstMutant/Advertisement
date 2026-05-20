package org.ost.marketplace.repository.user;

import org.ost.marketplace.dto.UserProfileDto;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.Role;
import org.ost.marketplace.entities.User;
import org.ost.sqlengine.SqlEntityDescriptor;
import org.ost.sqlengine.common.SqlCommand;
import org.ost.sqlengine.common.SqlDescriptorField;
import org.ost.sqlengine.filter.SqlCondition;
import org.ost.sqlengine.filter.SqlFilterBuilder;
import org.ost.sqlengine.read.SqlEntityProjection;
import org.ost.sqlengine.write.SqlEntityWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.time.Instant;
import java.util.List;

import static org.ost.marketplace.entities.User.Fields.*;
import static org.ost.sqlengine.common.SqlDescriptorFieldFactory.*;
import static org.ost.sqlengine.filter.SqlBoundFilter.of;
import static org.ost.sqlengine.filter.SqlCondition.*;
import static org.ost.sqlengine.write.SqlWriteFieldFactory.field;
import static org.ost.sqlengine.write.SqlWriteFieldFactory.fieldExpr;

public final class UserDescriptor implements SqlEntityDescriptor {

    public static final String TABLE  = "user_information";
    public static final String ALIAS  = "u";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlDescriptorField<Long>    ID            = longVal(ALIAS + ".id",            id);
    public static final SqlDescriptorField<String>  NAME          = str(ALIAS + ".name",          name);
    public static final SqlDescriptorField<String>  EMAIL         = str(ALIAS + ".email",         email);
    public static final SqlDescriptorField<String>  ROLE          = str(ALIAS + ".role",          role);
    public static final SqlDescriptorField<String>  PASSWORD_HASH = str(ALIAS + ".password_hash", passwordHash);
    public static final SqlDescriptorField<Instant> CREATED_AT    = instant(ALIAS + ".created_at", createdAt);
    public static final SqlDescriptorField<Instant> UPDATED_AT    = instant(ALIAS + ".updated_at", updatedAt);
    public static final SqlDescriptorField<String>  LOCALE        = str(ALIAS + ".locale",         locale);

    public static final class Read {
        private Read() {}

        public static final SqlEntityProjection<User> PROJECTION = SqlEntityProjection.of(
                List.of(ID, NAME, EMAIL, ROLE, PASSWORD_HASH, CREATED_AT, UPDATED_AT, LOCALE), SOURCE,
                (rs, rowNum) -> User.builder()
                        .id(ID.extract(rs))
                        .name(NAME.extract(rs))
                        .email(EMAIL.extract(rs))
                        .role(Role.valueOf(ROLE.extract(rs)))
                        .passwordHash(PASSWORD_HASH.extract(rs))
                        .createdAt(CREATED_AT.extract(rs))
                        .updatedAt(UPDATED_AT.extract(rs))
                        .locale(LOCALE.extract(rs))
                        .build());

        public static final SqlFilterBuilder<UserFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
                of(UserFilterDto.Fields.name,           NAME,       (m, v) -> like(m, v.getName())),
                of(UserFilterDto.Fields.email,          EMAIL,      (m, v) -> like(m, v.getEmail())),
                of(UserFilterDto.Fields.roles,          ROLE,       (m, v) -> inSet(m, v.getRoles())),
                of(UserFilterDto.Fields.createdAtStart, CREATED_AT, (m, v) -> after(m, v.getCreatedAtStart())),
                of(UserFilterDto.Fields.createdAtEnd,   CREATED_AT, (m, v) -> before(m, v.getCreatedAtEnd())),
                of(UserFilterDto.Fields.updatedAtStart, UPDATED_AT, (m, v) -> after(m, v.getUpdatedAtStart())),
                of(UserFilterDto.Fields.updatedAtEnd,   UPDATED_AT, (m, v) -> before(m, v.getUpdatedAtEnd())),
                of(UserFilterDto.Fields.startId,        ID,         (m, v) -> after(m, v.getStartId())),
                of(UserFilterDto.Fields.endId,          ID,         (m, v) -> before(m, v.getEndId()))
        ));

        public static final SqlFilterBuilder<String> EMAIL_FILTER = new SqlFilterBuilder<>(List.of(
                of(UserFilterDto.Fields.email, EMAIL, SqlCondition::equalsTo)
        ));

        public static final SqlCommand SELECT_EXISTING_IDS = SqlCommand.of(
                "SELECT " + ID.columnName() + " FROM " + TABLE +
                " WHERE " + ID.columnName() + " = ANY(:ids)");

        public static final SqlCommand SELECT_ACTOR_NAMES = SqlCommand.of(
                "SELECT " + ID.columnName() + ", " + NAME.columnName() + " FROM " + TABLE +
                " WHERE " + ID.columnName() + " = ANY(:ids)");

        public static MapSqlParameterSource idsParams(Long[] ids) {
            return Params.of("ids", ids);
        }
    }

    public static final class Write {
        private Write() {}
        public static final String TABLE    = UserDescriptor.TABLE;
        public static final String SETTINGS = "settings";

        public static final SqlEntityWriter<UserProfileDto> PROFILE_WRITER = SqlEntityWriter.of(
                TABLE,
                field(NAME,       UserProfileDto::name),
                field(ROLE,       u -> u.role().name()),
                fieldExpr(UPDATED_AT, "NOW()")
        );

        public static final SqlEntityWriter<String> LOCALE_WRITER = SqlEntityWriter.of(
                TABLE,
                field(LOCALE, s -> s)
        );

        public static final SqlCommand UPDATE_PROFILE = SqlCommand.of(PROFILE_WRITER.updateWhere("id = :id"));
        public static final SqlCommand UPDATE_LOCALE  = SqlCommand.of(LOCALE_WRITER.updateWhere("id = :id"));

        public static final SqlCommand SAVE_SETTINGS = SqlCommand.of(
                "UPDATE " + TABLE +
                " SET "   + SETTINGS + " = :settings::jsonb" +
                " WHERE id = :userId");

        public static final SqlCommand SELECT_SETTINGS = SqlCommand.of(
                "SELECT " + SETTINGS +
                " FROM "  + TABLE +
                " WHERE id = :userId");

        public static MapSqlParameterSource updateProfileParams(UserProfileDto dto) {
            return PROFILE_WRITER.params(dto).addValue("id", dto.id());
        }

        public static MapSqlParameterSource updateLocaleParams(Long userId, String locale) {
            return LOCALE_WRITER.params(locale).addValue("id", userId);
        }

        public static MapSqlParameterSource saveSettingsParams(Long userId, String settingsJson) {
            return Params.with("settings", settingsJson).add("userId", userId);
        }

        public static MapSqlParameterSource loadSettingsParams(Long userId) {
            return Params.of("userId", userId);
        }
    }

    private UserDescriptor() {}
}
