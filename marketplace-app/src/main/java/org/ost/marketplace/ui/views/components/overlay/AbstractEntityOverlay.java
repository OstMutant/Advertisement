package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.Component;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.orchestrator.services.AccessDeniedException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.OVERLAY_BREADCRUMB_VIEW;

@Slf4j
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

    // Overridden by overlays with a View mode; Settings keeps the false defaults.
    protected boolean isEditMode()      { return false; }
    protected boolean enteredFromView() { return false; }

    protected List<BreadcrumbStep> buildBreadcrumbSteps() {
        List<BreadcrumbStep> steps = new ArrayList<>();
        steps.add(new BreadcrumbStep(i18n().get(getBreadcrumbLabelKey()), this::closeToList));
        if (isEditMode() && enteredFromView()) {
            steps.add(new BreadcrumbStep(i18n().get(OVERLAY_BREADCRUMB_VIEW), this::handleCancel));
        }
        return steps;
    }

    protected List<Component> buildBreadcrumbLinks() {
        return buildBreadcrumbLinks(buildBreadcrumbSteps());
    }

    // Reuses an already-computed steps list, avoiding a duplicate buildBreadcrumbSteps() call.
    protected List<Component> buildBreadcrumbLinks(List<BreadcrumbStep> steps) {
        return steps.stream()
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
        } catch (AccessDeniedException e) {
            log.warn("Access denied on save: {}", e.getMessage());
            notification().accessDenied();
            currentFormHandler.afterSave(false);
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
