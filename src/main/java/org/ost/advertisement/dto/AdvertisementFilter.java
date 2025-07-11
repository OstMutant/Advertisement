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
    private String statusFilter;

    private Instant createdAtStart;
    private Instant createdAtEnd;
    private Instant updatedAtStart;
    private Instant updatedAtEnd;

    private Long startId;
    private Long endId;

    public void copyFrom(AdvertisementFilter other) {
        this.titleFilter = other.titleFilter;
        this.categoryFilter = other.categoryFilter;
        this.locationFilter = other.locationFilter;
        this.statusFilter = other.statusFilter;

        this.createdAtStart = other.createdAtStart;
        this.createdAtEnd = other.createdAtEnd;
        this.updatedAtStart = other.updatedAtStart;
        this.updatedAtEnd = other.updatedAtEnd;

        this.startId = other.startId;
        this.endId = other.endId;
    }

    public void clear() {
        this.titleFilter = null;
        this.categoryFilter = null;
        this.locationFilter = null;
        this.statusFilter = null;

        this.createdAtStart = null;
        this.createdAtEnd = null;
        this.updatedAtStart = null;
        this.updatedAtEnd = null;

        this.startId = null;
        this.endId = null;
    }
}
