package org.ost.marketplace.config;

import jakarta.validation.Validator;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGallery;
import org.ost.marketplace.ui.views.components.attachment.CardMediaLightbox;
import org.ost.marketplace.ui.views.components.audit.AuditActivityListRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditActivityPanel;
import org.ost.marketplace.ui.views.components.audit.AuditActivityRowRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditTimelineListRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditTimelineRowRenderer;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGalleryService;
import org.ost.marketplace.ui.query.elements.SortIcon;
import org.ost.marketplace.ui.query.elements.SvgIcon;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.UserPickerField;
import org.ost.marketplace.ui.query.elements.fields.QueryMultiSelectComboField;
import org.ost.marketplace.ui.query.elements.fields.QueryNumberField;
import org.ost.marketplace.ui.query.elements.fields.QueryTextField;
import org.ost.marketplace.ui.query.elements.rows.QueryInlineRow;
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
    ValidationService<?> validationService(Validator validator) {
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

    // ── Query UI factories ────────────────────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<QueryTextField> queryTextFieldFactory(ObjectProvider<QueryTextField> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<QueryDateTimeField> queryDateTimeFieldFactory(ObjectProvider<QueryDateTimeField> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<QueryNumberField> queryNumberFieldFactory(ObjectProvider<QueryNumberField> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    @SuppressWarnings("rawtypes")
    public UiComponentFactory<QueryMultiSelectComboField> queryMultiSelectComboFieldFactory(ObjectProvider<QueryMultiSelectComboField> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<UserPickerField> userPickerFieldFactory(ObjectProvider<UserPickerField> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<QueryInlineRow> queryInlineRowFactory(ObjectProvider<QueryInlineRow> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<SortIcon> sortIconFactory(ObjectProvider<SortIcon> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<SvgIcon> svgIconFactory(ObjectProvider<SvgIcon> p) {
        return new UiComponentFactory<>(p);
    }
}
