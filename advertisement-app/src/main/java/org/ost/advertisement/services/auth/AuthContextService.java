package org.ost.advertisement.services.auth;

import org.ost.advertisement.entities.User;

import java.util.Optional;

public interface AuthContextService {
    Optional<User> getCurrentUser();

    void updateCurrentUser(User user);
}
