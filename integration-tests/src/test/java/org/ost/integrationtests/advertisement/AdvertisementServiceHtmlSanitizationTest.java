package org.ost.integrationtests.advertisement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.advertisement.entity.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.user.spi.UserPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers improvement-027 Batch 2: {@link AdvertisementService}'s HTML sanitization policy —
 * OWASP {@code Sanitizers.FORMATTING.and(LINKS).and(BLOCKS)} plus a Jsoup visible-text-length
 * check (two distinct limits: {@link AdvertisementSaveDto#DESCRIPTION_MAX_LENGTH} for the
 * sanitized visible text, {@link AdvertisementSaveDto#DESCRIPTION_RAW_MAX_LENGTH} for the raw
 * HTML payload — see {@code marketplace-app/DECISIONS.md} ADR-024/ADR-031). Both {@code
 * sanitizeHtml()} and {@code buildEntity()} are {@code private static} — tested through the real
 * public {@link AdvertisementService#save} entry point instead, per {@code
 * integration-tests/DECISIONS.md} ADR-008.
 *
 * <p>No Spring context, no Testcontainers — {@link AdvertisementRepository} and every {@code
 * ComponentFactory} dependency are mocked directly (same shape as {@code UserServiceTest} in this
 * module); the sanitizer itself has no DB dependency, so a real Postgres would only add cost here,
 * not confidence.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdvertisementServiceHtmlSanitizationTest {

    @Mock
    private AdvertisementRepository repository;
    @Mock
    private ComponentFactory<AuditPort> auditPortFactory;
    @Mock
    private ComponentFactory<AttachmentPort> attachmentPortFactory;
    @Mock
    private ComponentFactory<TaxonPort> taxonPortFactory;
    @Mock
    private ComponentFactory<UserPort> userPortFactory;

    private AdvertisementService newService() {
        return new AdvertisementService(repository, auditPortFactory, attachmentPortFactory, taxonPortFactory, userPortFactory);
    }

    @Test
    void save_stripsDisallowedTags_keepsAllowedFormatting() {
        AdvertisementService service = newService();
        ArgumentCaptor<Advertisement> captor = ArgumentCaptor.forClass(Advertisement.class);
        when(repository.save(captor.capture()))
                .thenReturn(Advertisement.builder().id(1L).build());

        AdvertisementSaveDto dto = new AdvertisementSaveDto(
                null, "Title", "<script>alert(1)</script><b>Bold</b>", null, null);

        service.save(dto, 1L);

        assertThat(captor.getValue().getDescription())
                .doesNotContain("<script>")
                .contains("<b>Bold</b>");
    }

    @Test
    void save_descriptionExceedsVisibleTextMaxLength_throws() {
        AdvertisementService service = newService();
        String tooLong = "a".repeat(AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH + 1);
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, "Title", tooLong, null, null);

        assertThatThrownBy(() -> service.save(dto, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void save_descriptionAtVisibleTextMaxLength_succeeds() {
        AdvertisementService service = newService();
        ArgumentCaptor<Advertisement> captor = ArgumentCaptor.forClass(Advertisement.class);
        when(repository.save(captor.capture()))
                .thenReturn(Advertisement.builder().id(1L).build());
        String exactlyMax = "a".repeat(AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH);
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, "Title", exactlyMax, null, null);

        service.save(dto, 1L);

        assertThat(captor.getValue().getDescription()).hasSize(AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH);
    }

    @Test
    void save_htmlTagsDoNotCountTowardVisibleTextLength() {
        AdvertisementService service = newService();
        ArgumentCaptor<Advertisement> captor = ArgumentCaptor.forClass(Advertisement.class);
        when(repository.save(captor.capture()))
                .thenReturn(Advertisement.builder().id(1L).build());
        // Visible text is well under the limit; the HTML markup padding it out is not counted,
        // since validateDescriptionLength() measures Jsoup's parsed .text(), not raw HTML length.
        String visibleText = "a".repeat(AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH - 10);
        String html = "<b>" + visibleText + "</b>" + "<i></i>".repeat(50);
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, "Title", html, null, null);

        service.save(dto, 1L);

        assertThat(captor.getValue().getDescription()).contains(visibleText);
    }
}
