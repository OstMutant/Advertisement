package org.ost.marketplace.services.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.ComponentFactory;
import org.ost.marketplace.dto.SignUpDto;
import org.ost.marketplace.dto.UserProfileDto;
import org.ost.marketplace.entities.UserSettings;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.EntityMarker;
import org.ost.marketplace.entities.Role;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.exceptions.authorization.AccessDeniedException;
import org.ost.marketplace.repository.user.UserRepository;
import org.ost.marketplace.security.AccessEvaluator;
import org.ost.marketplace.dto.audit.SettingsSnapshotDto;
import org.ost.marketplace.dto.audit.UserSnapshotDto;
import org.ost.marketplace.services.auth.AuthContextService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private final UserRepository               repository;
    private final AccessEvaluator              access;
    private final PasswordEncoder              passwordEncoder;
    private final ComponentFactory<AuditPort>  auditPortFactory;
    private final AuthContextService           authContextService;

    public List<User> getFiltered(@Valid UserFilterDto filter, int page, int size, Sort sort) {
        return repository.findByFilter(filter, PageRequest.of(page, size, sort));
    }

    public int count(@Valid UserFilterDto filter) {
        return repository.countByFilter(filter).intValue();
    }

    @Transactional
    public void save(UserProfileDto dto) {
        log.info("User profile update: id={}", dto.id());
        if (access.canNotEdit(dto)) {
            throw new AccessDeniedException("You cannot edit this user");
        }
        User before = repository.findById(dto.id()).orElse(null);
        repository.updateProfile(dto);
        repository.findById(dto.id()).ifPresent(updated -> {
            Long changedBy = authContextService.getCurrentUser().map(User::getId).orElse(dto.id());
            auditPortFactory.ifAvailable(p -> p.captureUpdate(updated.getId(),
                    UserSnapshotDto.from(before),
                    UserSnapshotDto.from(updated),
                    changedBy));
        });
    }

    @Transactional
    public void updateLocale(Long userId, String locale) {
        repository.updateLocale(userId, locale);
    }

    @Transactional
    public void delete(EntityMarker targetUser) {
        log.info("User delete: id={}", targetUser.getId());
        if (access.canNotDelete(targetUser)) {
            throw new AccessDeniedException("You cannot delete this user");
        }
        repository.deleteById(targetUser.getId());
    }

    @Transactional
    public void register(@Valid SignUpDto dto) {
        log.info("User register: email={}", dto.getEmail());
        boolean isFirstUser = repository.countByFilter(UserFilterDto.empty()).equals(0L);
        User newUser = User.builder()
                .name(dto.getName().trim())
                .email(dto.getEmail().trim())
                .passwordHash(passwordEncoder.encode(dto.getPassword().trim()))
                .role(isFirstUser ? Role.ADMIN : Role.USER)
                .build();
        User saved = repository.save(newUser);
        auditPortFactory.ifAvailable(p -> p.captureCreation(saved.getId(), UserSnapshotDto.from(saved), saved.getId()));
        UserSettings defaults = UserSettings.defaultSettings();
        auditPortFactory.ifAvailable(p -> p.captureCreation(saved.getId(), SettingsSnapshotDto.from(defaults), saved.getId()));
    }

    public Optional<User> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Optional<User> restoreBeforeSnapshot(Long userId, Long snapshotId, Long actingUserId) {
        Optional<AuditSnapshotContentDto> content = Optional.ofNullable(auditPortFactory.getIfAvailable())
                .flatMap(p -> p.getPreviousSnapshotContent(snapshotId, EntityType.USER));
        return applyUserRestore(userId, content, actingUserId);
    }

    @Transactional
    public Optional<User> restoreToSnapshot(Long userId, Long snapshotId, Long actingUserId) {
        Optional<AuditSnapshotContentDto> content = Optional.ofNullable(auditPortFactory.getIfAvailable())
                .flatMap(p -> p.getSnapshotContent(snapshotId, EntityType.USER));
        return applyUserRestore(userId, content, actingUserId);
    }

    private Optional<User> applyUserRestore(Long userId, Optional<AuditSnapshotContentDto> contentOpt, Long actingUserId) {
        return contentOpt.flatMap(this::parseUserSnapshotDto).flatMap(snap -> {
            User before = repository.findById(userId).orElse(null);
            repository.updateProfile(new UserProfileDto(userId, snap.name(), Role.valueOf(snap.role())));
            return repository.findById(userId).map(updated -> {
                auditPortFactory.ifAvailable(p -> p.captureUpdate(updated.getId(),
                        UserSnapshotDto.from(before),
                        UserSnapshotDto.from(updated),
                        actingUserId));
                return updated;
            });
        });
    }

    private Optional<UserSnapshotDto> parseUserSnapshotDto(AuditSnapshotContentDto content) {
        return content.snapshotData() instanceof UserSnapshotDto u ? Optional.of(u) : Optional.empty();
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public List<Long> findExistingIds(Long[] ids) {
        return repository.findExistingIds(ids);
    }

    public Map<Long, String> findActorNames(Set<Long> ids) {
        return repository.findActorNames(ids.toArray(new Long[0]));
    }

    public String resolveDisplayName(EntityType entityType, AuditableSnapshot snapshot) {
        if (snapshot == null) return "";
        return switch (entityType) {
            case USER -> snapshot instanceof UserSnapshotDto u ? u.name() : "";
            case USER_SETTINGS -> "Settings";
            default -> "";
        };
    }

    public List<ChangeEntry> expandActivityFields(AuditActivityItemDto item) {
        return item.snapshotData() != null
                ? item.snapshotData().expandWithChanges(item.changes())
                : item.changes();
    }
}
