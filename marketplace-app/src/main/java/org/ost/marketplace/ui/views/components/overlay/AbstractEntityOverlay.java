package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.Component;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;

@SuppressWarnings("java:S110")
public abstract class AbstractEntityOverlay<H extends AbstractFormOverlayModeHandler<?>> extends BaseOverlay {

    public record SaveConfig(I18nKey success, I18nKey validFailed, I18nKey saveError, I18nKey conflict) {}

    protected OverlayLayout layout;
    protected H             currentFormHandler;

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

    // Overridden to insert a "View" step when the session was entered via View.
    protected List<BreadcrumbStep> buildBreadcrumbSteps() {
        return List.of(new BreadcrumbStep(i18n().get(getBreadcrumbLabelKey()), this::closeToList));
    }

    protected List<Component> buildBreadcrumbLinks() {
        return buildBreadcrumbSteps().stream()
                .<Component>map(step -> getSupport().createBreadcrumbButton(step.label(), step.onClick()))
                .toList();
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
    }

    @Override
    protected void onEsc() {
        handleCancel();
    }

    protected void launchSession(Runnable doSwitch) {
        if (layout != null) layout.removeFromParent();
        layout = getSupport().createLayout(List.<Component>of());
        doSwitch.run();
        add(layout);
        open();
    }

    protected void handleCancel() {
        getSupport().handleCancel(hasUnsavedChanges(), this::doCancel);
    }
}
