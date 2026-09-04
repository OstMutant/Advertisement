package org.ost.restapi.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.restapi.api.UserRegistrationController.UserCreatedResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationControllerTest {

    @Mock private UserProfileService userProfileService;
    @Mock private HttpServletRequest request;

    private UserRegistrationController controller;

    @BeforeEach
    void setUp() {
        controller = new UserRegistrationController(userProfileService);
    }

    @Test
    void register_registersThenResolvesIdByEmail() {
        SignUpDto dto = new SignUpDto();
        dto.setName("New User");
        dto.setEmail("new@example.com");
        dto.setPassword("password123");
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
        when(userProfileService.findByEmail("new@example.com"))
                .thenReturn(Optional.of(new UserDto(1L, "New User", "new@example.com", null, null, null, 0L)));

        UserCreatedResponse response = controller.register(dto, request);

        assertThat(response).isEqualTo(new UserCreatedResponse(1L, "New User", "new@example.com"));
        verify(userProfileService).register(dto, "203.0.113.5");
    }
}
