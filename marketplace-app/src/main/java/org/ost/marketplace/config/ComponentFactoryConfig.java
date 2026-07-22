package org.ost.marketplace.config;

import org.ost.marketplace.ui.views.components.EmptyStateView;
import org.ost.marketplace.ui.views.components.dialogs.ConfirmActionDialog;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.components.overlay.OverlayLayout;
import org.ost.marketplace.ui.views.main.tabs.advertisements.AdvertisementCardView;
import org.ost.marketplace.ui.views.main.tabs.advertisements.card.AdvertisementCardMetaPanel;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementViewOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.UserGridConfigurator;
import org.ost.marketplace.ui.views.main.header.settings.SettingsFormModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.TaxonFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.TaxonViewOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.modes.UserFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.overlay.modes.UserViewOverlayModeHandler;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComponentFactoryConfig {

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<EmptyStateView> emptyStateViewFactory(ObjectProvider<EmptyStateView> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ConfirmActionDialog> confirmActionDialogFactory(ObjectProvider<ConfirmActionDialog> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayLayout> overlayLayoutFactory(ObjectProvider<OverlayLayout> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    @SuppressWarnings("rawtypes")
    public UiComponentFactory<OverlayFormBinder> overlayFormBinderFactory(ObjectProvider<OverlayFormBinder> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AdvertisementCardView> advertisementCardViewFactory(ObjectProvider<AdvertisementCardView> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AdvertisementCardMetaPanel> advertisementCardMetaPanelFactory(ObjectProvider<AdvertisementCardMetaPanel> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AdvertisementViewOverlayModeHandler> advertisementViewOverlayModeHandlerFactory(ObjectProvider<AdvertisementViewOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AdvertisementFormOverlayModeHandler> advertisementFormOverlayModeHandlerFactory(ObjectProvider<AdvertisementFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<UserViewOverlayModeHandler> userViewOverlayModeHandlerFactory(ObjectProvider<UserViewOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<UserFormOverlayModeHandler> userFormOverlayModeHandlerFactory(ObjectProvider<UserFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<UserGridConfigurator> userGridConfiguratorFactory(ObjectProvider<UserGridConfigurator> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<SettingsFormModeHandler> settingsFormModeHandlerFactory(ObjectProvider<SettingsFormModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<TaxonFormOverlayModeHandler> taxonFormOverlayModeHandlerFactory(ObjectProvider<TaxonFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<TaxonViewOverlayModeHandler> taxonViewOverlayModeHandlerFactory(ObjectProvider<TaxonViewOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }
}
