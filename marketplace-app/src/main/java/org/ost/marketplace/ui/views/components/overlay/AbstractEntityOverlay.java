package org.ost.marketplace.ui.views.components.overlay;

import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.components.overlay.fields.OverlayBreadcrumbBackButton;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.springframework.dao.OptimisticLockingFailureException;

@SuppressWarnings("java:S110")
public abstract class AbstractEntityOverlay<H extends AbstractFormOverlayModeHandler<?>> extends BaseOverlay {

    public record SaveConfig(I18nKey success, I18nKey validFailed, I18nKey saveError, I18nKey conflict) {}

    protected OverlayLayout               layout;
    protected OverlayBreadcrumbBackButton breadcrumbButton;
    protected H                           currentFormHandler;

    protected abstract EntityOverlaySupport getSupport();
    protected abstract String               getOverlayCssClass();
    protected abstract I18nKey              getBreadcrumbLabelKey();
    protected abstract void                 switchTo();
    protected abstract SaveConfig           saveConfig();
    protected abstract void                 proceed();
    protected abstract void                 afterDiscard();

    protected I18nService         i18n()        { return getSupport().getI18n(); }
    protected NotificationService notification() { return getSupport().getNotification(); }

    protected final boolean hasUnsavedChanges() {
        return currentFormHandler != null && currentFormHandler.hasChanges();
    }

    protected final void handleSave() {
        try {
            if (currentFormHandler.save()) {
                notification().success(saveConfig().success());
                currentFormHandler.afterSave(true);
                proceed();
            } else {
                if (saveConfig().validFailed() != null) notification().error(saveConfig().validFailed());
                currentFormHandler.afterSave(false);
            }
        } catch (OptimisticLockingFailureException e) {
            if (saveConfig().conflict() != null) notification().error(saveConfig().conflict());
            else notification().error(e.getMessage());
            currentFormHandler.afterSave(false);
        } catch (Exception e) {
            if (saveConfig().saveError() != null) notification().error(saveConfig().saveError(), e.getMessage());
            else notification().error(e.getMessage());
            currentFormHandler.afterSave(false);
        }
    }

    protected final void doCancel() {
        if (currentFormHandler != null) currentFormHandler.discardChanges();
        afterDiscard();
    }

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
