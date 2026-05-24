package org.ost.marketplace.services.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.codec.SnapshotCodec;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.SnapshotContentDto;
import org.ost.platform.audit.dto.SnapshotPayloadDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private final UserRepository      repository;
    private final AccessEvaluator     access;
    private final PasswordEncoder     passwordEncoder;
    private final ObjectProvider<AuditPort> auditPort;
    private final AuthContextService  authContextService;
    private final SnapshotCodec       snapshotCodec;

    public List<User> getFiltered(@Valid UserFilterDto filter, int page, int size, Sort sort) {
        return repository.findByFilter(filter, PageRequest.of(page, size, sort));
    }

    public int count(@Valid UserFilterDto filter) {
        return repository.countByFilter(filter).intValue();
    }

    @Transactional
    public void save(UserProfileDto dto) {
        if (access.canNotEdit(dto)) {
            throw new AccessDeniedException("You cannot edit this user");
        }
        User before = repository.findById(dto.id()).orElse(null);
        repository.updateProfile(dto);
        repository.findById(dto.id()).ifPresent(updated -> {
            Long changedBy = authContextService.getCurrentUser().map(User::getId).orElse(dto.id());
            auditPort.ifAvailable(p -> p.captureUpdate(updated.getId(),
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
        if (access.canNotDelete(targetUser)) {
            throw new AccessDeniedException("You cannot delete this user");
        }
        repository.deleteById(targetUser.getId());
    }

    @Transactional
    public void register(@Valid SignUpDto dto) {
        boolean isFirstUser = repository.countByFilter(UserFilterDto.empty()).equals(0L);
        User newUser = User.builder()
                .name(dto.getName().trim())
                .email(dto.getEmail().trim())
                .passwordHash(passwordEncoder.encode(dto.getPassword().trim()))
                .role(isFirstUser ? Role.ADMIN : Role.USER)
                .build();
        User saved = repository.save(newUser);
        auditPort.ifAvailable(p -> p.captureCreation(saved.getId(), UserSnapshotDto.from(saved), saved.getId()));
        UserSettings defaults = UserSettings.defaultSettings();
        auditPort.ifAvailable(p -> p.captureCreation(saved.getId(), SettingsSnapshotDto.from(defaults), saved.getId()));
    }

    public Optional<User> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Optional<User> restoreBeforeSnapshot(Long userId, Long snapshotId, Long actingUserId) {
        Optional<SnapshotContentDto> content = Optional.ofNullable(auditPort.getIfAvailable())
                .flatMap(p -> p.getPreviousSnapshotContent(snapshotId, EntityType.USER));
        return applyUserRestore(userId, content, actingUserId);
    }

    @Transactional
    public Optional<User> restoreToSnapshot(Long userId, Long snapshotId, Long actingUserId) {
        Optional<SnapshotContentDto> content = Optional.ofNullable(auditPort.getIfAvailable())
                .flatMap(p -> p.getSnapshotContent(snapshotId, EntityType.USER));
        return applyUserRestore(userId, content, actingUserId);
    }

    private Optional<User> applyUserRestore(Long userId, Optional<SnapshotContentDto> contentOpt, Long actingUserId) {
        return contentOpt.flatMap(content -> parseUserSnapshotDto(content)).flatMap(snap -> {
            User before = repository.findById(userId).orElse(null);
            repository.updateProfile(new UserProfileDto(userId, snap.name(), Role.valueOf(snap.role())));
            return repository.findById(userId).map(updated -> {
                auditPort.ifAvailable(p -> p.captureUpdate(updated.getId(),
                        UserSnapshotDto.from(before),
                        UserSnapshotDto.from(updated),
                        actingUserId));
                return updated;
            });
        });
    }

    private Optional<UserSnapshotDto> parseUserSnapshotDto(SnapshotContentDto content) {
        return snapshotCodec.decode(content.snapshotData(), UserSnapshotDto.class);
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

    public String resolveDisplayName(EntityType entityType, SnapshotPayloadDto snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return "";
        return switch (entityType) {
            case USER -> snapshotCodec.decode(snapshot, UserSnapshotDto.class).map(UserSnapshotDto::name).orElse("");
            case USER_SETTINGS -> "Settings";
            default -> "";
        };
    }

    public List<ChangeEntry> expandActivityFields(ActivityItemDto item) {
        return switch (item.entityType()) {
            case USER_SETTINGS -> expandSettingsFields(item);
            default            -> expandUserFields(item);
        };
    }

    private List<ChangeEntry> expandUserFields(ActivityItemDto item) {
        return snapshotCodec.decode(item.snapshotData(), UserSnapshotDto.class)
                .map(state -> {
                    List<ChangeEntry> result = new ArrayList<>();
                    addActivityField(result, item.changes(), "name",  state.name());
                    addActivityField(result, item.changes(), "email", state.email());
                    addActivityField(result, item.changes(), "role",  state.role());
                    return result;
                })
                .orElseGet(item::changes);
    }

    private List<ChangeEntry> expandSettingsFields(ActivityItemDto item) {
        return snapshotCodec.decode(item.snapshotData(), SettingsSnapshotDto.class)
                .map(state -> {
                    List<ChangeEntry> result = new ArrayList<>();
                    addSettingField(result, item.changes(), "adsPageSize",   state.adsPageSize());
                    addSettingField(result, item.changes(), "usersPageSize", state.usersPageSize());
                    return result;
                })
                .orElseGet(item::changes);
    }

    private static void addSettingField(List<ChangeEntry> result, List<ChangeEntry> changes, String key, int currentValue) {
        changes.stream()
                .filter(c -> c instanceof ChangeEntry.SettingChange sc && key.equals(sc.key()))
                .findFirst()
                .ifPresentOrElse(
                        result::add,
                        () -> result.add(new ChangeEntry.SettingChange(key, null, currentValue))
                );
    }

    private static void addActivityField(List<ChangeEntry> result, List<ChangeEntry> changes, String field, String currentValue) {
        changes.stream()
                .filter(c -> c instanceof ChangeEntry.FieldChange fc && field.equals(fc.field()))
                .findFirst()
                .ifPresentOrElse(
                        result::add,
                        () -> result.add(new ChangeEntry.FieldChange(field, null, currentValue))
                );
    }
}
