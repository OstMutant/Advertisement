package org.ost.advertisement.services.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.AuditPort;
import org.ost.advertisement.dto.SignUpDto;
import org.ost.advertisement.dto.UserProfileDto;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.EntityMarker;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.audit.services.AuditQueryService;
import org.ost.advertisement.services.audit.SettingsSnapshot;
import org.ost.advertisement.services.audit.UserSnapshot;
import org.ost.advertisement.services.auth.AuthContextService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private final UserRepository      repository;
    private final AccessEvaluator     access;
    private final PasswordEncoder     passwordEncoder;
    private final AuditPort           auditPort;
    private final AuditQueryService   auditQueryService;
    private final AuthContextService  authContextService;

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
            auditPort.captureUpdate(updated.getId(),
                    UserSnapshot.from(before),
                    UserSnapshot.from(updated),
                    changedBy);
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
        auditPort.captureCreation(saved.getId(), UserSnapshot.from(saved), saved.getId());
        UserSettings defaults = UserSettings.defaultSettings();
        auditPort.captureCreation(saved.getId(), SettingsSnapshot.from(defaults), saved.getId());
    }

    public Optional<User> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public Optional<User> restoreBeforeSnapshot(Long snapshotId, Long actingUserId) {
        return auditQueryService.getUserStateBefore(snapshotId).flatMap(state -> {
            User before = repository.findById(state.userId()).orElse(null);
            repository.updateProfile(new UserProfileDto(state.userId(), state.name(), state.role()));
            return repository.findById(state.userId()).map(updated -> {
                auditPort.captureUpdate(updated.getId(),
                        UserSnapshot.from(before),
                        UserSnapshot.from(updated),
                        actingUserId);
                return updated;
            });
        });
    }

    @Transactional
    public Optional<User> restoreToSnapshot(Long snapshotId, Long actingUserId) {
        return auditQueryService.getUserStateAt(snapshotId).flatMap(state -> {
            User before = repository.findById(state.userId()).orElse(null);
            repository.updateProfile(new UserProfileDto(state.userId(), state.name(), state.role()));
            return repository.findById(state.userId()).map(updated -> {
                auditPort.captureUpdate(updated.getId(),
                        UserSnapshot.from(before),
                        UserSnapshot.from(updated),
                        actingUserId);
                return updated;
            });
        });
    }

    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }
}
