package org.ost.user.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.FailureRateLimiter;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;
import org.ost.user.entity.User;
import org.ost.query.sort.OffsetPageable;
import org.ost.user.repository.UserPreferencesRepository;
import org.ost.user.repository.UserRepository;
import org.ost.user.security.UserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User domain business logic -- filtered queries, registration (first-user auto-admin promotion,
 * Caffeine-backed rate-limiting), profile updates, soft-delete, retention-purge candidate lookup
 * and purge, and Spring Security principal construction/refresh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private static final int MAX_REGISTER_ATTEMPTS = 5;

    private final FailureRateLimiter registerLimiter = new FailureRateLimiter(MAX_REGISTER_ATTEMPTS, Duration.ofMinutes(15));

    private final UserRepository            repository;
    private final UserPreferencesRepository preferencesRepository;
    private final PasswordEncoder           passwordEncoder;
    private final UserPreferencesService    preferencesService;
    private final ComponentFactory<AuditPort> auditPortFactory;

    public List<UserDto> getFiltered(@Valid @NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort) {
        return repository.findByFilter(filter, PageRequest.of(page, size, sort)).stream().map(User::toDto).toList();
    }

    public List<UserDto> getFilteredByOffset(@Valid @NonNull UserFilterDto filter, long offset, int limit, @NonNull Sort sort) {
        return repository.findByFilter(filter, new OffsetPageable(offset, limit, sort)).stream().map(User::toDto).toList();
    }

    public int count(@Valid @NonNull UserFilterDto filter) {
        return repository.countByFilter(filter).intValue();
    }

    @Transactional
    public void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId) {
        log.info("User profile update: id={}", dto.id());
        repository.findById(dto.id()).orElseThrow();
        repository.updateProfile(dto);
        repository.findById(dto.id()).ifPresent(updated ->
                auditPortFactory.ifAvailable(p -> p.captureUpdate(updated.getId(),
                        toSnapshot(updated),
                        actingUserId)));
    }

    @Transactional
    public void delete(@NonNull Long userId, @NonNull Long actingUserId) {
        log.info("User delete: id={}", userId);
        User before = repository.findById(userId).orElseThrow();
        repository.softDelete(userId, actingUserId);
        auditPortFactory.ifAvailable(p -> p.captureDeletion(userId, toSnapshot(before), actingUserId));
    }

    public Set<Long> findDeletedIds(@NonNull Set<Long> ids) {
        return repository.findDeletedIds(ids.toArray(new Long[0]));
    }

    public Set<Long> findIdsDeletedOlderThan(int retentionDays) {
        return Set.copyOf(repository.findIdsDeletedOlderThan(retentionDays));
    }

    @Transactional
    public void purge(@NonNull Set<Long> ids) {
        for (Long id : ids) {
            preferencesRepository.deleteByActorId(id);
            repository.deleteById(id);
        }
        log.info("User purge finished: purged={}", ids.size());
    }

    @Transactional
    public void register(@Valid @NonNull SignUpDto dto, @NonNull String clientIp) {
        registerLimiter.checkAllowed(clientIp, "Too many failed registration attempts, try again later");
        log.info("User register: email={}", dto.getEmail());
        boolean isFirstUser = repository.countByFilter(UserFilterDto.empty()).equals(0L);
        User newUser = User.builder()
                .name(dto.getName().trim())
                .email(dto.getEmail().trim())
                .passwordHash(passwordEncoder.encode(dto.getPassword().trim()))
                .role(isFirstUser ? Role.ADMIN : Role.USER)
                .build();
        User saved;
        try {
            saved = repository.save(newUser);
        } catch (DuplicateKeyException ex) {
            registerLimiter.recordFailure(clientIp);
            throw ex;
        }
        preferencesRepository.insertDefault(saved.getId());
        UserSettingsDto defaults = UserSettingsDto.defaultSettings();
        auditPortFactory.ifAvailable(p -> {
            p.captureCreation(saved.getId(), toSnapshot(saved),                       saved.getId());
            p.captureCreation(saved.getId(), preferencesService.toSettingsSnapshot(defaults), saved.getId());
        });
    }

    public Optional<UserDto> findById(@NonNull Long id) {
        return repository.findById(id).map(User::toDto);
    }

    public UserPrincipal toPrincipal(@NonNull User user) {
        return new UserPrincipal(user, preferencesService.findLocale(user.getId()));
    }

    public void refreshSecurityContext(@NonNull Long userId) {
        try {
            User user = repository.findById(userId).orElseThrow();
            UserPrincipal principal = toPrincipal(user);
            Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
            Authentication newAuth = new UsernamePasswordAuthenticationToken(
                    principal, currentAuth != null ? currentAuth.getCredentials() : null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            log.debug("Refreshed security principal for user id={}", userId);
        } catch (Exception ex) {
            log.error("Failed to refresh security principal for user id={}", userId, ex);
        }
    }

    public Optional<User> findByEmail(@NonNull String email) {
        return repository.findByEmail(email);
    }

    public Optional<UserDto> findDtoByEmail(@NonNull String email) {
        return repository.findByEmail(email).map(User::toDto);
    }

    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return Set.copyOf(repository.findExistingIds(ids.toArray(new Long[0])));
    }

    public Map<Long, String> findActorNames(@NonNull Collection<Long> ids) {
        Set<Long> idSet = ids instanceof Set<Long> s ? s : new HashSet<>(ids);
        return repository.findActorNames(idSet.toArray(new Long[0]));
    }

    public Map<Long, UserDto> findByIds(@NonNull Set<Long> ids) {
        return repository.findByIds(ids.toArray(new Long[0])).stream()
                .collect(Collectors.toMap(User::getId, User::toDto));
    }

    private static UserSnapshotDto toSnapshot(User user) {
        return new UserSnapshotDto(user.getName(), user.getEmail(), user.getRole().name());
    }
}
