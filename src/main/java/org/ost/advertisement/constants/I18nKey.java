package org.ost.advertisement.constants;

public enum I18nKey {
    // === Header ===
    HEADER_SIGNED_IN("header.signedIn"),
    HEADER_NOT_SIGNED_IN("header.notSignedIn"),
    HEADER_LOGIN("header.login"),
    HEADER_LOGOUT("header.logout"),
    HEADER_SIGNUP("header.signup"),

    // === Logout Confirm ===
    LOGOUT_CONFIRM_TEXT("logout.confirm.text"),
    LOGOUT_CONFIRM_YES("logout.confirm.yes"),
    LOGOUT_CONFIRM_CANCEL("logout.confirm.cancel"),

    // === Login ===
    LOGIN_HEADER_TITLE("login.header.title"),
    LOGIN_EMAIL_LABEL("login.email.label"),
    LOGIN_PASSWORD_LABEL("login.password.label"),
    LOGIN_BUTTON_SUBMIT("login.button.submit"),
    LOGIN_BUTTON_CANCEL("login.button.cancel"),
    LOGIN_SUCCESS("login.success"),
    LOGIN_ERROR("login.error"),
    LOGIN_WELCOME("login.welcome"),

    // === Signup ===
    SIGNUP_HEADER_TITLE("signup.header.title"),
    SIGNUP_NAME_LABEL("signup.name.label"),
    SIGNUP_EMAIL_LABEL("signup.email.label"),
    SIGNUP_PASSWORD_LABEL("signup.password.label"),
    SIGNUP_BUTTON_SUBMIT("signup.button.submit"),
    SIGNUP_BUTTON_CANCEL("signup.button.cancel"),
    SIGNUP_SUCCESS("signup.success"),
    SIGNUP_ERROR_NAME_REQUIRED("signup.error.name.required"),
    SIGNUP_ERROR_EMAIL_INVALID("signup.error.email.invalid"),
    SIGNUP_ERROR_EMAIL_EXISTS("signup.error.email.exists"),
    SIGNUP_ERROR_PASSWORD_SHORT("signup.error.password.short"),

    // === User Dialog ===
    USER_DIALOG_TITLE("user.dialog.title"),
    USER_DIALOG_FIELD_ID_LABEL("user.dialog.field.id.label"),
    USER_DIALOG_FIELD_EMAIL_LABEL("user.dialog.field.email.label"),
    USER_DIALOG_FIELD_CREATED_LABEL("user.dialog.field.created.label"),
    USER_DIALOG_FIELD_UPDATED_LABEL("user.dialog.field.updated.label"),
    USER_DIALOG_FIELD_NAME_LABEL("user.dialog.field.name.label"),
    USER_DIALOG_FIELD_NAME_PLACEHOLDER("user.dialog.field.name.placeholder"),
    USER_DIALOG_FIELD_ROLE_LABEL("user.dialog.field.role.label"),
    USER_DIALOG_VALIDATION_NAME_REQUIRED("user.dialog.validation.name.required"),
    USER_DIALOG_VALIDATION_NAME_LENGTH("user.dialog.validation.name.length"),
    USER_DIALOG_VALIDATION_ROLE_REQUIRED("user.dialog.validation.role.required"),
    USER_DIALOG_NOTIFICATION_SUCCESS("user.dialog.notification.success"),
    USER_DIALOG_NOTIFICATION_VALIDATION_FAILED("user.dialog.notification.validation.failed"),
    USER_DIALOG_NOTIFICATION_SAVE_ERROR("user.dialog.notification.save.error"),
    USER_DIALOG_BUTTON_SAVE("user.dialog.button.save"),
    USER_DIALOG_BUTTON_CANCEL("user.dialog.button.cancel"),

    // === Advertisement Overlay ===
    ADVERTISEMENT_OVERLAY_TITLE_NEW("advertisement.overlay.title.new"),
    ADVERTISEMENT_OVERLAY_TITLE_EDIT("advertisement.overlay.title.edit"),
    ADVERTISEMENT_OVERLAY_FIELD_CREATED("advertisement.overlay.field.created"),
    ADVERTISEMENT_OVERLAY_FIELD_UPDATED("advertisement.overlay.field.updated"),
    ADVERTISEMENT_OVERLAY_FIELD_AUTHOR("advertisement.overlay.field.author"),  // #1: was USER, now shows name
    ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_REQUIRED("advertisement.overlay.validation.title.required"),
    ADVERTISEMENT_OVERLAY_VALIDATION_TITLE_LENGTH("advertisement.overlay.validation.title.length"),
    ADVERTISEMENT_OVERLAY_VALIDATION_DESCRIPTION_REQUIRED("advertisement.overlay.validation.description.required"),
    ADVERTISEMENT_OVERLAY_NOTIFICATION_SUCCESS("advertisement.overlay.notification.success"),
    ADVERTISEMENT_OVERLAY_NOTIFICATION_VALIDATION_FAILED("advertisement.overlay.notification.validation.failed"),
    ADVERTISEMENT_OVERLAY_NOTIFICATION_SAVE_ERROR("advertisement.overlay.notification.save.error"),
    ADVERTISEMENT_OVERLAY_FIELD_TITLE("advertisement.overlay.field.title"),
    ADVERTISEMENT_OVERLAY_FIELD_DESCRIPTION("advertisement.overlay.field.description"),
    ADVERTISEMENT_OVERLAY_BUTTON_SAVE("advertisement.overlay.button.save"),
    ADVERTISEMENT_OVERLAY_BUTTON_CANCEL("advertisement.overlay.button.cancel"),
    ADVERTISEMENT_DESCRIPTION_OVERLAY_AUTHOR("advertisement.description.overlay.author"),
    ADVERTISEMENT_DESCRIPTION_OVERLAY_CREATED("advertisement.description.overlay.created"),
    ADVERTISEMENT_DESCRIPTION_OVERLAY_UPDATED("advertisement.description.overlay.updated"),

