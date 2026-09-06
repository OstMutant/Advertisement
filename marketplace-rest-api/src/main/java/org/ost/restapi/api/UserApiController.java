package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.restapi.api.paging.PagedResponseBuilder;
import org.ost.restapi.api.paging.SortQueryParser;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserCreatedResponse register(@RequestBody @Valid SignUpDto dto, HttpServletRequest request) {
        userProfileService.register(dto, request.getRemoteAddr());
        UserDto user = userProfileService.findByEmail(dto.getEmail()).orElseThrow();
        return new UserCreatedResponse(user.id(), user.name(), user.email());
    }

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

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerKey")
    public UserDto getById(@AuthenticationPrincipal Long actorId, @PathVariable Long id) {
        authorizationService.requireIsPrivileged(actorId);
        return userProfileService.findById(id).orElseThrow(NoSuchElementException::new);
    }

    /** Updates the caller's own paginated-list page-size preferences, same validation as the Settings UI form. */
    @PatchMapping("/me/settings")
    @SecurityRequirement(name = "bearerKey")
    public UserSettingsDto updateSettings(@AuthenticationPrincipal Long actorId, @RequestBody @Valid UserSettingsDto dto) {
        userProfileService.saveSettings(actorId, dto);
        return userProfileService.loadSettings(actorId);
    }

    /** The newly registered user's identity, returned after a successful {@code POST /api/users}. */
    public record UserCreatedResponse(Long id, String name, String email) {
    }
}
