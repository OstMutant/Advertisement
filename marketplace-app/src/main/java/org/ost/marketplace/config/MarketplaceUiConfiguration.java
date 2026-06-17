package org.ost.marketplace.config;

import jakarta.validation.Validator;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGallery;
import org.ost.marketplace.ui.views.components.attachment.CardMediaLightbox;
import org.ost.marketplace.ui.views.components.audit.AuditActivityListRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditActivityPanel;
import org.ost.marketplace.ui.views.components.audit.AuditActivityRowRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditTimelineListRenderer;
import org.ost.marketplace.ui.views.components.audit.AuditTimelinePanel;
import org.ost.marketplace.ui.views.components.audit.AuditTimelineRowRenderer;
import org.ost.platform.core.ComponentFactory;
import org.ost.marketplace.ui.views.components.attachment.AttachmentGalleryService;
import org.ost.marketplace.ui.query.elements.SortIcon;
import org.ost.marketplace.ui.query.elements.SvgIcon;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
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
    public ComponentFactory<AuditActivityPanel> auditActivityPanelFactory(ObjectProvider<AuditActivityPanel> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditTimelinePanel> auditTimelinePanelFactory(ObjectProvider<AuditTimelinePanel> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditTimelineListRenderer> auditActivityListRendererFactory(ObjectProvider<AuditTimelineListRenderer> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditActivityListRenderer> auditHistoryListRendererFactory(ObjectProvider<AuditActivityListRenderer> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditActivityRowRenderer> auditHistoryRowRendererFactory(ObjectProvider<AuditActivityRowRenderer> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditTimelineRowRenderer> auditActivityRowRendererFactory(ObjectProvider<AuditTimelineRowRenderer> p) {
        return new ComponentFactory<>(p);
    }

    // ── Attachment UI factories ───────────────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AttachmentGalleryService> attachmentGalleryServiceFactory(ObjectProvider<AttachmentGalleryService> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AttachmentGallery> attachmentGalleryFactory(ObjectProvider<AttachmentGallery> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<CardMediaLightbox> cardMediaLightboxFactory(ObjectProvider<CardMediaLightbox> p) {
        return new ComponentFactory<>(p);
    }

    // ── Query UI factories ────────────────────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<QueryTextField> queryTextFieldFactory(ObjectProvider<QueryTextField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<QueryDateTimeField> queryDateTimeFieldFactory(ObjectProvider<QueryDateTimeField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<QueryNumberField> queryNumberFieldFactory(ObjectProvider<QueryNumberField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    @SuppressWarnings("rawtypes")
    public ComponentFactory<QueryMultiSelectComboField> queryMultiSelectComboFieldFactory(ObjectProvider<QueryMultiSelectComboField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<QueryInlineRow> queryInlineRowFactory(ObjectProvider<QueryInlineRow> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<SortIcon> sortIconFactory(ObjectProvider<SortIcon> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<SvgIcon> svgIconFactory(ObjectProvider<SvgIcon> p) {
        return new ComponentFactory<>(p);
    }
}
