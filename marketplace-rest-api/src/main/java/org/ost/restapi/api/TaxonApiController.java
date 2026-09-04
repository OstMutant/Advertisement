package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonFilterDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Category/city catalog management — reads are public, writes require the caller to be
 * privileged (admin/moderator), enforced inside {@link TaxonCatalogService} itself.
 */
@RestController
@RequestMapping("/api/taxons")
@RequiredArgsConstructor
public class TaxonApiController {

    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    // Only TaxonDto's own id field -- createdAt/updatedAt exist on the underlying repository row
    // but not on TaxonDto itself, so exposing them as sort keys would let a caller sort by a field
    // it can never see in the response body.
    private static final Set<String> SORTABLE_FIELDS = Set.of(TaxonDto.Fields.id);

    private final TaxonCatalogService taxonCatalogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public TaxonDto create(@AuthenticationPrincipal Long actorId, @RequestBody TaxonCreateRequest request) {
        Long id = taxonCatalogService.create(request.type(), toTranslations(request.translations()), actorId);
        return taxonCatalogService.findById(id, DEFAULT_LOCALE).orElseThrow();
    }

    @GetMapping
    public ResponseEntity<List<TaxonDto>> list(@RequestParam TaxonType type, @RequestParam(defaultValue = "en") String locale,
            @ModelAttribute @Valid TaxonFilterDto filter, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String sort,
            UriComponentsBuilder uriBuilder) {
        Sort sortObj = SortQueryParser.parse(sort, SORTABLE_FIELDS);
        List<TaxonDto> items = taxonCatalogService.getPage(type, Locale.forLanguageTag(locale), filter, page, size, sortObj);
        int total = taxonCatalogService.count(type, filter);
        return PagedResponseBuilder.build(uriBuilder, page, size, total, items);
    }

    @GetMapping("/{id}")
    public TaxonDto getById(@PathVariable Long id, @RequestParam(defaultValue = "en") String locale) {
        return taxonCatalogService.findById(id, Locale.forLanguageTag(locale)).orElseThrow(NoSuchElementException::new);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public TaxonDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestBody TaxonUpdateRequest request) {
        taxonCatalogService.update(id, toTranslations(request.translations()), actorId, request.version());
        return taxonCatalogService.findById(id, DEFAULT_LOCALE).orElseThrow();
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public void softDelete(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestParam(required = false) Long version) {
        taxonCatalogService.softDelete(id, actorId, version);
    }

    private static Map<Locale, TaxonTranslationDto> toTranslations(List<TaxonTranslationRequest> translations) {
        return translations.stream().collect(Collectors.toMap(
                t -> Locale.forLanguageTag(t.locale()),
                t -> TaxonTranslationDto.builder().locale(t.locale()).name(t.name()).description(t.description()).build()));
    }

    /**
     * Request body for creating a taxon — a thin wrapper since {@code Map<Locale,...>} can't bind
     * from JSON directly.
     */
    public record TaxonCreateRequest(TaxonType type, List<TaxonTranslationRequest> translations) {
    }

    /** Request body for updating a taxon's translations. */
    public record TaxonUpdateRequest(List<TaxonTranslationRequest> translations, Long version) {
    }

    /** One locale's translation for a taxon create/update request. */
    public record TaxonTranslationRequest(String locale, String name, String description) {
    }
}
