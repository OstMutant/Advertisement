package org.ost.advertisement.ui.views.components.query.elements.cell;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueryVerticalLayout extends VerticalLayout {
    protected void initLayout(Component... filterFields) {
        addClassName("query-vertical-layout");
        add(filterFields);
    }
}
