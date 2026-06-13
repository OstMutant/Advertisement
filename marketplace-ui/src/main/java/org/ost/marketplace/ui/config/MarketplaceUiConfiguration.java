package org.ost.marketplace.ui.config;

import jakarta.validation.Validator;
import org.ost.attachment.ui.AttachmentGallery;
import org.ost.attachment.ui.CardMediaLightbox;
import org.ost.audit.ui.AuditActivityListRenderer;
import org.ost.audit.ui.AuditActivityPanel;
import org.ost.audit.ui.AuditActivityRowRenderer;
import org.ost.audit.ui.AuditTimelineListRenderer;
import org.ost.audit.ui.AuditTimelinePanel;
import org.ost.audit.ui.AuditTimelineRowRenderer;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.ui.spi.attachment.AttachmentGalleryPort;
import org.ost.platform.ui.spi.audit.AuditUiPort;
import org.ost.query.ui.elements.SortIcon;
import org.ost.query.ui.elements.SvgIcon;
import org.ost.query.ui.elements.fields.QueryDateTimeField;
import org.ost.query.ui.elements.fields.QueryMultiSelectComboField;
import org.ost.query.ui.elements.fields.QueryNumberField;
import org.ost.query.ui.elements.fields.QueryTextField;
import org.ost.query.ui.elements.rows.QueryInlineRow;
import org.ost.query.ui.filter.ValidationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"org.ost.audit.ui", "org.ost.attachment.ui", "org.ost.query.ui"})
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
