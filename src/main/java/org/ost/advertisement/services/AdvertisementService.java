package org.ost.advertisement.services;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.EntityMarker;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.advertisement.AdvertisementRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@RequiredArgsConstructor
@Validated
public class AdvertisementService {

	private final AdvertisementRepository repository;
	private final AccessEvaluator access;

	public List<AdvertisementInfoDto> getFiltered(@Valid AdvertisementFilterDto filter, int page, int size, Sort sort) {
		return repository.findByFilter(filter, PageRequest.of(page, size, sort));
	}

	public int count(@Valid AdvertisementFilterDto filter) {
		return repository.countByFilter(filter).intValue();
	}

	public void save(Advertisement ad) {
		if (access.canNotEdit(ad)) {
			throw new AccessDeniedException("You cannot edit this advertisement");
		}
		repository.save(ad);
	}

	public void delete(EntityMarker ad) {
		if (access.canNotDelete(ad)) {
			throw new AccessDeniedException("You cannot delete this advertisement");
		}
		repository.deleteById(ad.getId());
	}

}
