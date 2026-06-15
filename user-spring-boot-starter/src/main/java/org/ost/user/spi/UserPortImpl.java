package org.ost.user.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.spi.UserPort;
import org.ost.user.entity.User;
import org.ost.user.security.UserPrincipal;
import org.ost.user.services.UserService;
import org.ost.user.services.UserSettingsService;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPortImpl implements UserPort {

    private final UserService         userService;
    private final UserSettingsService settingsService;

    @Override
    public List<UserDto> getFiltered(@NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort) {
        return userService.getFiltered(filter, page, size, sort).stream().map(UserPortImpl::toDto).toList();
    }

    @Override
    public int count(@NonNull UserFilterDto filter) {
        return userService.count(filter);
    }

    @Override
    public void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId) {
        userService.save(dto, actingUserId);
    }

    @Override
    public void refreshCurrentUserInContext(@NonNull Long userId) {
        try {
            User user = userService.findById(userId).orElseThrow();
            UserPrincipal principal = new UserPrincipal(user);
            Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
            Authentication newAuth = currentAuth != null
                    ? new UsernamePasswordAuthenticationToken(principal, currentAuth.getCredentials(), principal.getAuthorities())
                    : new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            log.debug("Refreshed security principal for user id={}", userId);
        } catch (Exception ex) {
            log.error("Failed to refresh security principal for user id={}", userId, ex);
        }
    }

    @Override
    public void updateLocale(@NonNull Long userId, @NonNull String locale) {
        userService.updateLocale(userId, locale);
    }

    @Override
    public void delete(@NonNull Long userId) {
        userService.delete(userId);
    }

    @Override
    public void register(@NonNull SignUpDto dto) {
        userService.register(dto);
    }

    @Override
    public Optional<UserDto> findById(@NonNull Long id) {
        return userService.findById(id).map(UserPortImpl::toDto);
    }

    @Override
    public Optional<UserDto> restoreToSnapshot(@NonNull Long userId, @NonNull Long snapshotId, @NonNull Long actingUserId) {
        return userService.restoreToSnapshot(userId, snapshotId, actingUserId).map(UserPortImpl::toDto);
    }

    @Override
    public Optional<UserDto> findByEmail(@NonNull String email) {
        return userService.findByEmail(email).map(UserPortImpl::toDto);
    }

    @Override
    public List<Long> findExistingIds(@NonNull Long[] ids) {
        return userService.findExistingIds(ids);
    }

    @Override
    public Map<Long, String> findActorNames(@NonNull Collection<Long> ids) {
        return userService.findActorNames(ids instanceof java.util.Set<Long> s ? s : new java.util.HashSet<>(ids));
    }

    @Override
    public List<ChangeEntry> expandActivityFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item) {
        return userService.expandActivityFields(item);
    }

    @Override
    public UserSettingsDto loadSettings(@NonNull Long userId) {
        return settingsService.load(userId);
    }

    @Override
    public void saveSettings(@NonNull Long userId, @NonNull UserSettingsDto settings) {
        settingsService.save(userId, settings);
    }

    static UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLocale());
    }
}
