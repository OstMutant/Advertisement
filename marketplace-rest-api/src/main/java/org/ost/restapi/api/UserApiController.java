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
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.model.PageSizeLimits;
import org.ost.restapi.api.concurrency.ETagUtil;
import org.ost.restapi.api.paging.PagedResponseBuilder;
import org.ost.restapi.api.paging.SortQueryParser;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

/** Self-service registration (public) plus ADMIN/MODERATOR read access to the user list. */
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

    @Operation(summary = "Register a new account", description = "Public, no authentication needed. Rate-limited by client IP (5 failed attempts / 15 min). Follow up with POST /api/api-keys (Basic auth) to obtain a bearer key for the other endpoints.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
            {"name":"Jane Doe","email":"jane@example.com","password":"password123"}""")))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserCreatedResponse register(@RequestBody @Valid SignUpDto dto, HttpServletRequest request) {
        userProfileService.register(dto, request.getRemoteAddr());
        UserDto user = userProfileService.findByEmail(dto.getEmail()).orElseThrow();
        return new UserCreatedResponse(user.id(), user.name(), user.email());
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

    @Operation(summary = "Get one user by id", description = "Admin/moderator only.")
    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public UserDto getById(@AuthenticationPrincipal Long actorId, @PathVariable Long id) {
        authorizationService.requireIsPrivileged(actorId);
        return userProfileService.findById(id).orElseThrow(NoSuchElementException::new);
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
            {"adsPageSize":20,"usersPageSize":20,"timelinePageSize":20}""")))
    @PatchMapping("/me/settings")
    @SecurityRequirement(name = "bearerKey")
    public UserSettingsDto updateSettings(@AuthenticationPrincipal Long actorId, @RequestBody @Valid UserSettingsWriteRequest request,
            @Parameter(description = "Version from the last GET response's ETag") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        UserSettingsDto dto = UserSettingsDto.builder()
                .adsPageSize(request.adsPageSize())
                .usersPageSize(request.usersPageSize())
                .timelinePageSize(request.timelinePageSize())
                .version(ETagUtil.parseIfMatch(ifMatch))
                .build();
        userProfileService.saveSettings(actorId, dto);
        return userProfileService.loadSettings(actorId);
    }

    /** The newly registered user's identity, returned after a successful {@code POST /api/users}. */
    public record UserCreatedResponse(Long id, String name, String email) {
    }

    /** Caller-writable settings fields -- version is server-managed (If-Match header), never client-supplied here. */
    public record UserSettingsWriteRequest(
            @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE) int adsPageSize,
            @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE) int usersPageSize,
            @Min(PageSizeLimits.MIN_PAGE_SIZE) @Max(PageSizeLimits.MAX_PAGE_SIZE) int timelinePageSize
    ) {
    }
}
