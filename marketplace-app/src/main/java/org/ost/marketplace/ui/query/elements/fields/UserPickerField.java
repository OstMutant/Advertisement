package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiIconButton;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.spi.UserPort;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;

import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_FILTER_ACTOR;
import static org.ost.marketplace.services.i18n.I18nKey.TIMELINE_SORT_ACTOR;
import static org.ost.marketplace.services.i18n.I18nKey.USER_PICKER_CLEAR_TOOLTIP;
import static org.ost.marketplace.services.i18n.I18nKey.USER_PICKER_OPEN_TOOLTIP;
import static org.ost.marketplace.services.i18n.I18nKey.USER_PICKER_SEARCH_TOOLTIP;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class UserPickerField extends CustomField<UserDto>
        implements Initialization<UserPickerField> {

    private static final String PLACEHOLDER_CSS = "user-picker-placeholder";

    private final transient UserPort    userPort;
    private final transient I18nService i18nService;
    private final transient UiComponentFactory<UiIconButton> iconButtonFactory;

    private UserDto currentValue;
    private Span    displaySpan;
    private Button  clearButton;

    @PostConstruct
    @Override
    public UserPickerField init() {
        addClassName("user-picker-field");

        displaySpan = new Span(i18nService.get(TIMELINE_FILTER_ACTOR));
        displaySpan.addClassName(PLACEHOLDER_CSS);

        clearButton = iconButtonFactory.build(
                UiIconButton.Parameters.builder().labelKey(USER_PICKER_CLEAR_TOOLTIP).icon(VaadinIcon.CLOSE_SMALL.create()).build());
        clearButton.addClassName("user-picker-clear");
        clearButton.setVisible(false);
        clearButton.addClickListener(e -> clearValue());

        Button openButton = iconButtonFactory.build(
                UiIconButton.Parameters.builder().labelKey(USER_PICKER_OPEN_TOOLTIP).icon(VaadinIcon.SEARCH.create()).build());
        openButton.addClassName("user-picker-open");
        openButton.addClickListener(e -> openDialog());

        HorizontalLayout layout = new HorizontalLayout(displaySpan, clearButton, openButton);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setPadding(false);
        add(layout);

        return this;
    }

    private void clearValue() {
        currentValue = null;
        displaySpan.setText(i18nService.get(TIMELINE_FILTER_ACTOR));
        displaySpan.addClassName(PLACEHOLDER_CSS);
        clearButton.setVisible(false);
        setModelValue(null, true);
    }

    private void selectUser(UserDto user) {
        currentValue = user;
        displaySpan.setText(user.name());
        displaySpan.removeClassName(PLACEHOLDER_CSS);
        clearButton.setVisible(true);
        setModelValue(user, true);
    }

    private void openDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(i18nService.get(TIMELINE_FILTER_ACTOR));
        dialog.setWidth("400px");

        TextField searchField = new TextField();
        searchField.setPlaceholder(i18nService.get(TIMELINE_FILTER_ACTOR));
        searchField.setClearButtonVisible(true);
        searchField.setWidthFull();

        Grid<UserDto> grid = new Grid<>();

        grid.addColumn(UserDto::name).setHeader(i18nService.get(TIMELINE_SORT_ACTOR));
        grid.setWidthFull();
        grid.setHeight("300px");

        CallbackDataProvider<UserDto, String> dataProvider = DataProvider.fromFilteringCallbacks(
                query -> userPort.getFilteredByOffset(
                        UserFilterDto.builder().name(query.getFilter().orElse(null)).build(),
                        query.getOffset(),
                        query.getLimit(),
                        Sort.by(Sort.Order.asc("name"))).stream(),
                query -> userPort.count(
                        UserFilterDto.builder().name(query.getFilter().orElse(null)).build())
        );
        ConfigurableFilterDataProvider<UserDto, Void, String> filterable = dataProvider.withConfigurableFilter();
        grid.setItems(filterable);

        Button searchButton = iconButtonFactory.build(
                UiIconButton.Parameters.builder().labelKey(USER_PICKER_SEARCH_TOOLTIP).icon(VaadinIcon.SEARCH.create()).inline(true).build());
        searchButton.addClickListener(e -> filterable.setFilter(searchField.getValue().isBlank() ? null : searchField.getValue()));
        searchField.setSuffixComponent(searchButton);

        searchField.addValueChangeListener(e ->
                filterable.setFilter(e.getValue().isBlank() ? null : e.getValue()));

        grid.asSingleSelect().addValueChangeListener(e -> {
            if (e.getValue() != null) {
                selectUser(e.getValue());
                dialog.close();
            }
        });

        VerticalLayout content = new VerticalLayout(searchField, grid);
        content.setPadding(false);
        content.setWidthFull();
        dialog.add(content);
        dialog.open();
    }

    @Override
    protected UserDto generateModelValue() {
        return currentValue;
    }

    @Override
    protected void setPresentationValue(UserDto value) {
        currentValue = value;
        if (value == null) {
            displaySpan.setText(i18nService.get(TIMELINE_FILTER_ACTOR));
            displaySpan.addClassName(PLACEHOLDER_CSS);
            clearButton.setVisible(false);
        } else {
            displaySpan.setText(value.name());
            displaySpan.removeClassName(PLACEHOLDER_CSS);
            clearButton.setVisible(true);
        }
    }
}
