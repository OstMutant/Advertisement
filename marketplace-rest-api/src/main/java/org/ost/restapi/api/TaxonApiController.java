package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonFilterDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.restapi.api.concurrency.ETagUtil;
import org.ost.restapi.api.paging.SortQueryParser;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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

    @Operation(summary = "Create a category or city",
            description = "translations must include every supported locale (en + uk today) with a non-blank name and description each -- an incomplete set is rejected with 400.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "Category with both required locales", value = """
            {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public TaxonDto create(@AuthenticationPrincipal Long actorId, @RequestBody TaxonCreateRequest request) {
        Long id = taxonCatalogService.create(request.type(), toTranslations(request.translations()), actorId);
        return taxonCatalogService.findById(id, DEFAULT_LOCALE).orElseThrow();
    }

    @Operation(summary = "List categories/cities", description = "cityTaxonId/categoryIds used elsewhere (e.g. POST /api/advertisements, POST /api/provider-profiles) come from this endpoint's own \"id\" field. type=CATEGORY or type=CITY narrows to one; omitted returns both. Reference data only -- always returns the full matching set, no pagination.")
    @GetMapping
    public ResponseEntity<List<TaxonDto>> list(
            @Parameter(description = "CATEGORY or CITY; omit to list both") @RequestParam(required = false) TaxonType type,
            @RequestParam(defaultValue = "en") String locale,
            @ModelAttribute @Valid TaxonFilterDto filter, @RequestParam(required = false) String sort) {
        Sort sortObj = SortQueryParser.parse(sort, SORTABLE_FIELDS);
        Locale resolvedLocale = Locale.forLanguageTag(locale);
        List<TaxonDto> items;
        if (type != null) {
            items = taxonCatalogService.getAll(type, resolvedLocale, filter, sortObj);
        } else {
            items = new ArrayList<>(taxonCatalogService.getAll(TaxonType.CATEGORY, resolvedLocale, filter, sortObj));
            items.addAll(taxonCatalogService.getAll(TaxonType.CITY, resolvedLocale, filter, sortObj));
        }
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(items.size())).body(items);
    }

    @Operation(summary = "Get one category/city by id", description = "Falls back to the English translation if the requested locale has none. ETag response header carries the version to send back via If-Match on update/delete.")
    @ApiResponse(responseCode = "200", headers = @Header(name = "ETag", description = "Version to send back via If-Match on update/delete"))
    @GetMapping("/{id}")
    public ResponseEntity<TaxonDto> getById(@PathVariable Long id, @RequestParam(defaultValue = "en") String locale) {
        TaxonDto taxon = taxonCatalogService.findById(id, Locale.forLanguageTag(locale)).orElseThrow(NoSuchElementException::new);
        return ETagUtil.withVersion(ResponseEntity.ok(), taxon.getVersion()).body(taxon);
    }

    @Operation(summary = "Update a category or city's translations", description = "Same all-supported-locales-required rule as create; admin/moderator only. If-Match must carry the version from the last GET response.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "Both required locales", value = """
            {"translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"},{"locale":"uk","name":"Сантехніка","description":"Сантехнічні послуги"}]}""")))
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public TaxonDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestBody TaxonUpdateRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        taxonCatalogService.update(id, toTranslations(request.translations()), actorId, ETagUtil.parseIfMatch(ifMatch));
        return taxonCatalogService.findById(id, DEFAULT_LOCALE).orElseThrow();
    }

    @Operation(summary = "Soft-delete a category or city", description = "If-Match must carry the version from the last GET/create/update response for this id; admin/moderator only.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void softDelete(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        taxonCatalogService.softDelete(id, actorId, ETagUtil.parseIfMatch(ifMatch));
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

    /** Request body for updating a taxon's translations -- version comes from the If-Match header, not this body. */
    public record TaxonUpdateRequest(List<TaxonTranslationRequest> translations) {
    }

    /** One locale's translation for a taxon create/update request. */
    public record TaxonTranslationRequest(String locale, String name, String description) {
    }
}
