package org.ost.user.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.model.Role;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Value
@Builder
@FieldNameConstants
@Table("user_information")
public class User {

    @Id
    Long id;
    String name;
    String email;
    String passwordHash;
    Role role;

    @CreatedDate
    Instant createdAt;

    @LastModifiedDate
    Instant updatedAt;

    @Version
    Long version;

    public UserDto toDto() {
        return new UserDto(id, name, email, role, createdAt, updatedAt, version);
    }
}