    // === User View ===
    USER_VIEW_HEADER_ID("user.view.header.id"),
    USER_VIEW_HEADER_NAME("user.view.header.name"),
    USER_VIEW_HEADER_EMAIL("user.view.header.email"),
    USER_VIEW_HEADER_ROLE("user.view.header.role"),
    USER_VIEW_HEADER_CREATED("user.view.header.created"),
    USER_VIEW_HEADER_UPDATED("user.view.header.updated"),
    USER_VIEW_HEADER_ACTIONS("user.view.header.actions"),
    USER_VIEW_CONFIRM_DELETE_TITLE("user.view.confirm.delete.title"),
    USER_VIEW_CONFIRM_DELETE_TEXT("user.view.confirm.delete.text"),
    USER_VIEW_BUTTON_EDIT("user.view.button.edit"),
    USER_VIEW_BUTTON_DELETE("user.view.button.delete"),
    USER_VIEW_CONFIRM_DELETE_BUTTON("user.view.confirm.delete.button"),
    USER_VIEW_CONFIRM_CANCEL_BUTTON("user.view.confirm.cancel.button"),
    USER_VIEW_NOTIFICATION_DELETED("user.view.notification.deleted"),
    USER_VIEW_NOTIFICATION_DELETE_ERROR("user.view.notification.delete.error"),
    USER_VIEW_NOTIFICATION_VALIDATION_FAILED("user.view.notification.validation.failed"),

    // UserViewDialog
    USER_VIEW_DIALOG_TITLE("user.view.dialog.title"),
    USER_VIEW_DIALOG_FIELD_ID("user.view.dialog.field.id"),
    USER_VIEW_DIALOG_FIELD_NAME("user.view.dialog.field.name"),
    USER_VIEW_DIALOG_FIELD_EMAIL("user.view.dialog.field.email"),
    USER_VIEW_DIALOG_FIELD_ROLE("user.view.dialog.field.role"),
    USER_VIEW_DIALOG_FIELD_CREATED("user.view.dialog.field.created"),
    USER_VIEW_DIALOG_FIELD_UPDATED("user.view.dialog.field.updated"),
    USER_VIEW_DIALOG_CLOSE("user.view.dialog.close"),

    // === Advertisement Sort ===
    USER_SORT_ID("user.sort.id"),
    USER_SORT_NAME("user.sort.name"),
    USER_SORT_EMAIL("user.sort.email"),
    USER_SORT_ROLE("user.sort.role"),
    USER_SORT_CREATED("user.sort.createdAt"),
    USER_SORT_UPDATED("user.sort.updatedAt"),

    // === Advertisement View ===
    ADVERTISEMENT_VIEW_CONFIRM_DELETE_TITLE("advertisement.view.confirm.delete.title"),
    ADVERTISEMENT_VIEW_CONFIRM_DELETE_TEXT("advertisement.view.confirm.delete.text"),
    ADVERTISEMENT_VIEW_CONFIRM_DELETE_BUTTON("advertisement.view.confirm.delete.button"),
    ADVERTISEMENT_VIEW_CONFIRM_CANCEL_BUTTON("advertisement.view.confirm.cancel.button"),
    ADVERTISEMENT_VIEW_NOTIFICATION_DELETED("advertisement.view.notification.deleted"),
    ADVERTISEMENT_VIEW_NOTIFICATION_DELETE_ERROR("advertisement.view.notification.delete.error"),

