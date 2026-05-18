package org.ost.marketplace.config;

import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentCurrentUserProvider;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.services.auth.AuthContextService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
@RequiredArgsConstructor
public class AttachmentUserProviderConfig {

    private final AuthContextService authContextService;

    @Bean
    public AttachmentCurrentUserProvider attachmentCurrentUserProvider() {
        return () -> authContextService.getCurrentUser().map(User::getId);
    }
}
