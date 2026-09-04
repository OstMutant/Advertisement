package org.ost.restapi.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public self-service registration, mirroring the Vaadin sign-up form's own flow.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRegistrationController {

    private final UserProfileService userProfileService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserCreatedResponse register(@RequestBody @Valid SignUpDto dto, HttpServletRequest request) {
        userProfileService.register(dto, request.getRemoteAddr());
        UserDto user = userProfileService.findByEmail(dto.getEmail()).orElseThrow();
        return new UserCreatedResponse(user.id(), user.name(), user.email());
    }

    /** The newly registered user's identity, returned after a successful {@code POST /api/users}. */
    public record UserCreatedResponse(Long id, String name, String email) {
    }
}
