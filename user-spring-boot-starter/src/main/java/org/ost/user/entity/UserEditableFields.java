package org.ost.user.entity;

import lombok.Builder;
import lombok.Value;
import org.ost.platform.user.model.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Narrower {@code user_information} projection (id/name/role/updatedAt/version only, no
 * email/passwordHash) mapped to the same table so the profile-edit path's generated {@code UPDATE}
 * can never touch a sensitive field.
 */
@Value
@Builder
@Table("user_information")
public class UserEditableFields {

    @Id
    Long id;
    String name;
    Role role;

    // write-only from Java's side -- Spring Data JDBC populates it on save; read back via raw SQL in UserRepository, not via this field.
    @LastModifiedDate
    Instant updatedAt;

    @Version
    Long version;
}
