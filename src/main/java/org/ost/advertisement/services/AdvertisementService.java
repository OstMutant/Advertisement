package org.ost.advertisement.services;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementView;
import org.ost.advertisement.dto.filter.AdvertisementFilter;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.mappers.AdvertisementMapper;
import org.ost.advertisement.repository.advertisement.AdvertisementRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.security.utils.AuthUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdvertisementService {

	private final AdvertisementRepository repository;
	private final AccessEvaluator access;
	private final AdvertisementMapper mapper;

	public List<AdvertisementView> getFiltered(AdvertisementFilter filter, int page, int size, Sort sort) {
		return repository.findByFilter(filter, PageRequest.of(page, size, sort));
	}

	public int count(AdvertisementFilter filter) {
		return repository.countByFilter(filter).intValue();
	}

	public void save(User currentUser, Advertisement ad) {
		if (!canEdit(currentUser, ad)) {
			throw new AccessDeniedException("You cannot edit this advertisement");
		}
		ad.setUpdatedAt(Instant.now());
		repository.save(ad);
	}

	public void delete(AdvertisementView ad) {
		if (!canDelete(AuthUtil.getCurrentUser(), ad)) {
			throw new AccessDeniedException("You cannot delete this advertisement");
		}
		repository.delete(mapper.toAdvertisement(ad));
	}

	public boolean canEdit(User currentUser, Advertisement target) {
		return access.canEdit(currentUser, target);
	}

	public boolean canDelete(User currentUser, AdvertisementView target) {
		return access.canDelete(currentUser, target);
	}

}
