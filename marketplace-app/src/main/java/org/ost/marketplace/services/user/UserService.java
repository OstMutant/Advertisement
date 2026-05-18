package org.ost.marketplace.services.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditPort;
import org.ost.marketplace.dto.SignUpDto;
import org.ost.marketplace.dto.UserProfileDto;
import org.ost.platform.core.config.UserSettings;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.entities.EntityMarker;
import org.ost.platform.core.model.Role;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.exceptions.authorization.AccessDeniedException;
import org.ost.marketplace.repository.user.UserRepository;
import org.ost.marketplace.security.AccessEvaluator;
import org.ost.marketplace.services.audit.SettingsSnapshot;
import org.ost.platform.audit.dto.UserSnapshotState;
import org.ost.marketplace.services.audit.UserSnapshot;
import org.ost.marketplace.services.auth.AuthContextService;
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
        return applyUserRestore(auditPort.getUserStateBefore(snapshotId), actingUserId);
    }

    @Transactional
    public Optional<User> restoreToSnapshot(Long snapshotId, Long actingUserId) {
        return applyUserRestore(auditPort.getUserStateAt(snapshotId), actingUserId);
    }

    private Optional<User> applyUserRestore(Optional<UserSnapshotState> stateOpt, Long actingUserId) {
        return stateOpt.flatMap(state -> {
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
