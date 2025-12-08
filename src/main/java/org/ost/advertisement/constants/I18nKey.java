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

	// === Advertisement Dialog ===
	ADVERTISEMENT_DIALOG_TITLE_NEW("advertisement.dialog.title.new"),
	ADVERTISEMENT_DIALOG_TITLE_EDIT("advertisement.dialog.title.edit"),
	ADVERTISEMENT_DIALOG_FIELD_TITLE("advertisement.dialog.field.title"),
	ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION("advertisement.dialog.field.description"),
	ADVERTISEMENT_DIALOG_FIELD_CREATED("advertisement.dialog.field.created"),
	ADVERTISEMENT_DIALOG_FIELD_UPDATED("advertisement.dialog.field.updated"),
	ADVERTISEMENT_DIALOG_FIELD_USER("advertisement.dialog.field.user"),
	ADVERTISEMENT_DIALOG_VALIDATION_TITLE_REQUIRED("advertisement.dialog.validation.title.required"),
	ADVERTISEMENT_DIALOG_VALIDATION_TITLE_LENGTH("advertisement.dialog.validation.title.length"),
	ADVERTISEMENT_DIALOG_VALIDATION_DESCRIPTION_REQUIRED("advertisement.dialog.validation.description.required"),
	ADVERTISEMENT_DIALOG_NOTIFICATION_SUCCESS("advertisement.dialog.notification.success"),
	ADVERTISEMENT_DIALOG_NOTIFICATION_VALIDATION_FAILED("advertisement.dialog.notification.validation.failed"),
	ADVERTISEMENT_DIALOG_NOTIFICATION_SAVE_ERROR("advertisement.dialog.notification.save.error"),
	ADVERTISEMENT_DIALOG_BUTTON_SAVE("advertisement.dialog.button.save"),
	ADVERTISEMENT_DIALOG_BUTTON_CANCEL("advertisement.dialog.button.cancel"),

	// === User View ===
	USER_VIEW_HEADER_ID("user.view.header.id"),
	USER_VIEW_HEADER_NAME("user.view.header.name"),
	USER_VIEW_HEADER_EMAIL("user.view.header.email"),
	USER_VIEW_HEADER_ROLE("user.view.header.role"),
	USER_VIEW_HEADER_CREATED("user.view.header.created"),
	USER_VIEW_HEADER_UPDATED("user.view.header.updated"),
	USER_VIEW_HEADER_ACTIONS("user.view.header.actions"),
	USER_VIEW_CONFIRM_DELETE_TEXT("user.view.confirm.delete.text"),
	USER_VIEW_CONFIRM_DELETE_BUTTON("user.view.confirm.delete.button"),
	USER_VIEW_CONFIRM_CANCEL_BUTTON("user.view.confirm.cancel.button"),
	USER_VIEW_NOTIFICATION_DELETED("user.view.notification.deleted"),
	USER_VIEW_NOTIFICATION_DELETE_ERROR("user.view.notification.delete.error"),
	USER_VIEW_NOTIFICATION_VALIDATION_FAILED("user.view.notification.validation.failed"),

	// === Advertisement View ===
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
	USER_FILTER_CREATED_START("user.filter.created.start"),
	USER_FILTER_CREATED_END("user.filter.created.end"),
	USER_FILTER_UPDATED_START("user.filter.updated.start"),
	USER_FILTER_UPDATED_END("user.filter.updated.end"),

	// === Advertisement Sort ===
	ADVERTISEMENT_SORT_TITLE("advertisement.sort.title"),
	ADVERTISEMENT_SORT_CREATED_AT("advertisement.sort.createdAt"),
	ADVERTISEMENT_SORT_UPDATED_AT("advertisement.sort.updatedAt"),
	SORT_DIRECTION_ASC("sort.direction.asc"),
	SORT_DIRECTION_DESC("sort.direction.desc"),

	// === Advertisement Sidebar ===
	ADVERTISEMENT_SIDEBAR_BUTTON_ADD("advertisement.sidebar.button.add"),

	// === Advertisement Filter ===
	ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER("advertisement.filter.title.placeholder"),
	ADVERTISEMENT_FILTER_CREATED_START("advertisement.filter.created.start"),
	ADVERTISEMENT_FILTER_CREATED_END("advertisement.filter.created.end"),
	ADVERTISEMENT_FILTER_UPDATED_START("advertisement.filter.updated.start"),
	ADVERTISEMENT_FILTER_UPDATED_END("advertisement.filter.updated.end"),

	// === Advertisement Card ===
	ADVERTISEMENT_CARD_CREATED("advertisement.card.created"),
	ADVERTISEMENT_CARD_UPDATED("advertisement.card.updated"),
	ADVERTISEMENT_CARD_USER("advertisement.card.user"),
	ADVERTISEMENT_CARD_BUTTON_EDIT("advertisement.card.button.edit"),
	ADVERTISEMENT_CARD_BUTTON_DELETE("advertisement.card.button.delete"),
	// Unified Actions
	ACTIONS_APPLY_TOOLTIP("actions.apply.tooltip"),
	ACTIONS_CLEAR_TOOLTIP("actions.clear.tooltip"),
	// Sort actions
	SORT_TOGGLE_TOOLTIP("sort.toggle.tooltip"),
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
	QUERY_STATUS_SORT_NONE("query.status.sort.none");

	private final String key;

	I18nKey(String key) {
		this.key = key;
	}

	public String key() {
		return key;
	}
}

