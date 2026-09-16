package org.ost.marketplace.config;

import org.ost.marketplace.ui.dto.AdvertisementEditDto;
import org.ost.marketplace.ui.dto.CityEditDto;
import org.ost.marketplace.ui.dto.ProviderProfileEditDto;
import org.ost.marketplace.ui.dto.SettingsEditDto;
import org.ost.marketplace.ui.dto.TaxonEditDto;
import org.ost.marketplace.ui.dto.UserEditDto;
import org.ost.marketplace.ui.views.components.overlay.OverlayFormBinder;
import org.ost.marketplace.ui.views.main.header.account.AccountNameFormModeHandler;
import org.ost.marketplace.ui.views.main.header.account.AccountNameViewModeHandler;
import org.ost.marketplace.ui.views.main.header.account.ProviderProfileFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.header.account.ProviderProfileViewModeHandler;
import org.ost.marketplace.ui.views.main.tabs.advertisements.AdvertisementCardView;
import org.ost.marketplace.ui.views.main.tabs.providers.ProviderProfileCardView;
import org.ost.marketplace.ui.views.main.tabs.providers.overlay.ProviderProfileCatalogViewModeHandler;
import org.ost.marketplace.ui.views.components.EntityMetaPanel;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.modes.AdvertisementViewOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.users.UserGridConfigurator;
import org.ost.marketplace.ui.views.main.header.settings.SettingsFormModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.CityFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.CityViewOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.TaxonFormOverlayModeHandler;
import org.ost.marketplace.ui.views.main.tabs.referencedata.overlay.modes.TaxonViewOverlayModeHandler;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Central registry of {@code UiComponentFactory<T, T.Parameters>} beans, one {@code @Bean} per
 *  {@code Configurable} prototype UI type. */
@Configuration
public class ComponentFactoryConfig {

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<AdvertisementEditDto>, OverlayFormBinder.Parameters<AdvertisementEditDto>> advertisementFormBinderFactory(
            ObjectProvider<OverlayFormBinder<AdvertisementEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<UserEditDto>, OverlayFormBinder.Parameters<UserEditDto>> userFormBinderFactory(
            ObjectProvider<OverlayFormBinder<UserEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<TaxonEditDto>, OverlayFormBinder.Parameters<TaxonEditDto>> taxonFormBinderFactory(
            ObjectProvider<OverlayFormBinder<TaxonEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<SettingsEditDto>, OverlayFormBinder.Parameters<SettingsEditDto>> settingsFormBinderFactory(
            ObjectProvider<OverlayFormBinder<SettingsEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<CityEditDto>, OverlayFormBinder.Parameters<CityEditDto>> cityFormBinderFactory(
            ObjectProvider<OverlayFormBinder<CityEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<ProviderProfileEditDto>, OverlayFormBinder.Parameters<ProviderProfileEditDto>> providerProfileFormBinderFactory(
            ObjectProvider<OverlayFormBinder<ProviderProfileEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AdvertisementCardView, AdvertisementCardView.Parameters> advertisementCardViewFactory(ObjectProvider<AdvertisementCardView> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<EntityMetaPanel, EntityMetaPanel.Parameters> entityMetaPanelFactory(ObjectProvider<EntityMetaPanel> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AdvertisementViewOverlayModeHandler, AdvertisementViewOverlayModeHandler.Parameters> advertisementViewOverlayModeHandlerFactory(ObjectProvider<AdvertisementViewOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AdvertisementFormOverlayModeHandler, AdvertisementFormOverlayModeHandler.Parameters> advertisementFormOverlayModeHandlerFactory(ObjectProvider<AdvertisementFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AccountNameFormModeHandler, AccountNameFormModeHandler.Parameters> accountNameFormModeHandlerFactory(ObjectProvider<AccountNameFormModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AccountNameViewModeHandler, AccountNameViewModeHandler.Parameters> accountNameViewModeHandlerFactory(ObjectProvider<AccountNameViewModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ProviderProfileFormOverlayModeHandler, ProviderProfileFormOverlayModeHandler.Parameters> providerProfileFormOverlayModeHandlerFactory(ObjectProvider<ProviderProfileFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ProviderProfileViewModeHandler, ProviderProfileViewModeHandler.Parameters> providerProfileViewModeHandlerFactory(ObjectProvider<ProviderProfileViewModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ProviderProfileCardView, ProviderProfileCardView.Parameters> providerProfileCardViewFactory(ObjectProvider<ProviderProfileCardView> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ProviderProfileCatalogViewModeHandler, ProviderProfileCatalogViewModeHandler.Parameters> providerProfileCatalogViewModeHandlerFactory(ObjectProvider<ProviderProfileCatalogViewModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<UserGridConfigurator, UserGridConfigurator.Parameters> userGridConfiguratorFactory(ObjectProvider<UserGridConfigurator> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<SettingsFormModeHandler, SettingsFormModeHandler.Parameters> settingsFormModeHandlerFactory(ObjectProvider<SettingsFormModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<TaxonFormOverlayModeHandler, TaxonFormOverlayModeHandler.Parameters> taxonFormOverlayModeHandlerFactory(ObjectProvider<TaxonFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<TaxonViewOverlayModeHandler, TaxonViewOverlayModeHandler.Parameters> taxonViewOverlayModeHandlerFactory(ObjectProvider<TaxonViewOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<CityFormOverlayModeHandler, CityFormOverlayModeHandler.Parameters> cityFormOverlayModeHandlerFactory(ObjectProvider<CityFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<CityViewOverlayModeHandler, CityViewOverlayModeHandler.Parameters> cityViewOverlayModeHandlerFactory(ObjectProvider<CityViewOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }
}
