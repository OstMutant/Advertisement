package org.ost.platform.advertisement.spi;

import lombok.NonNull;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public interface AdvertisementPort {

    List<AdvertisementInfoDto> getFiltered(@NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort, @NonNull Locale locale);

    int count(@NonNull AdvertisementFilterDto filter);

    Optional<AdvertisementInfoDto> findById(@NonNull Long id);

    Long save(@NonNull AdvertisementSaveDto dto, @NonNull Long actingUserId);

    void delete(@NonNull Long id, @NonNull Long actingUserId);

    void onMediaChanged(@NonNull Long entityId);

    Set<Long> findExistingIds(@NonNull Set<Long> ids);
}
