package org.ost.advertisement.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.ui.utils.SessionUtil;
import org.ost.advertisement.utils.PasswordEncoderUtil;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;

	public boolean login(String email, String rawPassword) {
		return userRepository.findByEmail(email)
			.filter(user -> PasswordEncoderUtil.matches(rawPassword, user.getPasswordHash()))
			.map(this::setCurrentUser)
			.orElse(false);
	}

	public void logout() {
		SessionUtil.clearUser();
	}

	private boolean setCurrentUser(User user) {
		SessionUtil.setCurrentUser(user);
		return true;
	}
}
