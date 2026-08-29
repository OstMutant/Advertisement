package org.ost.marketplace.ui.views.main.header.account;

import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.views.components.overlay.OverlayModeHandler;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.marketplace.ui.views.components.overlay.AbstractEntityOverlay;
import org.ost.marketplace.ui.views.components.overlay.AbstractFormOverlayModeHandler;
import org.ost.marketplace.ui.views.components.overlay.BreadcrumbStep;
import org.ost.marketplace.ui.views.components.overlay.EntityOverlaySupport;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.main.header.settings.SettingsFormModeHandler;
import org.ost.platform.user.dto.UserDto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.ost.marketplace.services.i18n.I18nKey.*;

/**
 * Replaces {@code SettingsOverlay} -- unified Name/Settings/Provider Profile account view for
 * both self-service and admin/moderator viewing another user's account (see
 * {@code improvement-178}). Reuses {@code AbstractEntityOverlay}'s existing single-active-handler
 * pattern with a 3-value {@code Section} switched via a {@code Tabs} component; Name and Provider
 * Profile each keep their own {@code NameMode}/{@code ProviderProfileMode} {@code VIEW,EDIT}
 * sub-state, mirroring the Advertisement/Taxon/City domains' own View/Edit split -- Edit button
 * visible only for self/admin per {@link org.ost.marketplace.services.security.AccessEvaluator#canEditUserAccount}.
 * Settings has no such split since it's pure preferences with no content to view separately from
 * editing.
 */
@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings({"java:S110", "java:S2065"})
public class AccountOverlay extends AbstractEntityOverlay<AbstractFormOverlayModeHandler<?>> {

    private enum Section {NAME, SETTINGS, PROVIDER_PROFILE}
    private enum NameMode {VIEW, EDIT}
    private enum ProviderProfileMode {VIEW, EDIT}

    private record OverlaySession(Section section, NameMode nameMode, ProviderProfileMode providerProfileMode,
                                   boolean enteredFromView, Long targetUserId,
                                   Consumer<UserDto> onUpdated, Runnable onClosed) {
        OverlaySession withSection(Section s) {
            return new OverlaySession(s, NameMode.VIEW, ProviderProfileMode.VIEW, false, targetUserId, onUpdated, onClosed);
        }
        OverlaySession toNameEdit() {
            return new OverlaySession(Section.NAME, NameMode.EDIT, providerProfileMode, true, targetUserId, onUpdated, onClosed);
        }
        OverlaySession toNameView() {
            return new OverlaySession(Section.NAME, NameMode.VIEW, providerProfileMode, false, targetUserId, onUpdated, onClosed);
        }
        OverlaySession toProviderProfileEdit() {
            return new OverlaySession(Section.PROVIDER_PROFILE, nameMode, ProviderProfileMode.EDIT, true, targetUserId, onUpdated, onClosed);
        }
        OverlaySession toProviderProfileView() {
            return new OverlaySession(Section.PROVIDER_PROFILE, nameMode, ProviderProfileMode.VIEW, false, targetUserId, onUpdated, onClosed);
        }
    }

    @Getter private final transient EntityOverlaySupport support;
    private final transient UiComponentFactory<AccountNameViewModeHandler>             nameViewHandlerFactory;
    private final transient UiComponentFactory<AccountNameFormModeHandler>             nameFormHandlerFactory;
    private final transient UiComponentFactory<SettingsFormModeHandler>                settingsHandlerFactory;
    private final transient UiComponentFactory<ProviderProfileViewModeHandler>         providerProfileViewHandlerFactory;
    private final transient UiComponentFactory<ProviderProfileFormOverlayModeHandler>  providerProfileHandlerFactory;
    private final transient ProviderProfileSaveService                                 providerProfileSaveService;

    private OverlaySession session;

    @Override protected String  getOverlayCssClass()   { return "account-overlay"; }
    @Override protected I18nKey getBreadcrumbLabelKey() { return HEADER_HOME; }

    @Override protected boolean isEditMode() {
        return (session.section() == Section.NAME && session.nameMode() == NameMode.EDIT)
                || (session.section() == Section.PROVIDER_PROFILE && session.providerProfileMode() == ProviderProfileMode.EDIT);
    }

    @Override protected boolean enteredFromView() { return session.enteredFromView(); }

