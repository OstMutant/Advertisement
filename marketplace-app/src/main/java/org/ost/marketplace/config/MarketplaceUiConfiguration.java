package org.ost.marketplace.config;

import jakarta.validation.Validator;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGallery;
import org.ost.marketplace.ui.views.components.attachment.AttachmentLightbox;
import org.ost.marketplace.ui.views.components.attachment.AttachmentThumbnail;
import org.ost.marketplace.ui.views.components.attachment.CardLightboxViewer;
import org.ost.marketplace.ui.views.components.attachment.CardMediaLightbox;
import org.ost.marketplace.ui.views.components.audit.AuditActivityListRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditActivityPanel;
import org.ost.marketplace.ui.views.components.audit.AuditActivityRowRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditTimelineListRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditTimelineRowRenderer;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGalleryService;
import org.ost.marketplace.ui.query.elements.fields.UserPickerField;
import org.ost.marketplace.ui.query.filter.ValidationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"org.ost.marketplace.ui.views.components.audit", "org.ost.marketplace.ui.views.components.attachment", "org.ost.marketplace.ui.query", "org.ost.marketplace.spi"})
public class MarketplaceUiConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("rawtypes")
    ValidationService validationService(Validator validator) {
        return new ValidationService<>(validator);
    }

    // ── Audit UI factories ────────────────────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AuditActivityPanel> auditActivityPanelFactory(ObjectProvider<AuditActivityPanel> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AuditTimelineListRenderer> auditActivityListRendererFactory(ObjectProvider<AuditTimelineListRenderer> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AuditActivityListRenderer> auditHistoryListRendererFactory(ObjectProvider<AuditActivityListRenderer> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AuditActivityRowRenderer> auditHistoryRowRendererFactory(ObjectProvider<AuditActivityRowRenderer> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AuditTimelineRowRenderer> auditActivityRowRendererFactory(ObjectProvider<AuditTimelineRowRenderer> p) {
        return new UiComponentFactory<>(p);
    }

    // ── Attachment UI factories ───────────────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AttachmentGalleryService> attachmentGalleryServiceFactory(ObjectProvider<AttachmentGalleryService> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AttachmentGallery> attachmentGalleryFactory(ObjectProvider<AttachmentGallery> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<CardMediaLightbox> cardMediaLightboxFactory(ObjectProvider<CardMediaLightbox> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<CardLightboxViewer> cardLightboxViewerFactory(ObjectProvider<CardLightboxViewer> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AttachmentThumbnail> attachmentThumbnailFactory(ObjectProvider<AttachmentThumbnail> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AttachmentLightbox> attachmentLightboxFactory(ObjectProvider<AttachmentLightbox> p) {
        return new UiComponentFactory<>(p);
    }

    // ── Query UI factories ────────────────────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<UserPickerField> userPickerFieldFactory(ObjectProvider<UserPickerField> p) {
        return new UiComponentFactory<>(p);
    }
}
