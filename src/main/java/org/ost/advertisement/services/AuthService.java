package org.ost.advertisement.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.security.UserPrincipal;
import org.ost.advertisement.utils.PasswordEncoderUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;

	private final HttpServletRequest request;

	public boolean login(String email, String rawPassword) {
		Optional<User> optionalUser = userRepository.findByEmail(email);
		if (optionalUser.isEmpty()) {
			return false;
		}

		User user = optionalUser.get();
		if (!PasswordEncoderUtil.matches(rawPassword, user.getPasswordHash())) {
			return false;
		}

		UserPrincipal principal = new UserPrincipal(user);
		Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(auth);
		request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
		return true;
	}

	public void logout() {
		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.removeAttribute("SPRING_SECURITY_CONTEXT");
			session.invalidate();
		}
	}

}
