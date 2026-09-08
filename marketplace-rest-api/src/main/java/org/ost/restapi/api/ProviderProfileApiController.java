package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.model.ProviderKind;
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

    @Operation(summary = "Create a provider profile", description = "Self-service only -- the profile is always created for the caller's own account. categoryIds come from GET /api/taxons?type=CATEGORY, cityTaxonId from GET /api/taxons?type=CITY. kind=SUPPORT requires a privileged (admin/moderator) caller.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {"kind":"MASTER","about":"Experienced plumber","categoryIds":[1],"cityTaxonId":5}""")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto create(@AuthenticationPrincipal Long actorId, @RequestBody @Valid ProviderProfileWriteRequest request) {
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, request.kind(), request.about(), request.categoryIds(), request.cityTaxonId(), null);
        Long id = saveService.save(dto, actorId, actorId);
        return enrich(readService.findById(id).orElseThrow(), DEFAULT_LOCALE);
    }

    @Operation(summary = "List/filter/sort provider profiles")
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

    @Operation(summary = "Get one provider profile by id")
    @ApiResponse(responseCode = "200", headers = @Header(name = "ETag", description = "Version to send back via If-Match on update/delete"))
    @GetMapping("/{id}")
    public ResponseEntity<ProviderProfileDto> getById(@PathVariable Long id, @RequestParam(defaultValue = "en") String locale) {
        ProviderProfileDto profile = enrich(readService.findById(id).orElseThrow(NoSuchElementException::new), Locale.forLanguageTag(locale));
        return ETagUtil.withVersion(ResponseEntity.ok(), profile.getVersion()).body(profile);
    }

    @Operation(summary = "Update a provider profile", description = "If-Match must carry the version from the last GET response's ETag; the caller must own the profile.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {"kind":"MASTER","about":"Experienced plumber, now also water heaters","categoryIds":[1],"cityTaxonId":5}""")))
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @RequestBody @Valid ProviderProfileWriteRequest request,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(id, request.kind(), request.about(), request.categoryIds(), request.cityTaxonId(), ETagUtil.parseIfMatch(ifMatch));
        Long savedId = saveService.save(dto, actorId, actorId);
        return enrich(readService.findById(savedId).orElseThrow(), DEFAULT_LOCALE);
    }

    private ProviderProfileDto enrich(ProviderProfileDto profile, Locale locale) {
        profile = enrichmentService.enrichWithCategoryAndCity(profile, locale);
        return enrichmentService.enrichWithActor(profile);
    }

    @Operation(summary = "Delete a provider profile", description = "If-Match must carry the version from the last GET response's ETag; the caller must own the profile.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        saveService.delete(id, actorId, ETagUtil.parseIfMatch(ifMatch));
    }

    /** Caller-writable fields for both create and update -- id/version are server-managed (path variable / If-Match header), never client-supplied here. */
    public record ProviderProfileWriteRequest(
            @NotNull ProviderKind kind,
            @Size(max = ProviderProfileSaveDto.ABOUT_RAW_MAX_LENGTH) String about,
            @Size(max = ProviderProfileSaveDto.CATEGORY_MAX_COUNT) Set<Long> categoryIds,
            Long cityTaxonId
    ) {
    }
}
