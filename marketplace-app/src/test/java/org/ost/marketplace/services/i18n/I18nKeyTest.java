package org.ost.marketplace.services.i18n;

import org.junit.jupiter.api.Test;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class I18nKeyTest {

    @Test
    void everyKey_hasNonBlankMessageSourceKey() {
        assertThat(I18nKey.values()).extracting(I18nKey::key).allMatch(key -> key != null && !key.isBlank());
    }

    @Test
    void everyKey_isUnique() {
        var distinctCount = java.util.Arrays.stream(I18nKey.values()).map(I18nKey::key).collect(Collectors.toSet()).size();

        assertThat(distinctCount).isEqualTo(I18nKey.values().length);
    }

    @Test
    void toTestId_lowercasesAndDashesTheEnumName() {
        assertThat(I18nKey.HEADER_LOGIN.toTestId()).isEqualTo("header-login");
        assertThat(I18nKey.COMMON_NOTIFICATION_ACCESS_DENIED.toTestId()).isEqualTo("common-notification-access-denied");
    }

    @Test
    void forAction_mapsEveryActionTypeToItsOwnKey() {
        assertThat(I18nKey.forAction(ActionType.CREATED)).isEqualTo(I18nKey.AUDIT_ACTIVITY_ACTION_CREATED);
        assertThat(I18nKey.forAction(ActionType.UPDATED)).isEqualTo(I18nKey.AUDIT_ACTIVITY_ACTION_UPDATED);
        assertThat(I18nKey.forAction(ActionType.DELETED)).isEqualTo(I18nKey.AUDIT_ACTIVITY_ACTION_DELETED);
        assertThat(I18nKey.forAction(ActionType.RESTORED)).isEqualTo(I18nKey.AUDIT_ACTIVITY_ACTION_RESTORED);
    }

    @Test
    void forAdKind_mapsEveryAdKindToItsOwnKey() {
        assertThat(I18nKey.forAdKind(AdKind.OFFER)).isEqualTo(I18nKey.ADVERTISEMENT_AD_KIND_OFFER);
        assertThat(I18nKey.forAdKind(AdKind.REQUEST)).isEqualTo(I18nKey.ADVERTISEMENT_AD_KIND_REQUEST);
        assertThat(I18nKey.forAdKind(AdKind.PRODUCT)).isEqualTo(I18nKey.ADVERTISEMENT_AD_KIND_PRODUCT);
    }

    @Test
    void forProviderKind_mapsEveryProviderKindToItsOwnKey() {
        assertThat(I18nKey.forProviderKind(ProviderKind.MASTER)).isEqualTo(I18nKey.PROVIDER_KIND_MASTER);
        assertThat(I18nKey.forProviderKind(ProviderKind.SHOP)).isEqualTo(I18nKey.PROVIDER_KIND_SHOP);
        assertThat(I18nKey.forProviderKind(ProviderKind.SUPPORT)).isEqualTo(I18nKey.PROVIDER_KIND_SUPPORT);
    }

    @Test
    void forEntityType_mapsEveryEntityTypeToItsOwnKey() {
        assertThat(I18nKey.forEntityType(EntityType.ADVERTISEMENT)).isEqualTo(I18nKey.ENTITY_TYPE_ADVERTISEMENT);
        assertThat(I18nKey.forEntityType(EntityType.USER)).isEqualTo(I18nKey.ENTITY_TYPE_USER);
        assertThat(I18nKey.forEntityType(EntityType.USER_SETTINGS)).isEqualTo(I18nKey.ENTITY_TYPE_USER_SETTINGS);
        assertThat(I18nKey.forEntityType(EntityType.TAXON)).isEqualTo(I18nKey.ENTITY_TYPE_TAXON);
        assertThat(I18nKey.forEntityType(EntityType.PROVIDER_PROFILE)).isEqualTo(I18nKey.ENTITY_TYPE_PROVIDER_PROFILE);
    }
}
