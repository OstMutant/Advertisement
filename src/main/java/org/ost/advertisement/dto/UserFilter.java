package org.ost.advertisement.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFilter {
    private String nameFilter;

    // Changed from LocalDate to Instant, removed @DateTimeFormat
    private Instant createdAtStart;
    private Instant createdAtEnd;
    private Instant updatedAtStart;
    private Instant updatedAtEnd;

    private Long startId;
    private Long endId;
}
