package org.ost.marketplace.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.audit.AdvertisementSnapshotDto;
import org.ost.marketplace.dto.audit.SettingsSnapshotDto;
import org.ost.marketplace.dto.audit.UserSnapshotDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class JacksonConfig {

    @Qualifier("auditObjectMapper")
    private final ObjectMapper auditObjectMapper;

    @Bean("userSettingsObjectMapper")
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @PostConstruct
    void registerAuditSnapshotSubtypes() {
        auditObjectMapper.registerSubtypes(
                AdvertisementSnapshotDto.class,
                UserSnapshotDto.class,
                SettingsSnapshotDto.class
        );
    }
}
