package org.ost.advertisement.security.utils;

import java.util.Optional;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class AuthUtil {

	public static User getCurrentUser() {
		return getOptionalCurrentUser().orElse(null);
	}

	public static Optional<User> getOptionalCurrentUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			return Optional.empty();
		}

		Object principal = auth.getPrincipal();
		if (!(principal instanceof UserPrincipal)) {
			return Optional.empty();
		}

		return Optional.of(((UserPrincipal) principal).user());
	}

	public static void updateCurrentUser(User updatedUser) {
		UserPrincipal newPrincipal = new UserPrincipal(updatedUser);
		Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

		Authentication newAuth = new UsernamePasswordAuthenticationToken(
			newPrincipal,
			currentAuth.getCredentials(),
			currentAuth.getAuthorities()
		);

		SecurityContextHolder.getContext().setAuthentication(newAuth);
	}
}
