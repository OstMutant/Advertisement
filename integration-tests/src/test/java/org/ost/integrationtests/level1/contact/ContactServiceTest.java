package org.ost.integrationtests.level1.contact;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.contact.config.ContactAutoConfiguration;
import org.ost.contact.services.ContactService;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.contact.dto.ContactInfoDto;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link ContactService#save} upsert behavior -- looks up any existing row by {@code (entityType, entityId)} itself, since a caller never knows the row id directly. */
@SpringBootTest(classes = {
        ContactAutoConfiguration.class,
        RepositoryTestSupport.class,
        ValidationAutoConfiguration.class
})
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class ContactServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ContactService contactService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    @Test
    void save_secondCallForSameEntity_updatesExistingRowInPlace() {
        ContactInfoDto first = contactService.save(new ContactInfoDto(
                null, EntityType.PROVIDER_PROFILE, 1L, "+380501111111", null, null, null, null));

        ContactInfoDto second = contactService.save(new ContactInfoDto(
                null, EntityType.PROVIDER_PROFILE, 1L, "+380502222222", "mastername", null, null, first.version()));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.phone()).isEqualTo("+380502222222");
        assertThat(second.telegram()).isEqualTo("mastername");
        assertThat(contactService.find(EntityType.PROVIDER_PROFILE, 1L).orElseThrow().phone())
                .isEqualTo("+380502222222");
    }

    @Test
    void save_invalidPhoneFormat_throwsConstraintViolationException() {
        assertThatThrownBy(() -> contactService.save(new ContactInfoDto(
                null, EntityType.PROVIDER_PROFILE, 1L, "not-a-phone", null, null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
