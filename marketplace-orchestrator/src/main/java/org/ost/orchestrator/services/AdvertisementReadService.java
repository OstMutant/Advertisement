package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Shared read-only {@link AdvertisementPort} lookups, reused by every marketplace-app adapter. */
@Service
@RequiredArgsConstructor
public class AdvertisementReadService {

    private final ComponentFactory<AdvertisementPort> advertisementPortFactory;

    public List<AdvertisementInfoDto> getFiltered(@NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort) {
        return advertisementPortFactory.findIfAvailable()
                .map(p -> p.getFiltered(filter, page, size, sort))
                .orElse(List.of());
    }

    public int count(@NonNull AdvertisementFilterDto filter) {
        return advertisementPortFactory.findIfAvailable().map(p -> p.count(filter)).orElse(0);
    }

    public Optional<AdvertisementInfoDto> findById(@NonNull Long id) {
        return advertisementPortFactory.findIfAvailable().flatMap(p -> p.findById(id));
    }

    public boolean isAvailable() {
        return advertisementPortFactory.findIfAvailable().isPresent();
    }
}
