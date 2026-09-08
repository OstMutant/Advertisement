package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AdvertisementDisplayEnrichmentService;
import org.ost.orchestrator.services.AdvertisementReadService;
import org.ost.orchestrator.services.AdvertisementSaveService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.restapi.api.concurrency.ETagUtil;
import org.ost.restapi.api.paging.PagedResponseBuilder;
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

    @Operation(summary = "Create an advertisement", description = "categoryIds come from GET /api/taxons?type=CATEGORY, cityTaxonId from GET /api/taxons?type=CITY.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {"title":"Plumbing services","description":"Fast and reliable plumbing","adKind":"OFFER","categoryIds":[1],"cityTaxonId":5}""")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public AdvertisementInfoDto create(@AuthenticationPrincipal Long actorId, @RequestBody @Valid AdvertisementWriteRequest request) {
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, request.title(), request.description(),
                request.adKind(), request.categoryIds(), request.cityTaxonId(), null);
        Long id = saveService.save(dto, actorId, ref -> null);
        return enrich(readService.findById(id).orElseThrow(), DEFAULT_LOCALE);
    }

    @Operation(summary = "List/filter/sort advertisements", description = "size is not caller-supplied -- it comes from the caller's saved settings (PATCH /api/users/me/settings), or the shared default for anonymous callers.")
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

    @Operation(summary = "Get one advertisement by id")
    @ApiResponse(responseCode = "200", headers = @Header(name = "ETag", description = "Version to send back via If-Match on update/delete"))
    @GetMapping("/{id}")
    public ResponseEntity<AdvertisementInfoDto> getById(@PathVariable Long id, @RequestParam(defaultValue = "en") String locale) {
        AdvertisementInfoDto ad = enrich(readService.findById(id).orElseThrow(NoSuchElementException::new), Locale.forLanguageTag(locale));
        return ETagUtil.withVersion(ResponseEntity.ok(), ad.getVersion()).body(ad);
    }

    @Operation(summary = "Update an advertisement", description = "If-Match must carry the version from the last GET response's ETag; the caller must own the advertisement.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {"title":"Plumbing services","description":"Fast and reliable plumbing, now weekends too","adKind":"OFFER","categoryIds":[1],"cityTaxonId":5}""")))
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public AdvertisementInfoDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @RequestBody @Valid AdvertisementWriteRequest request,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AdvertisementSaveDto dto = new AdvertisementSaveDto(id, request.title(), request.description(),
                request.adKind(), request.categoryIds(), request.cityTaxonId(), ETagUtil.parseIfMatch(ifMatch));
        Long savedId = saveService.save(dto, actorId, ref -> null);
        return enrich(readService.findById(savedId).orElseThrow(), DEFAULT_LOCALE);
    }

    private AdvertisementInfoDto enrich(AdvertisementInfoDto ad, Locale locale) {
        ad = enrichmentService.enrichWithCategoryAndCity(ad, locale);
        ad = enrichmentService.enrichWithActor(ad);
        return enrichmentService.enrichWithMedia(ad);
    }

    @Operation(summary = "Delete an advertisement", description = "If-Match must carry the version from the last GET response's ETag; the caller must own the advertisement.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        saveService.delete(id, actorId, ETagUtil.parseIfMatch(ifMatch));
    }

    /** Caller-writable fields for both create and update -- id/version are server-managed (path variable / If-Match header), never client-supplied here. */
    public record AdvertisementWriteRequest(
            @NotBlank @Size(min = 1, max = AdvertisementSaveDto.TITLE_MAX_LENGTH) String title,
            @NotBlank @Size(max = AdvertisementSaveDto.DESCRIPTION_RAW_MAX_LENGTH) String description,
            @NotNull AdKind adKind,
            @Size(max = AdvertisementSaveDto.CATEGORY_MAX_COUNT) Set<Long> categoryIds,
            Long cityTaxonId
    ) {
    }
}
