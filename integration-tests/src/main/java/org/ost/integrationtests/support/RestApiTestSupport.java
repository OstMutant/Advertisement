package org.ost.integrationtests.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.ost.orchestrator.spi.CurrentUserHook;
import org.ost.orchestrator.spi.SessionActorHook;
import org.ost.orchestrator.spi.SettingsChangeHook;
import org.ost.orchestrator.spi.UiLabelHook;
import org.ost.orchestrator.spi.UiLocaleHook;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Shared Level 3 wiring: stand-ins for the marketplace-app forwarder hooks (the real ones are covered by marketplace-app unit tests and the end-to-end suite), the audit snapshot subtype registration, plus a request-scoped {@link AuditorAware} reading the resolved bearer-key principal. */
@TestConfiguration
public class RestApiTestSupport {

    // marketplace-app's JacksonConfig does this in production; a Level 3 context has no such class.
    @Autowired
    @Qualifier("auditObjectMapper")
    private ObjectMapper auditObjectMapper;

    @PostConstruct
    void registerAuditSnapshotSubtypes() {
        auditObjectMapper.registerSubtypes(AdvertisementSnapshotDto.class, ProviderProfileSnapshotDto.class,
                TaxonSnapshotDto.class, UserSnapshotDto.class, SettingsSnapshotDto.class);
    }

    // @CreatedBy/@LastModifiedBy read this per insert -- mirrors marketplace-app's JdbcAuditingConfig, off the resolved bearer-key principal instead of a Vaadin session.
    @Bean
    public AuditorAware<Long> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.getPrincipal() instanceof Long id ? Optional.of(id) : Optional.empty();
        };
    }

    @Bean
    public SessionActorHook sessionActorHook() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.getPrincipal() instanceof Long id ? Optional.of(id) : Optional.empty();
        };
    }

    @Bean
    public UiLocaleHook uiLocaleHook() {
        return Optional::empty;
    }

    @Bean
    public CurrentUserHook currentUserHook() {
        return new CurrentUserHook() {
            @Override
            public Optional<UserDto> getCurrentUser() {
                return Optional.empty();
            }

            @Override
            public Optional<String> getCurrentUserLocale() {
                return Optional.empty();
            }
        };
    }

    @Bean
    public SettingsChangeHook settingsChangeHook() {
        return (userId, settings) -> { };
    }

    @Bean
    public UiLabelHook uiLabelHook() {
        return new UiLabelHook() {
            @Override
            public String translateActorDeletedSuffix(String actorName) {
                return actorName;
            }

            @Override
            public String labelFor(AdKind kind) {
                return kind.name();
            }

            @Override
            public String markDeleted(String name) {
                return name;
            }

            @Override
            public String noMediaPlaceholder() {
                return "";
            }
        };
    }
}
