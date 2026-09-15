package org.ost.platform.apikey.dto;

import lombok.experimental.FieldNameConstants;

import java.time.Instant;

/**
 * Caller-facing view of an API key — never carries the raw key or its hash.
 */
@FieldNameConstants
public record ApiKeySummaryDto(Long id, String keyPrefix, String label, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {
}
