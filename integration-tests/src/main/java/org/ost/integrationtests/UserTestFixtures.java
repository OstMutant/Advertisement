package org.ost.integrationtests;

import lombok.NonNull;
import org.ost.platform.user.model.Role;
import org.ost.user.entity.User;
import org.ost.user.repository.UserRepository;

/**
 * Creates a minimal, valid {@code user_information} row for repository tests whose entity has a
 * foreign key to it (e.g. {@code advertisement.created_by}). Uses user-spring-boot-starter's own
 * real {@link User} entity and {@link UserRepository} — never a hand-rolled stub schema — so this
 * fixture can never silently drift from the real {@code user_information} schema. See
 * {@code integration-tests/CLAUDE.md} "Architecture decision" for why this lives here instead of
 * inside {@code user-spring-boot-starter} itself or {@code advertisement-spring-boot-starter}.
 *
 * <p>Requires the caller's Spring context to have {@code user-spring-boot-starter}'s
 * autoconfiguration active (for {@link UserRepository} to exist as a bean) and JDBC auditing
 * enabled with an {@code AuditorAware<Long>} bean (for {@code User.createdAt} to populate —
 * neither starter ships one itself; it is an app-level concern in production, provided here by
 * the consuming test's own {@code @TestConfiguration}).</p>
 */
public final class UserTestFixtures {

    private UserTestFixtures() {
    }

    /**
     * Inserts a new user with the given name/email and role {@link Role#USER}, returning the
     * saved entity (with its generated {@code id} — the value to use for FK-dependent columns
     * like {@code advertisement.created_by}).
     */
    public static User createTestUser(@NonNull UserRepository userRepository, @NonNull String name, @NonNull String email) {
        User user = User.builder()
                .name(name)
                .email(email)
                .role(Role.USER)
                .build();
        return userRepository.save(user);
    }
}
