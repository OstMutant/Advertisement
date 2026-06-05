package org.ost.marketplace.ui.views.components.overlay;

import org.ost.marketplace.common.I18nKey;
import org.ost.platform.core.i18n.I18nService;
import org.ost.marketplace.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.marketplace.ui.views.services.NotificationService;

@SuppressWarnings("java:S110")
public abstract class AbstractEntityOverlay extends BaseOverlay {

    protected OverlayLayout               layout;
    protected OverlayBreadcrumbBackButton breadcrumbButton;

    protected abstract EntityOverlaySupport getSupport();
    protected abstract String               getOverlayCssClass();
    protected abstract I18nKey              getBreadcrumbLabelKey();
    protected abstract boolean              hasUnsavedChanges();
    protected abstract void                 switchTo();
    protected abstract void                 doCancel();

    protected I18nService         i18n()         { return getSupport().getI18n(); }
    protected NotificationService notification()  { return getSupport().getNotification(); }

    @Override
    protected void buildContent() {
        addClassName(getOverlayCssClass());
        breadcrumbButton = getSupport().createBreadcrumbButton(getBreadcrumbLabelKey(), this::closeToList);
    }

    @Override
    protected void onEsc() {
        handleCancel();
    }

    protected void launchSession(Runnable doSwitch) {
        if (layout != null) layout.removeFromParent();
        layout = getSupport().createLayout(breadcrumbButton);
        doSwitch.run();
        add(layout);
        open();
    }

    protected void handleCancel() {
        getSupport().handleCancel(hasUnsavedChanges(), this::doCancel);
    }
}
