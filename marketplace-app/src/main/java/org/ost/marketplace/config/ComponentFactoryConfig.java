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
import org.ost.marketplace.ui.views.main.tabs.advertisements.card.AdvertisementCardMetaPanel;
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

/** Central registry of {@code UiComponentFactory<T>} beans, one {@code @Bean} per
 *  {@code Configurable} prototype UI type. */
@Configuration
public class ComponentFactoryConfig {

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<AdvertisementEditDto>> advertisementFormBinderFactory(
            ObjectProvider<OverlayFormBinder<AdvertisementEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<UserEditDto>> userFormBinderFactory(
            ObjectProvider<OverlayFormBinder<UserEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<TaxonEditDto>> taxonFormBinderFactory(
            ObjectProvider<OverlayFormBinder<TaxonEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<SettingsEditDto>> settingsFormBinderFactory(
            ObjectProvider<OverlayFormBinder<SettingsEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<CityEditDto>> cityFormBinderFactory(
            ObjectProvider<OverlayFormBinder<CityEditDto>> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<OverlayFormBinder<ProviderProfileEditDto>> providerProfileFormBinderFactory(
            ObjectProvider<OverlayFormBinder<ProviderProfileEditDto>> p) {
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
    public UiComponentFactory<AccountNameFormModeHandler> accountNameFormModeHandlerFactory(ObjectProvider<AccountNameFormModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<AccountNameViewModeHandler> accountNameViewModeHandlerFactory(ObjectProvider<AccountNameViewModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ProviderProfileFormOverlayModeHandler> providerProfileFormOverlayModeHandlerFactory(ObjectProvider<ProviderProfileFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ProviderProfileViewModeHandler> providerProfileViewModeHandlerFactory(ObjectProvider<ProviderProfileViewModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ProviderProfileCardView> providerProfileCardViewFactory(ObjectProvider<ProviderProfileCardView> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<ProviderProfileCatalogViewModeHandler> providerProfileCatalogViewModeHandlerFactory(ObjectProvider<ProviderProfileCatalogViewModeHandler> p) {
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

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<CityFormOverlayModeHandler> cityFormOverlayModeHandlerFactory(ObjectProvider<CityFormOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public UiComponentFactory<CityViewOverlayModeHandler> cityViewOverlayModeHandlerFactory(ObjectProvider<CityViewOverlayModeHandler> p) {
        return new UiComponentFactory<>(p);
    }
}
