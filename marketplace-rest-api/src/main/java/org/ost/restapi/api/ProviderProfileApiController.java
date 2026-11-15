package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.orchestrator.services.SnapshotRestoreService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.SupportedLocales;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.restapi.api.audit.ActivityHistorySupport;
import org.ost.restapi.api.concurrency.ETagUtil;
import org.ost.restapi.api.paging.PagedResponseBuilder;
import org.ost.restapi.api.paging.SortQueryParser;
import org.ost.restapi.api.patch.MergePatchSupport;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

/** Full CRUD over {@link ProviderProfileSaveService}/{@link ProviderProfileReadService}; {@code targetUserId} is always the caller's own id, and every returned profile is enriched like the Vaadin UI's. */
@RestController
@RequestMapping("/api/provider-profiles")
@RequiredArgsConstructor
public class ProviderProfileApiController {


    // Same sortable set as ProviderProfileSortMeta, built from the shared ProviderProfileDto.Fields constants.
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            ProviderProfileDto.Fields.createdAt, ProviderProfileDto.Fields.updatedAt, ProviderProfileDto.Fields.promoted);

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, ProviderProfileDto.Fields.promoted, ProviderProfileDto.Fields.updatedAt, ProviderProfileDto.Fields.createdAt);

    private final ProviderProfileSaveService saveService;
    private final ProviderProfileReadService readService;
    private final ProviderProfileDisplayEnrichmentService enrichmentService;
    private final UserProfileService userProfileService;
    private final ActivityHistorySupport activityHistorySupport;
    private final SnapshotRestoreService snapshotRestoreService;
    private final MergePatchSupport mergePatchSupport;

    @Operation(operationId = "createProviderProfile", summary = "Create a provider profile", description = "Self-service only -- the profile is always created for the caller's own account. categoryIds come from GET /api/taxons?type=CATEGORY, cityTaxonId from GET /api/taxons?type=CITY. kind=SUPPORT requires a privileged (admin/moderator) caller.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {
              "kind": "MASTER",
              "about": "Experienced plumber",
              "categoryIds": [1],
              "cityTaxonId": 5,
              "phone": "+380501234567",
              "telegram": "plumber_master",
              "viber": "+380501234567"
            }""")))
    @ApiResponse(responseCode = "429", description = "Write limit reached")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto create(@AuthenticationPrincipal Long actorId, @RequestBody @Valid ProviderProfileWriteRequest request) {
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(null, request.kind(), request.about(), request.categoryIds(), request.cityTaxonId(),
                request.phone(), request.telegram(), request.viber(), null);
        Long id = saveService.save(dto, actorId, actorId);
        return enrich(readService.findById(id).orElseThrow(), SupportedLocales.DEFAULT);
    }

    @Operation(operationId = "listProviderProfiles", summary = "List/filter/sort provider profiles", description = "size is not caller-supplied -- it comes from the caller's saved settings (PATCH /api/users/me/settings), or the shared default for anonymous callers. Default order when sort is omitted: promoted profiles first, then most recently changed, then most recently created, then id; sort=promoted,asc lists the promoted ones last. promoted=true keeps only promoted profiles. Each item has promoted and promotionReason.")
    @GetMapping
    public ResponseEntity<List<ProviderProfileDto>> list(@AuthenticationPrincipal Long actorId,
            @ModelAttribute @Valid ProviderProfileFilterDto filter, @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String sort, @RequestParam(defaultValue = SupportedLocales.DEFAULT_TAG) String locale,
            HttpServletRequest httpRequest) {
        int size = userProfileService.resolveProviderProfilesPageSize(actorId);
        Sort sortObj = SortQueryParser.parse(sort, SORTABLE_FIELDS, DEFAULT_SORT);
        List<ProviderProfileDto> items = readService.getFiltered(filter, page, size, sortObj);
        items = enrichmentService.enrichWithCategoriesAndCity(items, Locale.forLanguageTag(locale));
        items = enrichmentService.enrichWithActorInfo(items);
        int total = readService.count(filter);
        return PagedResponseBuilder.build(httpRequest, page, size, total, items);
    }


    @Operation(operationId = "getProviderProfile", summary = "Get one provider profile by id",
            description = "Public. Returns one provider profile with its ETag, the version to send back via If-Match on update or delete. 404 when the id is unknown.")
    @ApiResponse(responseCode = "200", headers = @Header(name = "ETag", description = "Version to send back via If-Match on update/delete"))
    @GetMapping("/{id}")
    public ResponseEntity<ProviderProfileDto> getById(@PathVariable Long id, @RequestParam(defaultValue = SupportedLocales.DEFAULT_TAG) String locale) {
        ProviderProfileDto profile = enrich(readService.findById(id).orElseThrow(NoSuchElementException::new), Locale.forLanguageTag(locale));
        profile = enrichmentService.enrichWithPromotion(profile);
        return ETagUtil.withVersion(ResponseEntity.ok(), profile.getVersion()).body(profile);
    }

    @Operation(operationId = "getProviderProfileHistory", summary = "List a provider profile's change history",
            description = "Newest first. Administrators and moderators see every entry; anyone else sees only the entries they authored.")
    @GetMapping("/{id}/history")
    @SecurityRequirement(name = "bearerKey")
    public List<ActivityHistorySupport.HistoryEntryResponse> history(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id) {
        return activityHistorySupport.history(actorId, EntityType.PROVIDER_PROFILE, id);
    }

    @Operation(operationId = "getProviderProfileHistorySnapshot", summary = "Get one recorded state of a provider profile",
            description = "Returns the recorded state and its version, for a restoring update (PUT with If-Match). Administrators and moderators can read any snapshot; anyone else only one from their own visible history. 403 when the snapshot is not visible to the caller, 404 when unknown.")
    @GetMapping("/{id}/history/{snapshotId}")
    @SecurityRequirement(name = "bearerKey")
    public ActivityHistorySupport.SnapshotResponse historySnapshot(@AuthenticationPrincipal @NonNull Long actorId,
            @PathVariable @NonNull Long id, @PathVariable @NonNull Long snapshotId) {
        return activityHistorySupport.snapshot(actorId, EntityType.PROVIDER_PROFILE, id, snapshotId, ProviderProfileSnapshotDto.class);
    }

    @Operation(operationId = "updateProviderProfile", summary = "Update a provider profile", description = "If-Match must carry the version from the last GET response's ETag; the owner of the profile, an administrator or a moderator may call it, with their API key. Full replace, same as the other fields -- omitting phone/telegram/viber clears them.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {
              "kind": "MASTER",
              "about": "Experienced plumber, now also water heaters",
              "categoryIds": [1],
              "cityTaxonId": 5,
              "phone": "+380501234567",
              "telegram": "plumber_master",
              "viber": "+380501234567"
            }""")))
    @ApiResponse(responseCode = "429", description = "Write limit reached")
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto update(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @RequestBody @Valid ProviderProfileWriteRequest request,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return saveExisting(actorId, id, request, ETagUtil.requireIfMatch(ifMatch));
    }

    @Operation(operationId = "patchProviderProfile", summary = "Partially update a provider profile",
            description = "JSON Merge Patch (RFC 7396): send only the fields to change. A field left out keeps its value; null clears about, categoryIds, cityTaxonId, phone, telegram or viber; null on kind is 400. categoryIds is replaced as a whole. An unknown field is 400. kind=SUPPORT still requires a privileged caller. Contacts are never returned by this call. If-Match must carry the version from the last GET response's ETag (412 when stale); the owner of the profile, an administrator or a moderator may call it, with their API key.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = {
            @Content(mediaType = "application/merge-patch+json", schema = @Schema(implementation = ProviderProfileWriteRequest.class),
                    examples = @ExampleObject(value = "{\"telegram\": \"plumber_master\"}")),
            @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProviderProfileWriteRequest.class),
                    examples = @ExampleObject(value = "{\"telegram\": \"plumber_master\"}"))})
    @ApiResponse(responseCode = "429", description = "Write limit reached")
    @PatchMapping(value = "/{id}", consumes = {"application/merge-patch+json", MediaType.APPLICATION_JSON_VALUE})
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto patch(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @RequestBody JsonNode patch,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        Long version = ETagUtil.requireIfMatch(ifMatch);
        ProviderProfileSnapshotDto current = saveService.findCurrentSnapshot(id).orElseThrow(NoSuchElementException::new);
        ProviderProfileWriteRequest merged = mergePatchSupport.apply(ProviderProfileWriteRequest.from(current), patch, ProviderProfileWriteRequest.class);
        return saveExisting(actorId, id, merged, version);
    }

    @Operation(operationId = "restoreProviderProfileFromSnapshot", summary = "Restore a provider profile to a recorded state",
            description = "Returns the profile to the recorded state through the normal save path (same permissions and validation as an update). If-Match carries the current version. 403 when the snapshot is not visible to the caller, 404 when unknown, 412 when the version is stale.")
    @ApiResponse(responseCode = "429", description = "Write limit reached")
    @PostMapping("/{id}/history/{snapshotId}/restore")
    @SecurityRequirement(name = "bearerKey")
    public ProviderProfileDto restoreFromSnapshot(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id,
            @PathVariable @NonNull Long snapshotId,
            @Parameter(description = "Current version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        Long savedId = snapshotRestoreService.restoreProviderProfile(actorId, id, snapshotId, ETagUtil.requireIfMatch(ifMatch));
        return enrich(readService.findById(savedId).orElseThrow(), SupportedLocales.DEFAULT);
    }

    private ProviderProfileDto saveExisting(Long actorId, Long id, ProviderProfileWriteRequest request, Long version) {
        ProviderProfileSaveDto dto = new ProviderProfileSaveDto(id, request.kind(), request.about(), request.categoryIds(), request.cityTaxonId(),
                request.phone(), request.telegram(), request.viber(), version);
        Long savedId = saveService.save(dto, actorId, actorId);
        return enrich(readService.findById(savedId).orElseThrow(), SupportedLocales.DEFAULT);
    }

    private ProviderProfileDto enrich(ProviderProfileDto profile, Locale locale) {
        profile = enrichmentService.enrichWithCategoryAndCity(profile, locale);
        return enrichmentService.enrichWithActor(profile);
    }

    @Operation(operationId = "deleteProviderProfile", summary = "Delete a provider profile", description = "If-Match must carry the version from the last GET response's ETag; the owner of the profile, an administrator or a moderator may call it, with their API key.")
    @ApiResponse(responseCode = "429", description = "Write limit reached")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        saveService.delete(id, actorId, ETagUtil.requireIfMatch(ifMatch));
    }

    /** Caller-writable fields for both create and update -- id/version are server-managed (path variable / If-Match header), never client-supplied here. */
    public record ProviderProfileWriteRequest(
            @NotNull ProviderKind kind,
            @Size(max = ProviderProfileSaveDto.ABOUT_RAW_MAX_LENGTH) String about,
            @Size(max = ProviderProfileSaveDto.CATEGORY_MAX_COUNT) Set<Long> categoryIds,
            Long cityTaxonId,
            @Pattern(regexp = ContactInfoDto.PHONE_PATTERN) String phone,
            @Pattern(regexp = ContactInfoDto.TELEGRAM_PATTERN) String telegram,
            @Pattern(regexp = ContactInfoDto.PHONE_PATTERN) String viber
    ) {

        static ProviderProfileWriteRequest from(ProviderProfileSnapshotDto snapshot) {
            return new ProviderProfileWriteRequest(snapshot.kind(), snapshot.about(), Set.copyOf(snapshot.categoryIds()),
                    snapshot.cityTaxonId(), snapshot.phone(), snapshot.telegram(), snapshot.viber());
        }
    }

}
