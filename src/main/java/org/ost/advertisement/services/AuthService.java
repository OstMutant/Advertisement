package org.ost.advertisement.services;

import org.ost.advertisement.repository.UserRepository;
import org.ost.advertisement.ui.utils.SessionUtil;
import org.ost.advertisement.utils.PasswordEncoderUtil;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final UserRepository userRepository;

	public AuthService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public boolean login(String email, String rawPassword) {
		return userRepository.findByEmail(email)
			.filter(user -> PasswordEncoderUtil.matches(rawPassword, user.getPasswordHash()))
			.map(user -> {
				SessionUtil.setCurrentUser(user);
				return true;
			})
			.orElse(false);
	}

	public void logout() {
		SessionUtil.clearUser();
	}
}
