package org.ost.marketplace.services.auth;

import org.ost.marketplace.entities.User;

import java.util.Optional;

public interface AuthContextService {
    Optional<User> getCurrentUser();

    void updateCurrentUser(User user);
}