    // === User Filter ===
    USER_FILTER_ID_MIN("user.filter.id.min"),
    USER_FILTER_ID_MAX("user.filter.id.max"),
    USER_FILTER_NAME_PLACEHOLDER("user.filter.name.placeholder"),
    USER_FILTER_EMAIL_PLACEHOLDER("user.filter.email.placeholder"),
    USER_FILTER_ROLE_ANY("user.filter.role.any"),
    USER_FILTER_DATE_CREATED_START("user.filter.date.created.start"),
    USER_FILTER_DATE_CREATED_END("user.filter.date.created.end"),
    USER_FILTER_DATE_UPDATED_START("user.filter.date.updated.start"),
    USER_FILTER_DATE_UPDATED_END("user.filter.date.updated.end"),
    USER_FILTER_TIME_CREATED_START("user.filter.time.created.start"),
    USER_FILTER_TIME_CREATED_END("user.filter.time.created.end"),
    USER_FILTER_TIME_UPDATED_START("user.filter.time.updated.start"),
    USER_FILTER_TIME_UPDATED_END("user.filter.time.updated.end"),

    // === Advertisement Sort ===
    ADVERTISEMENT_SORT_TITLE("advertisement.sort.title"),
    ADVERTISEMENT_SORT_CREATED_AT("advertisement.sort.createdAt"),
    ADVERTISEMENT_SORT_UPDATED_AT("advertisement.sort.updatedAt"),

    // === Advertisement Sidebar ===
    ADVERTISEMENT_SIDEBAR_BUTTON_ADD("advertisement.sidebar.button.add"),

    // === Advertisement Filter ===
    ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER("advertisement.filter.title.placeholder"),
    ADVERTISEMENT_FILTER_DATE_CREATED_START("advertisement.filter.date.created.start"),
    ADVERTISEMENT_FILTER_DATE_CREATED_END("advertisement.filter.date.created.end"),
    ADVERTISEMENT_FILTER_DATE_UPDATED_START("advertisement.filter.date.updated.start"),
    ADVERTISEMENT_FILTER_DATE_UPDATED_END("advertisement.filter.date.updated.end"),
    ADVERTISEMENT_FILTER_TIME_CREATED_START("advertisement.filter.time.created.start"),
    ADVERTISEMENT_FILTER_TIME_CREATED_END("advertisement.filter.time.created.end"),
    ADVERTISEMENT_FILTER_TIME_UPDATED_START("advertisement.filter.time.updated.start"),
    ADVERTISEMENT_FILTER_TIME_UPDATED_END("advertisement.filter.time.updated.end"),

    // === Advertisement Card ===
    ADVERTISEMENT_CARD_CREATED("advertisement.card.created"),
    ADVERTISEMENT_CARD_UPDATED("advertisement.card.updated"),
    ADVERTISEMENT_CARD_AUTHOR("advertisement.card.author"),
    ADVERTISEMENT_CARD_BUTTON_EDIT("advertisement.card.button.edit"),
    ADVERTISEMENT_CARD_BUTTON_DELETE("advertisement.card.button.delete"),

    // === Advertisement Description Dialog ===
    ADVERTISEMENT_DESCRIPTION_DIALOG_CLOSE("advertisement.description.dialog.close"),

    // === Advertisement Empty State ===
    ADVERTISEMENT_EMPTY_TITLE("advertisement.empty.title"),
    ADVERTISEMENT_EMPTY_HINT("advertisement.empty.hint"),

    // Unified Actions
    ACTIONS_APPLY_TOOLTIP("actions.apply.tooltip"),
    ACTIONS_CLEAR_TOOLTIP("actions.clear.tooltip"),
    // === Main Tabs ===
    MAIN_TAB_ADVERTISEMENTS("main.tab.advertisements"),
    MAIN_TAB_USERS("main.tab.users"),
    // === Pagination ===
    PAGINATION_FIRST("pagination.first"),
    PAGINATION_PREV("pagination.prev"),
    PAGINATION_NEXT("pagination.next"),
    PAGINATION_LAST("pagination.last"),
    PAGINATION_INDICATOR("pagination.indicator"),

    QUERY_STATUS_FILTERS_PREFIX("query.status.filters.prefix"),
    QUERY_STATUS_FILTERS_NONE("query.status.filters.none"),
    QUERY_STATUS_SORT_PREFIX("query.status.sort.prefix"),
    QUERY_STATUS_SORT_NONE("query.status.sort.none"),

    // Sort actions
    SORT_DIRECTION_ASC("sort.direction.asc"),
    SORT_DIRECTION_DESC("sort.direction.desc"),
    SORT_ICON_TOOLTIP("sort.icon.tooltip"),
    SORT_ICON_ASC("sort.icon.asc"),
    SORT_ICON_DESC("sort.icon.desc"),
    SORT_ICON_NEUTRAL("sort.icon.neutral"),

    LOCALE_ENGLISH("locale.english"),
    LOCALE_UKRAINIAN("locale.ukrainian"),

    // === Overlay Dirty Guard ===
    OVERLAY_UNSAVED_TITLE("overlay.unsaved.title"),
    OVERLAY_UNSAVED_TEXT("overlay.unsaved.text"),
    OVERLAY_UNSAVED_CONFIRM("overlay.unsaved.confirm"),
    OVERLAY_UNSAVED_CANCEL("overlay.unsaved.cancel");

    private final String key;

    I18nKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}