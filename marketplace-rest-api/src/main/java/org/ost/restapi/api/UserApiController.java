package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.SnapshotRestoreService;
import org.ost.orchestrator.services.UserDeleteService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.SupportedLocales;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.PageSizeLimits;
import org.ost.platform.user.model.Role;
import org.ost.restapi.api.audit.ActivityHistorySupport;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/** Self-service registration (public), administrator account management, and ADMIN/MODERATOR read access to the user list. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    // Mirrors UserSortMeta's UI-sortable set -- marketplace-rest-api can't import that class
    // (wrong dependency direction, see .claude/rules/marketplace-rest-api.md), so both sides
    // independently reference the same UserDto.Fields.* constants instead.
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            UserDto.Fields.id, UserDto.Fields.name, UserDto.Fields.email, UserDto.Fields.role,
            UserDto.Fields.createdAt, UserDto.Fields.updatedAt);

    private final UserProfileService userProfileService;
    private final AuthorizationService authorizationService;
    private final UserDeleteService userDeleteService;
    private final ActivityHistorySupport activityHistorySupport;
    private final SnapshotRestoreService snapshotRestoreService;

    @Operation(summary = "Register a new account", description = "Public, no authentication needed. Rate-limited by client IP (5 failed attempts / 15 min). Follow up with POST /api/api-keys (Basic auth) to obtain a bearer key for the other endpoints.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {
              "name": "Jane Doe",
              "email": "jane@example.com",
              "password": "password123"
            }""")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserCreatedResponse register(@RequestBody @Valid SignUpDto dto, HttpServletRequest request) {
        userProfileService.register(dto, request.getRemoteAddr());
        UserDto user = userProfileService.findByEmail(dto.getEmail()).orElseThrow();
        return new UserCreatedResponse(user.id(), user.name(), user.email(), user.role());
    }

    @Operation(summary = "List/filter/sort users", description = "Admin/moderator only. size is not caller-supplied -- it comes from the caller's saved settings (PATCH /api/users/me/settings).")
    @GetMapping
    @SecurityRequirement(name = "bearerKey")
    public ResponseEntity<List<UserDto>> list(@AuthenticationPrincipal Long actorId, @ModelAttribute @Valid UserFilterDto filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(required = false) String sort, UriComponentsBuilder uriBuilder) {
        authorizationService.requireIsPrivileged(actorId);
        int size = userProfileService.resolveUsersPageSize(actorId);
        Sort sortObj = SortQueryParser.parse(sort, SORTABLE_FIELDS);
        List<UserDto> items = userProfileService.getFiltered(filter, page, size, sortObj);
        int total = userProfileService.count(filter);
        return PagedResponseBuilder.build(uriBuilder, page, size, total, items);
    }

    @Operation(summary = "Get one user by id", description = "Admin/moderator only. The ETag response header carries the version to send back via If-Match on PUT.")
    @ApiResponse(responseCode = "200", headers = @Header(name = "ETag", description = "Version to send back via If-Match on PUT"))
    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public ResponseEntity<UserDto> getById(@AuthenticationPrincipal Long actorId, @PathVariable Long id) {
        authorizationService.requireIsPrivileged(actorId);
        UserDto user = userProfileService.findById(id).orElseThrow(NoSuchElementException::new);
        return ETagUtil.withVersion(ResponseEntity.ok(), user.version()).body(user);
    }

    @Operation(operationId = "getUserHistory", summary = "List a user's change history",
            description = "Newest first. Administrators and moderators see every entry; anyone else sees only the entries they authored.")
    @GetMapping("/{id}/history")
    @SecurityRequirement(name = "bearerKey")
    public List<ActivityHistorySupport.HistoryEntryResponse> history(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id) {
        return activityHistorySupport.history(actorId, EntityType.USER, id);
    }

    @Operation(operationId = "getUserHistorySnapshot", summary = "Get one recorded state of a user",
            description = "Returns the recorded state and its version, for a restoring update (PUT with If-Match). Administrators and moderators can read any snapshot; anyone else only one from their own visible history. 403 when the snapshot is not visible to the caller, 404 when unknown.")
    @GetMapping("/{id}/history/{snapshotId}")
    @SecurityRequirement(name = "bearerKey")
    public ActivityHistorySupport.SnapshotResponse historySnapshot(@AuthenticationPrincipal @NonNull Long actorId,
            @PathVariable @NonNull Long id, @PathVariable @NonNull Long snapshotId) {
        return activityHistorySupport.snapshot(actorId, EntityType.USER, id, snapshotId, UserSnapshotDto.class);
    }

    @Operation(operationId = "updateUser", summary = "Update a user's name and role", description = "Full replace of name and role. A user may change their own name; only an administrator may change a role, and never their own. If-Match must carry the version from the last GET /api/users/{id} ETag.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {
              "name": "Max Moderator",
              "role": "MODERATOR"
            }""")))
    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public ResponseEntity<UserDto> update(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @RequestBody @Valid UserUpdateRequest request,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        userProfileService.save(new UserProfileDto(id, request.name(), request.role(), ETagUtil.parseIfMatch(ifMatch)), actorId);
        UserDto updated = userProfileService.findById(id).orElseThrow(NoSuchElementException::new);
        return ETagUtil.withVersion(ResponseEntity.ok(), updated.version()).body(updated);
    }

    @Operation(operationId = "restoreUserFromSnapshot", summary = "Restore a user to a recorded state",
            description = "Returns the user's name and role to the recorded state through the normal save path (same permissions and validation as an update); nothing else is restored. If-Match carries the current version. 403 when the snapshot is not visible to the caller, 404 when unknown, 412 when the version is stale.")
    @PostMapping("/{id}/history/{snapshotId}/restore")
    @SecurityRequirement(name = "bearerKey")
    public ResponseEntity<UserDto> restoreFromSnapshot(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id,
            @PathVariable @NonNull Long snapshotId,
            @Parameter(description = "Current version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        snapshotRestoreService.restoreUser(actorId, id, snapshotId, ETagUtil.requireIfMatch(ifMatch));
        UserDto restored = userProfileService.findById(id).orElseThrow(NoSuchElementException::new);
        return ETagUtil.withVersion(ResponseEntity.ok(), restored.version()).body(restored);
    }

    @Operation(operationId = "deleteUser", summary = "Delete a user", description = "Allowed for the account's owner or an administrator, as in the UI. The user's advertisements, provider profile and API keys are removed first.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id) {
        userDeleteService.delete(id, actorId);
    }

    @Operation(operationId = "changeMyLocale", summary = "Change the caller's own language", description = "Supported locales: en, uk.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {
              "locale": "uk"
            }""")))
    @PutMapping("/me/locale")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void updateLocale(@AuthenticationPrincipal Long actorId, @RequestBody @Valid LocaleWriteRequest request) {
        userProfileService.updateLocale(actorId, request.locale());
    }

    @Operation(summary = "Get the caller's own paginated-list page-size preferences", description = "ETag response header carries the version to send back via If-Match on PATCH.")
    @ApiResponse(responseCode = "200", headers = @Header(name = "ETag", description = "Version to send back via If-Match on PATCH"))
    @GetMapping("/me/settings")
    @SecurityRequirement(name = "bearerKey")
    public ResponseEntity<UserSettingsDto> getSettings(@AuthenticationPrincipal Long actorId) {
        UserSettingsDto settings = userProfileService.loadSettings(actorId);
        return ETagUtil.withVersion(ResponseEntity.ok(), settings.getVersion()).body(settings);
    }

    @Operation(summary = "Update the caller's own paginated-list page-size preferences", description = "Same validation limits as the Settings UI form. If-Match must carry the version from the last GET response's ETag.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {
              "adsPageSize": 20,
              "usersPageSize": 20,
              "timelinePageSize": 20,
              "providerProfilesPageSize": 20
            }""")))
    @PatchMapping("/me/settings")
    @SecurityRequirement(name = "bearerKey")
    public UserSettingsDto updateSettings(@AuthenticationPrincipal Long actorId, @RequestBody @Valid UserSettingsWriteRequest request,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        UserSettingsDto dto = UserSettingsDto.builder()
                .adsPageSize(request.adsPageSize())
                .usersPageSize(request.usersPageSize())
                .timelinePageSize(request.timelinePageSize())
                .providerProfilesPageSize(request.providerProfilesPageSize())
                .version(ETagUtil.parseIfMatch(ifMatch))
                .build();
        userProfileService.saveSettings(actorId, actorId, dto);
        return userProfileService.loadSettings(actorId);
    }

    @Operation(operationId = "getMySettingsHistory", summary = "List the caller's own settings change history",
            description = "Newest first. Administrators and moderators see every entry; anyone else sees only the entries they authored.")
    @GetMapping("/me/settings/history")
    @SecurityRequirement(name = "bearerKey")
    public List<ActivityHistorySupport.HistoryEntryResponse> mySettingsHistory(@AuthenticationPrincipal @NonNull Long actorId) {
        return activityHistorySupport.history(actorId, EntityType.USER_SETTINGS, actorId);
    }

    @Operation(operationId = "getMySettingsHistorySnapshot", summary = "Get one recorded state of the caller's own settings",
            description = "Returns the recorded state and its version, for a restoring update (PATCH with If-Match). Administrators and moderators can read any snapshot; anyone else only one from their own visible history. 403 when the snapshot is not visible to the caller, 404 when unknown.")
    @GetMapping("/me/settings/history/{snapshotId}")
    @SecurityRequirement(name = "bearerKey")
    public ActivityHistorySupport.SnapshotResponse mySettingsHistorySnapshot(@AuthenticationPrincipal @NonNull Long actorId,
            @PathVariable @NonNull Long snapshotId) {
        return activityHistorySupport.snapshot(actorId, EntityType.USER_SETTINGS, actorId, snapshotId, SettingsSnapshotDto.class);
    }

    @Operation(operationId = "restoreMySettingsFromSnapshot", summary = "Restore the caller's own settings to a recorded state",
            description = "Returns the page-size preferences to the recorded state through the normal save path (same validation as PATCH /api/users/me/settings). If-Match carries the current version. 403 when the snapshot is not visible to the caller, 404 when unknown, 412 when the version is stale.")
    @PostMapping("/me/settings/history/{snapshotId}/restore")
    @SecurityRequirement(name = "bearerKey")
    public UserSettingsDto restoreMySettingsFromSnapshot(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long snapshotId,
            @Parameter(description = "Current version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        snapshotRestoreService.restoreSettings(actorId, actorId, snapshotId, ETagUtil.requireIfMatch(ifMatch));
        return userProfileService.loadSettings(actorId);
    }

    /** The newly registered user's identity, returned after a successful {@code POST /api/users}. */
    public record UserCreatedResponse(Long id, String name, String email, Role role) {
    }

    /** Caller-writable settings fields -- version is server-managed (If-Match header), never client-supplied here. */
    public record UserSettingsWriteRequest(
            @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE) int adsPageSize,
            @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE) int usersPageSize,
            @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE) int timelinePageSize,
            @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE) int providerProfilesPageSize
    ) {
    }

    /** Caller-writable user fields -- id and version are server-managed (path variable / If-Match header). */
    public record UserUpdateRequest(@NotBlank @Size(max = SignUpDto.NAME_MAX_LENGTH) String name, @NotNull Role role) {
    }

    /** The language to switch the caller's own account to. */
    public record LocaleWriteRequest(@NotNull @Pattern(regexp = SupportedLocales.TAG_PATTERN) String locale) {
    }
}
