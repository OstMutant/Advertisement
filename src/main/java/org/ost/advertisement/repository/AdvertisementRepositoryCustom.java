// AdvertisementRepositoryCustom.java
package org.ost.advertisement.repository;

import java.util.List;
import org.ost.advertisement.dto.AdvertisementFilter;
import org.ost.advertisement.entyties.Advertisement;
import org.springframework.data.domain.Pageable;

public interface AdvertisementRepositoryCustom {
    /**
     * Finds advertisements based on a filter and pagination information.
     * @param filter The AdvertisementFilter object containing criteria for filtering.
     * @param pageable The Pageable object containing pagination and sorting information.
     * @return A List of Advertisement objects matching the criteria.
     */
    List<Advertisement> findByFilter(AdvertisementFilter filter, Pageable pageable); // Changed from Flux

    /**
     * Counts the total number of advertisements matching a given filter.
     * This is used for pagination metadata.
     * @param filter The AdvertisementFilter object containing criteria for filtering.
     * @return A Long representing the total count of matching advertisements.
     */
    Long countByFilter(AdvertisementFilter filter); // Changed from Mono<Long>
}
