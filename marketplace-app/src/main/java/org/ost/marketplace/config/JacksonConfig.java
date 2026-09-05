package org.ost.marketplace.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@RequiredArgsConstructor
public class JacksonConfig {

    @Qualifier("auditObjectMapper")
    private final ObjectMapper auditObjectMapper;

    // Overrides UserAutoConfiguration's own conditional bean of the same name -- unconditional, so this one wins.
    @Bean("userSettingsObjectMapper")
    public tools.jackson.databind.ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    @PostConstruct
    void registerAuditSnapshotSubtypes() {
        auditObjectMapper.registerSubtypes(
                AdvertisementSnapshotDto.class,
                UserSnapshotDto.class,
                SettingsSnapshotDto.class,
                TaxonSnapshotDto.class,
                ProviderProfileSnapshotDto.class
        );
    }
}
