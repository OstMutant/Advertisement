package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
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
 * Full CRUD over {@link ProviderProfileSaveService}/{@link ProviderProfileReadService} — v1 is
 * self-service only, {@code targetUserId} is always the caller's own id. Every returned
 * {@link ProviderProfileDto} is enriched with category/city names and actor info via
 * {@link ProviderProfileDisplayEnrichmentService}, the same pipeline the Vaadin UI uses.
 */
@RestController
@RequestMapping("/api/provider-profiles")
@RequiredArgsConstructor
public class ProviderProfileApiController {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    // Mirrors ProviderProfileSortMeta's UI-sortable set -- marketplace-rest-api can't import that
    // class (wrong dependency direction, see .claude/rules/marketplace-rest-api.md), so both sides
    // independently reference the same ProviderProfileDto.Fields.* constants instead.
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            ProviderProfileDto.Fields.createdAt, ProviderProfileDto.Fields.updatedAt);

    private final ProviderProfileSaveService saveService;
    private final ProviderProfileReadService readService;
    private final ProviderProfileDisplayEnrichmentService enrichmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto create(@AuthenticationPrincipal Long actorId, @RequestBody @Valid ProviderProfileSaveDto dto) {
        Long id = saveService.save(dto, actorId, actorId);
        return enrich(readService.findById(id).orElseThrow(), DEFAULT_LOCALE);
    }

    @GetMapping
    public ResponseEntity<List<ProviderProfileDto>> list(@ModelAttribute @Valid ProviderProfileFilterDto filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort, @RequestParam(defaultValue = "en") String locale,
            UriComponentsBuilder uriBuilder) {
        Sort sortObj = SortQueryParser.parse(sort, SORTABLE_FIELDS);
        List<ProviderProfileDto> items = readService.getFiltered(filter, page, size, sortObj);
        items = enrichmentService.enrichWithCategoriesAndCity(items, Locale.forLanguageTag(locale));
        items = enrichmentService.enrichWithActorInfo(items);
        int total = readService.count(filter);
        return PagedResponseBuilder.build(uriBuilder, page, size, total, items);
    }

    @GetMapping("/{id}")
    public ProviderProfileDto getById(@PathVariable Long id, @RequestParam(defaultValue = "en") String locale) {
        return enrich(readService.findById(id).orElseThrow(NoSuchElementException::new), Locale.forLanguageTag(locale));
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestBody @Valid ProviderProfileSaveDto dto) {
        ProviderProfileSaveDto withId = new ProviderProfileSaveDto(id, dto.kind(), dto.about(), dto.categoryIds(), dto.cityTaxonId(), dto.version());
        Long savedId = saveService.save(withId, actorId, actorId);
        return enrich(readService.findById(savedId).orElseThrow(), DEFAULT_LOCALE);
    }

    private ProviderProfileDto enrich(ProviderProfileDto profile, Locale locale) {
        profile = enrichmentService.enrichWithCategoryAndCity(profile, locale);
        return enrichmentService.enrichWithActor(profile);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestParam(required = false) Long version) {
        saveService.delete(id, actorId, version);
    }
}
