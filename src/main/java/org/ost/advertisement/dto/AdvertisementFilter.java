package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvertisementFilter {
    private String titleFilter;
    private String categoryFilter;
    private String locationFilter;
    private String statusFilter; // e.g., "ACTIVE", "EXPIRED", "DRAFT"

    // Changed from LocalDate to Instant
    private Instant createdAtStart;
    private Instant createdAtEnd;
    private Instant updatedAtStart;
    private Instant updatedAtEnd;

    private Long startId;
    private Long endId;
}
