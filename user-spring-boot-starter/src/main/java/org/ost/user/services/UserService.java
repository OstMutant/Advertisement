package org.ost.user.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;
import org.ost.user.entity.User;
import org.ost.query.sort.OffsetPageable;
import org.ost.user.repository.UserRepository;
import org.ost.user.security.UserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private static final int MAX_REGISTER_ATTEMPTS = 5;

    private final Cache<String, AtomicInteger> registerAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    private final UserRepository              repository;
    private final PasswordEncoder             passwordEncoder;
    private final ComponentFactory<AuditPort> auditPortFactory;

    public List<UserDto> getFiltered(@Valid @NonNull UserFilterDto filter, int page, int size, @NonNull Sort sort) {
        return repository.findByFilter(filter, PageRequest.of(page, size, sort)).stream().map(this::toDto).toList();
    }

    public List<UserDto> getFilteredByOffset(@Valid @NonNull UserFilterDto filter, long offset, int limit, @NonNull Sort sort) {
        return repository.findByFilter(filter, new OffsetPageable(offset, limit, sort)).stream().map(this::toDto).toList();
    }

    public int count(@Valid @NonNull UserFilterDto filter) {
        return repository.countByFilter(filter).intValue();
    }

    @Transactional
    public void save(@NonNull UserProfileDto dto, @NonNull Long actingUserId) {
        log.info("User profile update: id={}", dto.id());
        User before = repository.findById(dto.id()).orElseThrow();
        repository.updateProfile(dto);
        repository.findById(dto.id()).ifPresent(updated ->
                auditPortFactory.ifAvailable(p -> p.captureUpdate(updated.getId(),
                        toSnapshot(before),
                        toSnapshot(updated),
                        actingUserId)));
    }

    @Transactional
    public void updateLocale(@NonNull Long userId, @NonNull String locale) {
        repository.updateLocale(userId, locale);
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

    // per-row, not one bulk statement -- an FK-blocked row is skipped, not fatal to the batch
    public void cleanup(int retentionDays) {
        List<Long> candidates = repository.findIdsDeletedOlderThan(retentionDays);
        int purged = 0;
        for (Long id : candidates) {
            try {
                repository.deleteById(id);
                purged++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Skipped purging user {} - still referenced elsewhere, will retry next run", id);
            }
        }
        log.info("User cleanup finished: purged={}, skipped={}", purged, candidates.size() - purged);
    }

    @Transactional
    public void register(@Valid @NonNull SignUpDto dto, @NonNull String clientIp) {
        AtomicInteger attempts = registerAttempts.get(clientIp, _ -> new AtomicInteger(0));
        if (attempts.get() >= MAX_REGISTER_ATTEMPTS) {
            throw new IllegalStateException("Too many failed registration attempts, try again later");
        }
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
            attempts.incrementAndGet();
            throw ex;
        }
        UserSettingsDto defaults = UserSettingsDto.defaultSettings();
        auditPortFactory.ifAvailable(p -> {
            p.captureCreation(saved.getId(), toSnapshot(saved),                       saved.getId());
            p.captureCreation(saved.getId(), SettingsSnapshotDto.from(defaults),      saved.getId());
        });
    }

    public Optional<UserDto> findById(@NonNull Long id) {
        return repository.findById(id).map(this::toDto);
    }

    @Transactional
    public Optional<UserDto> restoreToSnapshot(@NonNull Long userId, @NonNull Long snapshotId, @NonNull Long actingUserId) {
        return auditPortFactory.findIfAvailable()
                .flatMap(p -> p.<UserSnapshotDto>getSnapshotContent(snapshotId, EntityType.USER))
                .map(AuditSnapshotContentDto::snapshotData)
                .flatMap(snap -> applyUserRestore(userId, snap, actingUserId))
                .map(this::toDto);
    }

    private Optional<User> applyUserRestore(@NonNull Long userId, @NonNull UserSnapshotDto snap, @NonNull Long actingUserId) {
        User before = repository.findById(userId).orElseThrow();
        repository.updateProfile(new UserProfileDto(userId, snap.name(), Role.valueOf(snap.role()), before.getVersion()));
        return repository.findById(userId).map(updated -> {
            auditPortFactory.ifAvailable(p -> p.captureUpdate(updated.getId(),
                    toSnapshot(before),
                    toSnapshot(updated),
                    actingUserId));
            return updated;
        });
    }

    public void refreshSecurityContext(@NonNull Long userId) {
        try {
            User user = repository.findById(userId).orElseThrow();
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

    public Optional<User> findByEmail(@NonNull String email) {
        return repository.findByEmail(email);
    }

    public Optional<UserDto> findDtoByEmail(@NonNull String email) {
        return repository.findByEmail(email).map(this::toDto);
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
                .collect(Collectors.toMap(User::getId, this::toDto));
    }

    public List<ChangeEntry> expandActivityFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item) {
        return item.snapshotData() != null
                ? item.snapshotData().expandWithChanges(item.changes())
                : item.changes();
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.getCreatedAt(), user.getUpdatedAt(), user.getLocale(), user.getVersion());
    }

    private static UserSnapshotDto toSnapshot(User user) {
        return new UserSnapshotDto(user.getName(), user.getEmail(), user.getRole().name());
    }
}
