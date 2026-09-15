package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Shared read-only {@link ProviderProfilePort} lookups, reused by every marketplace-app adapter. */
@Service
@RequiredArgsConstructor
public class ProviderProfileReadService {

    private final ComponentFactory<ProviderProfilePort> providerProfilePortFactory;

    public List<ProviderProfileDto> getFiltered(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort) {
        return providerProfilePortFactory.findIfAvailable()
                .map(p -> p.getFiltered(filter, page, size, sort))
                .orElse(List.of());
    }

    public int count(@NonNull ProviderProfileFilterDto filter) {
        return providerProfilePortFactory.findIfAvailable().map(p -> p.count(filter)).orElse(0);
    }

    public Optional<ProviderProfileDto> findById(@NonNull Long id) {
        return providerProfilePortFactory.findIfAvailable().flatMap(p -> p.findById(id));
    }

    public boolean isAvailable() {
        return providerProfilePortFactory.findIfAvailable().isPresent();
    }
}
