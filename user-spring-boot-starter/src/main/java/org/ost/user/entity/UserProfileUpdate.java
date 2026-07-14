package org.ost.user.entity;

import lombok.Builder;
import lombok.Value;
import org.ost.platform.user.model.Role;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Value
@Builder
@Table("user_information")
public class UserProfileUpdate {

    @Id
    Long id;
    String name;
    Role role;

    @LastModifiedDate
    Instant updatedAt;

    @Version
    Long version;
}
