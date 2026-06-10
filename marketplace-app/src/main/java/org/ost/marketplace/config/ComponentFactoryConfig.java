package org.ost.marketplace.config;

import org.ost.marketplace.ui.views.components.EmptyStateView;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.buttons.action.DeleteActionButton;
import org.ost.marketplace.ui.views.components.buttons.action.EditActionButton;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.fields.UiEmailField;
import org.ost.marketplace.ui.views.components.fields.UiLabeledField;
import org.ost.marketplace.ui.views.components.fields.UiPasswordField;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.marketplace.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.main.tabs.advertisements.AdvertisementCardView;
import org.ost.marketplace.ui.views.main.tabs.advertisements.card.AdvertisementCardMetaPanel;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementViewOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.UserGridConfigurator;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.modes.UserFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.modes.UserViewOverlayModeHandler;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComponentFactoryConfig {

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UiPrimaryButton> uiPrimaryButtonFactory(ObjectProvider<UiPrimaryButton> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UiTertiaryButton> uiTertiaryButtonFactory(ObjectProvider<UiTertiaryButton> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UiIconButton> uiIconButtonFactory(ObjectProvider<UiIconButton> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UiTextField> uiTextFieldFactory(ObjectProvider<UiTextField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UiEmailField> uiEmailFieldFactory(ObjectProvider<UiEmailField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UiPasswordField> uiPasswordFieldFactory(ObjectProvider<UiPasswordField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UiLabeledField> uiLabeledFieldFactory(ObjectProvider<UiLabeledField> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<EditActionButton> editActionButtonFactory(ObjectProvider<EditActionButton> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<DeleteActionButton> deleteActionButtonFactory(ObjectProvider<DeleteActionButton> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<EmptyStateView> emptyStateViewFactory(ObjectProvider<EmptyStateView> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<ConfirmActionDialog> confirmActionDialogFactory(ObjectProvider<ConfirmActionDialog> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<OverlayLayout> overlayLayoutFactory(ObjectProvider<OverlayLayout> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<OverlayBreadcrumbBackButton> overlayBreadcrumbBackButtonFactory(ObjectProvider<OverlayBreadcrumbBackButton> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    @SuppressWarnings("rawtypes")
    public ComponentFactory<OverlayFormBinder> overlayFormBinderFactory(ObjectProvider<OverlayFormBinder> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementCardView> advertisementCardViewFactory(ObjectProvider<AdvertisementCardView> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementCardMetaPanel> advertisementCardMetaPanelFactory(ObjectProvider<AdvertisementCardMetaPanel> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementViewOverlayModeHandler> advertisementViewOverlayModeHandlerFactory(ObjectProvider<AdvertisementViewOverlayModeHandler> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementFormOverlayModeHandler> advertisementFormOverlayModeHandlerFactory(ObjectProvider<AdvertisementFormOverlayModeHandler> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UserViewOverlayModeHandler> userViewOverlayModeHandlerFactory(ObjectProvider<UserViewOverlayModeHandler> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UserFormOverlayModeHandler> userFormOverlayModeHandlerFactory(ObjectProvider<UserFormOverlayModeHandler> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<UserGridConfigurator> userGridConfiguratorFactory(ObjectProvider<UserGridConfigurator> p) {
        return new ComponentFactory<>(p);
    }
}
