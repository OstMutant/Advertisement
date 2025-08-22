package org.ost.advertisement.ui.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.UserPrincipal;
import org.ost.advertisement.utils.PasswordEncoderUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class TestSecurityUtil {

	public static void setTestUser() {
		User testUser = new User();
		testUser.setId(1000L);
		testUser.setName("admin");
		testUser.setEmail("admin@example.com");
		testUser.setPasswordHash(PasswordEncoderUtil.encode("test123"));
		testUser.setRole(Role.ADMIN);
		UserPrincipal principal = new UserPrincipal(testUser);
		Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(auth);
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
		request.getSession(true).setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
	}
}
