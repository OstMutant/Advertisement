package org.ost.integrationtests.level1.contact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.contact.config.ContactAutoConfiguration;
import org.ost.contact.entity.ContactInfo;
import org.ost.contact.repository.ContactRepository;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.RepositoryTestSupport;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.contact.dto.ContactViewCountDto;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testcontainers repository test for {@link ContactRepository} -- entity ids are synthetic longs, since {@code contact_info}/{@code contact_view} carry no FK to any other table. */
@SpringBootTest(classes = {
        ContactAutoConfiguration.class,
        RepositoryTestSupport.class,
        ValidationAutoConfiguration.class
})
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class ContactRepositoryTest extends AbstractPostgresIntegrationTest {

    private static final AtomicLong ENTITY_ID_SEQ = new AtomicLong(1);

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
    }

    private static Long newEntityId() {
        return ENTITY_ID_SEQ.incrementAndGet();
    }

    @Test
    void save_and_findByEntity_returnsPersistedRow() {
        Long entityId = newEntityId();

        ContactInfo saved = contactRepository.save(ContactInfo.builder()
                .entityType(EntityType.PROVIDER_PROFILE)
                .entityId(entityId)
                .phone("+380501234567")
                .telegram("mastername")
                .viber("+380501234567")
                .build());

        Optional<ContactInfo> found = contactRepository.findByEntity(EntityType.PROVIDER_PROFILE, entityId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getPhone()).isEqualTo("+380501234567");
        assertThat(found.get().getTelegram()).isEqualTo("mastername");
        assertThat(found.get().getViber()).isEqualTo("+380501234567");
        assertThat(found.get().getVersion()).isZero();
    }

    @Test
    void findByEntity_noRow_returnsEmpty() {
        assertThat(contactRepository.findByEntity(EntityType.PROVIDER_PROFILE, newEntityId())).isEmpty();
    }

    @Test
    void findByEntity_differentEntityType_sameEntityId_doesNotCollide() {
        Long sharedId = newEntityId();
        contactRepository.save(ContactInfo.builder()
                .entityType(EntityType.PROVIDER_PROFILE)
                .entityId(sharedId)
                .phone("+380501111111")
                .build());
        contactRepository.save(ContactInfo.builder()
                .entityType(EntityType.ADVERTISEMENT)
                .entityId(sharedId)
                .phone("+380502222222")
                .build());

        assertThat(contactRepository.findByEntity(EntityType.PROVIDER_PROFILE, sharedId).orElseThrow().getPhone())
                .isEqualTo("+380501111111");
        assertThat(contactRepository.findByEntity(EntityType.ADVERTISEMENT, sharedId).orElseThrow().getPhone())
                .isEqualTo("+380502222222");
    }

    @Test
    void save_staleVersion_throwsStaleWriteException() {
        Long entityId = newEntityId();
        ContactInfo saved = contactRepository.save(ContactInfo.builder()
                .entityType(EntityType.ADVERTISEMENT)
                .entityId(entityId)
                .phone("+380501234567")
                .build());
        contactRepository.save(ContactInfo.builder()
                .id(saved.getId())
                .entityType(EntityType.ADVERTISEMENT)
                .entityId(entityId)
                .phone("+380509999999")
                .createdAt(saved.getCreatedAt())
                .version(saved.getVersion())
                .build());

        ContactInfo staleUpdate = ContactInfo.builder()
                .id(saved.getId())
                .entityType(EntityType.ADVERTISEMENT)
                .entityId(entityId)
                .phone("+380508888888")
                .createdAt(saved.getCreatedAt())
                .version(saved.getVersion())
                .build();

        assertThatThrownBy(() -> contactRepository.save(staleUpdate))
                .isInstanceOf(StaleWriteException.class);
    }

    @Test
    void recordView_and_countViewsThisMonth_countsPerChannelIndependently() {
        Long entityId = newEntityId();

        contactRepository.recordView(EntityType.PROVIDER_PROFILE, entityId, ContactChannel.PHONE, 42L);
        contactRepository.recordView(EntityType.PROVIDER_PROFILE, entityId, ContactChannel.PHONE, null);
        contactRepository.recordView(EntityType.PROVIDER_PROFILE, entityId, ContactChannel.TELEGRAM, 42L);

        List<ContactViewCountDto> counts = contactRepository.countViewsThisMonth(EntityType.PROVIDER_PROFILE, entityId);

        assertThat(counts).containsExactlyInAnyOrder(
                new ContactViewCountDto(ContactChannel.PHONE, 2L),
                new ContactViewCountDto(ContactChannel.TELEGRAM, 1L));
    }

    @Test
    void countViewsThisMonth_noViews_returnsEmptyList() {
        assertThat(contactRepository.countViewsThisMonth(EntityType.PROVIDER_PROFILE, newEntityId())).isEmpty();
    }
}
