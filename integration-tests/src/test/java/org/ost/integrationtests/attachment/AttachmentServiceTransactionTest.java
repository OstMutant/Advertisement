package org.ost.integrationtests.attachment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ost.attachment.config.AttachmentAutoConfiguration;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.services.AttachmentService;
import org.ost.attachment.services.AttachmentSnapshotService;
import org.ost.attachment.services.StorageService;
import org.ost.integrationtests.AbstractPostgresIntegrationTest;
import org.ost.integrationtests.support.TestDataCleaner;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;

/**
 * Covers improvement-049 item 3: {@link AttachmentService#upload} was not {@code @Transactional},
 * unlike its siblings ({@code delete()}/{@code restoreToUrls()}) in the same class. If {@code
 * captureMediaChanges()} threw after {@code attachmentRepository.save()}
 * had already committed, the {@code catch} block deleted the just-uploaded file from storage but
 * the DB row stayed committed — worse than doing nothing, since the row now points at a file that
 * no longer exists. Fixed by adding {@code @Transactional}, matching the sibling methods: an
 * exception thrown anywhere in {@code upload()} now rolls back the {@code save()} too, so there is
 * never a committed row with no corresponding file.
 *
 * <p>Uses its own minimal {@code TestConfig} rather than {@code RepositoryTestSupport} — needs a
 * {@link StorageService} stub (no real S3/MinIO dependency) and a {@link CurrentActorHook} that
 * actually returns an actor id (so {@code captureMediaChanges()}'s {@code ifPresent()} branch runs
 * at all), neither of which {@code RepositoryTestSupport} provides, same shape of reasoning as
 * {@code UserServiceRestoreTest}. {@link AttachmentSnapshotService} is replaced with a
 * {@code @MockitoBean} so its {@code capture()} call can be forced to throw — everything else
 * (repository, transaction manager, real Postgres) stays real, so the rollback this test proves is
 * a genuine database transaction rollback, not a Mockito call-order assertion.</p>
 */
@SpringBootTest(classes = {
        AttachmentAutoConfiguration.class,
        AttachmentServiceTransactionTest.TestConfig.class
})
class AttachmentServiceTransactionTest extends AbstractPostgresIntegrationTest {

    @TestConfiguration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcClientAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            DataJdbcRepositoriesAutoConfiguration.class,
            LiquibaseAutoConfiguration.class,
            TransactionAutoConfiguration.class,
            ConfigurationPropertiesAutoConfiguration.class
    })
    @EnableJdbcAuditing
    static class TestConfig {

        @Bean
        CurrentActorHook currentActorHook() {
            return () -> Optional.of(1L);
        }
    }

    // AttachmentS3Config's s3Client/s3StorageService beans have no @ConditionalOnMissingBean
    // ordering that a plain @Bean method in TestConfig above can reliably win (confirmed directly:
    // a same-named @Bean throws BeanDefinitionOverrideException, and a differently-named
    // type-matching @Bean still lost the race and the real s3Client construction ran anyway,
    // failing on the unset storage.s3.endpoint property). @MockitoBean replaces the bean via
    // Spring Test's dedicated override mechanism instead of competing through
    // @ConditionalOnMissingBean at all, so both are overridden this way rather than via TestConfig.
    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private AttachmentSnapshotService attachmentSnapshotService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        TestDataCleaner.cleanAll(jdbcClient);
        lenient().when(storageService.upload(anyString(), anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn("uploaded/file.jpg");
    }

    @Test
    void upload_captureMediaChangesThrows_rollsBackAttachmentRow() {
        doThrow(new RuntimeException("audit capture failed"))
                .when(attachmentSnapshotService).capture(any(), any(), any());

        assertThatThrownBy(() -> attachmentService.upload(EntityType.ADVERTISEMENT, 1L, "file.jpg",
                new ByteArrayInputStream("data".getBytes()), 4, "image/jpeg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("audit capture failed");

        List<Attachment> rows = attachmentRepository.getByEntityId(EntityType.ADVERTISEMENT, 1L);
        assertThat(rows).isEmpty();
    }

    @Test
    void upload_success_persistsAttachmentRow() {
        AttachmentItemDto saved = attachmentService.upload(EntityType.ADVERTISEMENT, 2L, "file.jpg",
                new ByteArrayInputStream("data".getBytes()), 4, "image/jpeg");

        assertThat(saved.id()).isNotNull();
        List<Attachment> rows = attachmentRepository.getByEntityId(EntityType.ADVERTISEMENT, 2L);
        assertThat(rows).hasSize(1);
    }
}
