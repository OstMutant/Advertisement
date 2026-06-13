package org.ost.marketplace.config;

import jakarta.validation.Validator;
import org.ost.ui.attachment.AttachmentGallery;
import org.ost.ui.attachment.CardMediaLightbox;
import org.ost.ui.audit.AuditActivityListRenderer;
import org.ost.ui.audit.AuditActivityPanel;
import org.ost.ui.audit.AuditActivityRowRenderer;
import org.ost.ui.audit.AuditTimelineListRenderer;
import org.ost.ui.audit.AuditTimelinePanel;
import org.ost.ui.audit.AuditTimelineRowRenderer;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.ui.spi.attachment.AttachmentGalleryPort;
import org.ost.platform.ui.spi.audit.AuditUiPort;
import org.ost.ui.query.elements.SortIcon;
import org.ost.ui.query.elements.SvgIcon;
import org.ost.ui.query.elements.fields.QueryDateTimeField;
import org.ost.ui.query.elements.fields.QueryMultiSelectComboField;
import org.ost.ui.query.elements.fields.QueryNumberField;
import org.ost.ui.query.elements.fields.QueryTextField;
import org.ost.ui.query.elements.rows.QueryInlineRow;
import org.ost.ui.query.filter.ValidationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"org.ost.ui.audit", "org.ost.ui.attachment", "org.ost.ui.query", "org.ost.marketplace.spi"})
public class MarketplaceUiConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ValidationService<?> validationService(Validator validator) {
        return new ValidationService<>(validator);
    }

    // ── Audit UI factories ────────────────────────────────────────────────────

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditUiPort> auditUiPortFactory(ObjectProvider<AuditUiPort> p) {
        return new ComponentFactory<>(p);
    }

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
    public ComponentFactory<AttachmentGalleryPort> attachmentGalleryPortFactory(ObjectProvider<AttachmentGalleryPort> p) {
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