    @Override
    protected SaveConfig saveConfig() {
        return switch (session.section()) {
            case NAME -> new SaveConfig(
                    USER_DIALOG_NOTIFICATION_SUCCESS, USER_DIALOG_NOTIFICATION_VALIDATION_FAILED,
                    USER_DIALOG_NOTIFICATION_SAVE_ERROR, USER_DIALOG_NOTIFICATION_CONFLICT);
            case SETTINGS -> new SaveConfig(SETTINGS_SAVED_SUCCESS, null, null, null);
            case PROVIDER_PROFILE -> new SaveConfig(
                    PROVIDER_PROFILE_OVERLAY_NOTIFICATION_SUCCESS, PROVIDER_PROFILE_OVERLAY_NOTIFICATION_VALIDATION_FAILED,
                    PROVIDER_PROFILE_OVERLAY_NOTIFICATION_SAVE_ERROR, PROVIDER_PROFILE_OVERLAY_NOTIFICATION_CONFLICT);
        };
    }

    @Override
    protected void proceed() {
        // stays open after save, same as the SettingsOverlay it replaces -- nothing to close
        if (session.section() == Section.NAME && session.nameMode() == NameMode.EDIT) {
            UserDto fresh = ((AccountNameFormModeHandler) currentFormHandler).getSavedUser();
            if (fresh != null) session.onUpdated().accept(fresh);
        }
    }

    @Override
    protected void afterDiscard() {
        if (session.section() == Section.NAME && session.nameMode() == NameMode.EDIT && session.enteredFromView()) {
            session = session.toNameView();
            switchTo();
        } else if (session.section() == Section.PROVIDER_PROFILE && session.providerProfileMode() == ProviderProfileMode.EDIT) {
            session = session.toProviderProfileView();
            switchTo();
        } else {
            closeToList();
        }
    }

    /** {@code onUpdated} splices the freshly-saved user into the caller's own row/list data in place instead of forcing a full refresh. */
    public void openFor(Long targetUserId, Consumer<UserDto> onUpdated, Runnable onClosed) {
        ensureInitialized();
        session = new OverlaySession(Section.NAME, NameMode.VIEW, ProviderProfileMode.VIEW, false, targetUserId, onUpdated, onClosed);
        launchSession(this::switchTo);
    }

    /** Opens directly in Name Edit mode -- used by the Users grid's own Edit row action, which
     *  skips the View screen entirely (mirrors the deleted {@code UserOverlay}'s two entry points). */
    public void openForEdit(Long targetUserId, Consumer<UserDto> onUpdated, Runnable onClosed) {
        ensureInitialized();
        session = new OverlaySession(Section.NAME, NameMode.EDIT, ProviderProfileMode.VIEW, false, targetUserId, onUpdated, onClosed);
        launchSession(this::switchTo);
    }

    /** Opens directly on the Settings tab -- used by {@code HeaderBar}'s own Settings button
     *  (mirrors the deleted {@code SettingsOverlay}'s own entry point). */
    public void openForSettings(Long targetUserId) {
        ensureInitialized();
        session = new OverlaySession(Section.SETTINGS, NameMode.VIEW, ProviderProfileMode.VIEW, false, targetUserId, _ -> { }, () -> { });
        launchSession(this::switchTo);
    }

    @Override
    protected void closeToList() {
        session.onClosed().run();
        super.closeToList();
    }

    private void switchToNameEdit() {
        session = session.toNameEdit();
        switchTo();
    }

    private void switchToProviderProfileEdit() {
        session = session.toProviderProfileEdit();
        switchTo();
    }

