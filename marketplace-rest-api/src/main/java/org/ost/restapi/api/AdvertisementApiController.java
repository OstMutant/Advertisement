package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AdvertisementDisplayEnrichmentService;
import org.ost.orchestrator.services.AdvertisementReadService;
import org.ost.orchestrator.services.AdvertisementSaveService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.restapi.api.paging.PagedResponseBuilder;
import org.ost.restapi.api.paging.SortQueryParser;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Full CRUD over {@link AdvertisementSaveService}/{@link AdvertisementReadService} — reads are
 * public, writes need bearer authentication. No photo upload via this API (no in-progress gallery
 * to commit, unlike the Vaadin form). Every returned {@link AdvertisementInfoDto} is enriched with
 * category/city names, author info, and media summary via {@link AdvertisementDisplayEnrichmentService},
 * the same pipeline the Vaadin UI uses.
 */
@RestController
@RequestMapping("/api/advertisements")
@RequiredArgsConstructor
public class AdvertisementApiController {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    // Mirrors AdvertisementSortMeta's UI-sortable set -- marketplace-rest-api can't import that
    // class (wrong dependency direction, see .claude/rules/marketplace-rest-api.md), so both sides
    // independently reference the same AdvertisementInfoDto.Fields.* constants instead.
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            AdvertisementInfoDto.Fields.title, AdvertisementInfoDto.Fields.createdAt, AdvertisementInfoDto.Fields.updatedAt);

    private final AdvertisementSaveService saveService;
    private final AdvertisementReadService readService;
    private final AdvertisementDisplayEnrichmentService enrichmentService;
    private final UserProfileService userProfileService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public AdvertisementInfoDto create(@AuthenticationPrincipal Long actorId, @RequestBody @Valid AdvertisementSaveDto dto) {
        Long id = saveService.save(dto, actorId, ref -> null);
        return enrich(readService.findById(id).orElseThrow(), DEFAULT_LOCALE);
    }

    @GetMapping
    public ResponseEntity<List<AdvertisementInfoDto>> list(@AuthenticationPrincipal Long actorId,
            @ModelAttribute @Valid AdvertisementFilterDto filter, @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String sort, @RequestParam(defaultValue = "en") String locale,
            UriComponentsBuilder uriBuilder) {
        int size = userProfileService.resolveAdsPageSize(actorId);
        Sort sortObj = SortQueryParser.parse(sort, SORTABLE_FIELDS);
        List<AdvertisementInfoDto> items = readService.getFiltered(filter, page, size, sortObj);
        items = enrichmentService.enrichWithCategoriesAndCity(items, Locale.forLanguageTag(locale));
        items = enrichmentService.enrichWithActorInfo(items);
        items = enrichmentService.enrichWithMediaSummary(items);
        int total = readService.count(filter);
        return PagedResponseBuilder.build(uriBuilder, page, size, total, items);
    }

    @GetMapping("/{id}")
    public AdvertisementInfoDto getById(@PathVariable Long id, @RequestParam(defaultValue = "en") String locale) {
        return enrich(readService.findById(id).orElseThrow(NoSuchElementException::new), Locale.forLanguageTag(locale));
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public AdvertisementInfoDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestBody @Valid AdvertisementSaveDto dto) {
        AdvertisementSaveDto withId = new AdvertisementSaveDto(id, dto.title(), dto.description(), dto.adKind(), dto.categoryIds(), dto.cityTaxonId(), dto.version());
        Long savedId = saveService.save(withId, actorId, ref -> null);
        return enrich(readService.findById(savedId).orElseThrow(), DEFAULT_LOCALE);
    }

    private AdvertisementInfoDto enrich(AdvertisementInfoDto ad, Locale locale) {
        ad = enrichmentService.enrichWithCategoryAndCity(ad, locale);
        ad = enrichmentService.enrichWithActor(ad);
        return enrichmentService.enrichWithMedia(ad);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestParam(required = false) Long version) {
        saveService.delete(id, actorId, version);
    }
}
