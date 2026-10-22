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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.SnapshotRestoreService;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.SupportedLocales;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.restapi.api.audit.ActivityHistorySupport;
import org.ost.restapi.api.concurrency.ETagUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import java.util.stream.Collectors;

/**
 * Category/city catalog management — reads are public except the history, writes require the caller to be
 * privileged (admin/moderator), enforced inside {@link TaxonCatalogService} itself.
 */
@RestController
@RequestMapping("/api/taxons")
@RequiredArgsConstructor
public class TaxonApiController {


    private final TaxonCatalogService taxonCatalogService;
    private final ActivityHistorySupport activityHistorySupport;
    private final SnapshotRestoreService snapshotRestoreService;

    @Operation(operationId = "createTaxon", summary = "Create a category or city",
            description = "translations must include every supported locale (en + uk today) with a non-blank name and description each -- an incomplete set is rejected with 400.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "Category with both required locales", value = """
            {
              "type": "CATEGORY",
              "translations": [
                {"locale": "en", "name": "Plumbing", "description": "Plumbing services"},
                {"locale": "uk", "name": "Сантехніка", "description": "Сантехнічні послуги"}
              ]
            }""")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public TaxonDto create(@AuthenticationPrincipal Long actorId, @Valid @RequestBody TaxonCreateRequest request) {
        Long id = taxonCatalogService.create(request.type(), toTranslations(request.translations()), actorId);
        return taxonCatalogService.findById(id, SupportedLocales.DEFAULT).orElseThrow();
    }

    @Operation(operationId = "listTaxons", summary = "List categories/cities", description = "cityTaxonId/categoryIds used elsewhere (e.g. POST /api/advertisements, POST /api/provider-profiles) come from this endpoint's own \"id\" field. type=CATEGORY or type=CITY narrows to one; omitted returns both. Reference data only -- always returns the full matching set, no pagination.")
    @GetMapping
    public ResponseEntity<List<TaxonDto>> list(
            @Parameter(description = "CATEGORY or CITY; omit to list both") @RequestParam(required = false) TaxonType type,
            @RequestParam(defaultValue = SupportedLocales.DEFAULT_TAG) String locale) {
        Locale resolvedLocale = Locale.forLanguageTag(locale);
        List<TaxonDto> items;
        if (type != null) {
            items = taxonCatalogService.getAllByType(type, resolvedLocale);
        } else {
            items = new ArrayList<>(taxonCatalogService.getAllByType(TaxonType.CATEGORY, resolvedLocale));
            items.addAll(taxonCatalogService.getAllByType(TaxonType.CITY, resolvedLocale));
        }
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(items.size())).body(items);
    }

    @Operation(operationId = "getTaxon", summary = "Get one category/city by id", description = "Falls back to the English translation if the requested locale has none. ETag response header carries the version to send back via If-Match on update/delete.")
    @ApiResponse(responseCode = "200", headers = @Header(name = "ETag", description = "Version to send back via If-Match on update/delete"))
    @GetMapping("/{id}")
    public ResponseEntity<TaxonDto> getById(@PathVariable Long id, @RequestParam(defaultValue = SupportedLocales.DEFAULT_TAG) String locale) {
        TaxonDto taxon = taxonCatalogService.findById(id, Locale.forLanguageTag(locale)).orElseThrow(NoSuchElementException::new);
        return ETagUtil.withVersion(ResponseEntity.ok(), taxon.getVersion()).body(taxon);
    }

    @Operation(operationId = "getTaxonHistory", summary = "List a category's or city's change history",
            description = "Newest first. Administrators and moderators see every entry; anyone else sees only the entries they authored.")
    @GetMapping("/{id}/history")
    @SecurityRequirement(name = "bearerKey")
    public List<ActivityHistorySupport.HistoryEntryResponse> history(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id) {
        return activityHistorySupport.history(actorId, EntityType.TAXON, id);
    }

    @Operation(operationId = "getTaxonHistorySnapshot", summary = "Get one recorded state of a category or city",
            description = "Returns the recorded state and its version, for a restoring update (PUT with If-Match). Administrators and moderators can read any snapshot; anyone else only one from their own visible history. 403 when the snapshot is not visible to the caller, 404 when unknown.")
    @GetMapping("/{id}/history/{snapshotId}")
    @SecurityRequirement(name = "bearerKey")
    public ActivityHistorySupport.SnapshotResponse historySnapshot(@AuthenticationPrincipal @NonNull Long actorId,
            @PathVariable @NonNull Long id, @PathVariable @NonNull Long snapshotId) {
        return activityHistorySupport.snapshot(actorId, EntityType.TAXON, id, snapshotId, TaxonSnapshotDto.class);
    }

    @Operation(operationId = "updateTaxon", summary = "Update a category or city's translations", description = "Same all-supported-locales-required rule as create; admin/moderator only. If-Match must carry the version from the last GET response.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "Both required locales", value = """
            {
              "translations": [
                {"locale": "en", "name": "Plumbing", "description": "Plumbing services"},
                {"locale": "uk", "name": "Сантехніка", "description": "Сантехнічні послуги"}
              ]
            }""")))
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public TaxonDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @Valid @RequestBody TaxonUpdateRequest request,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        taxonCatalogService.update(id, toTranslations(request.translations()), actorId, ETagUtil.requireIfMatch(ifMatch));
        return taxonCatalogService.findById(id, SupportedLocales.DEFAULT).orElseThrow();
    }

    @Operation(operationId = "deleteTaxon", summary = "Soft-delete a category or city", description = "If-Match must carry the version from the last GET/create/update response for this id; admin/moderator only.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void softDelete(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        taxonCatalogService.softDelete(id, actorId, ETagUtil.requireIfMatch(ifMatch));
    }

    @Operation(operationId = "restoreTaxon", summary = "Restore a soft-deleted category or city",
            description = "Clears the deletion mark and records the restore in the audit log; admin/moderator only.")
    @ApiResponse(responseCode = "200", description = "Taxon restored and returned")
    @ApiResponse(responseCode = "404", description = "Taxon not found")
    @ApiResponse(responseCode = "409", description = "Taxon is not deleted")
    @PostMapping("/{id}/restore")
    @SecurityRequirement(name = "bearerKey")
    public TaxonDto restore(@AuthenticationPrincipal Long actorId, @PathVariable Long id) {
        taxonCatalogService.restore(id, actorId);
        return taxonCatalogService.findById(id, SupportedLocales.DEFAULT).orElseThrow();
    }

    @Operation(operationId = "restoreTaxonFromSnapshot", summary = "Restore a category or city to a recorded state",
            description = "Returns the taxon to the recorded translations through the normal save path (same permissions and validation as an update). If-Match carries the current version. 403 when the snapshot is not visible to the caller, 404 when unknown, 412 when the version is stale.")
    @PostMapping("/{id}/history/{snapshotId}/restore")
    @SecurityRequirement(name = "bearerKey")
    public TaxonDto restoreFromSnapshot(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id,
            @PathVariable @NonNull Long snapshotId,
            @Parameter(description = "Current version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        snapshotRestoreService.restoreTaxon(actorId, id, snapshotId, ETagUtil.requireIfMatch(ifMatch));
        return taxonCatalogService.findById(id, SupportedLocales.DEFAULT).orElseThrow();
    }

    private static Map<Locale, TaxonTranslationDto> toTranslations(List<TaxonTranslationRequest> translations) {
        return translations.stream().collect(Collectors.toMap(
                t -> Locale.forLanguageTag(t.locale()),
                t -> TaxonTranslationDto.builder().locale(t.locale()).name(t.name()).description(t.description()).build(),
                (first, second) -> {
                    throw new IllegalArgumentException("Duplicate translation locale: " + first.getLocale());
                }));
    }

    /**
     * Request body for creating a taxon — a thin wrapper since {@code Map<Locale,...>} can't bind
     * from JSON directly.
     */
    public record TaxonCreateRequest(@NotNull TaxonType type, @NotEmpty List<@Valid TaxonTranslationRequest> translations) {
    }

    /** Request body for updating a taxon's translations -- version comes from the If-Match header, not this body. */
    public record TaxonUpdateRequest(@NotEmpty List<@Valid TaxonTranslationRequest> translations) {
    }

    /** One locale's translation for a taxon create/update request. */
    public record TaxonTranslationRequest(@NotBlank String locale, @NotBlank String name, @NotBlank String description) {
    }
}