    @Override
    protected void switchTo() {
        currentFormHandler = null;
        List<BreadcrumbStep> breadcrumbSteps = buildBreadcrumbSteps();
        layout.setBreadcrumbLinks(buildBreadcrumbLinks(breadcrumbSteps));

        Tabs tabs = buildTabs();
        OverlayModeHandler handler = switch (session.section()) {
            case NAME -> switch (session.nameMode()) {
                case VIEW -> nameViewHandlerFactory.build(
                        AccountNameViewModeHandler.Parameters.builder()
                                .targetUserId(session.targetUserId())
                                .onEdit(this::switchToNameEdit)
                                .onClose(this::closeToList)
                                .tabBar(tabs)
                                .build());
                case EDIT -> {
                    currentFormHandler = nameFormHandlerFactory.build(
                            AccountNameFormModeHandler.Parameters.builder()
                                    .targetUserId(session.targetUserId())
                                    .onSave(this::handleSave)
                                    .onCancel(this::handleCancel)
                                    .breadcrumbSteps(breadcrumbSteps)
                                    .tabBar(tabs)
                                    .build());
                    yield currentFormHandler;
                }
            };
            case SETTINGS -> {
                currentFormHandler = settingsHandlerFactory.build(
                        SettingsFormModeHandler.Parameters.builder()
                                .userId(session.targetUserId())
                                .onSave(this::handleSave)
                                .onCancel(this::handleCancel)
                                .breadcrumbSteps(breadcrumbSteps)
                                .tabBar(tabs)
                                .build());
                yield currentFormHandler;
            }
            case PROVIDER_PROFILE -> switch (session.providerProfileMode()) {
                case VIEW -> providerProfileViewHandlerFactory.build(
                        ProviderProfileViewModeHandler.Parameters.builder()
                                .targetUserId(session.targetUserId())
                                .onEdit(this::switchToProviderProfileEdit)
                                .onClose(this::closeToList)
                                .tabBar(tabs)
                                .build());
                case EDIT -> {
                    currentFormHandler = providerProfileHandlerFactory.build(
                            ProviderProfileFormOverlayModeHandler.Parameters.builder()
                                    .targetUserId(session.targetUserId())
                                    .onSave(this::handleSave)
                                    .onCancel(this::handleCancel)
                                    .breadcrumbSteps(breadcrumbSteps)
                                    .tabBar(tabs)
                                    .build());
                    yield currentFormHandler;
                }
            };
        };

        handler.activate(layout);
        layout.getBreadcrumbCurrent().setText(i18n().get(sectionLabelKey(session.section())));
    }

    private I18nKey sectionLabelKey(Section section) {
        return switch (section) {
            case NAME -> ACCOUNT_OVERLAY_TAB_NAME;
            case SETTINGS -> ACCOUNT_OVERLAY_TAB_SETTINGS;
            case PROVIDER_PROFILE -> ACCOUNT_OVERLAY_TAB_PROVIDER_PROFILE;
        };
    }

    /**
     * Builds a fresh Tabs bar reflecting the current session's section, rebuilt on every
     * {@link #switchTo()} call rather than kept persistent -- {@code OverlayLayout} has no slot
     * for anything besides breadcrumbs/header-actions/content, so each section handler's own
     * {@code activate()} places this bar at the top of its own content Div (see the
     * {@code tabBar} Parameter each handler takes).
     * <p>
     * Switching tabs with unsaved changes discards them silently rather than prompting -- a
     * deliberate choice: tab switching stays inside the same overlay, not a real navigation-away
     * event, and {@code BeforeUnloadUtil} already guards the one navigation-away moment that
     * genuinely risks losing work. Switching tabs while the Name section is in Edit mode also
     * resets it back to View, same as clicking Cancel there would.
     */
    private Tabs buildTabs() {
        Tab nameTab = new Tab(i18n().get(ACCOUNT_OVERLAY_TAB_NAME));
        Tab settingsTab = new Tab(i18n().get(ACCOUNT_OVERLAY_TAB_SETTINGS));
        List<Tab> tabList = new ArrayList<>(List.of(nameTab, settingsTab));
        Tab providerProfileTab = null;
        if (providerProfileSaveService.isAvailable()) {
            providerProfileTab = new Tab(i18n().get(ACCOUNT_OVERLAY_TAB_PROVIDER_PROFILE));
            tabList.add(providerProfileTab);
        }

        Tabs tabs = new Tabs(tabList.toArray(new Tab[0]));
        tabs.addClassName("account-overlay-tabs");
        tabs.setSelectedTab(switch (session.section()) {
            case NAME -> nameTab;
            case SETTINGS -> settingsTab;
            case PROVIDER_PROFILE -> providerProfileTab;
        });

        tabs.addSelectedChangeListener(e -> {
            Section newSection;
            if (e.getSelectedTab() == nameTab) newSection = Section.NAME;
            else if (e.getSelectedTab() == settingsTab) newSection = Section.SETTINGS;
            else newSection = Section.PROVIDER_PROFILE;
            if (newSection == session.section()) return;
            if (currentFormHandler != null) currentFormHandler.discardChanges();
            session = session.withSection(newSection);
            switchTo();
        });
        return tabs;
    }
}
