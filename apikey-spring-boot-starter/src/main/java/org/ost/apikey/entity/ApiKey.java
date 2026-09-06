package org.ost.apikey.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * A bearer token owned by an actor, used to authenticate external REST API requests.
 */
@Value
@Builder
@FieldNameConstants
@Table("api_key")
public class ApiKey {

    @Id
    Long id;
    Long actorId;
    String keyHash;
    String keyPrefix;
    String label;

    @CreatedDate
    Instant createdAt;

    Instant lastUsedAt;
    Instant revokedAt;

    public ApiKeySummaryDto toSummaryDto() {
        return new ApiKeySummaryDto(id, keyPrefix, label, createdAt, lastUsedAt, revokedAt);
    }
}
