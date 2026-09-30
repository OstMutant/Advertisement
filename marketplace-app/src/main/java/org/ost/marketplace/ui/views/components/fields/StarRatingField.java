package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

public class StarRatingField extends Div {

    private static final int STAR_COUNT = 5;

    private Integer value;

    public StarRatingField() {
        addClassName("star-rating-field");
        render();
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
        render();
    }

    private void render() {
        removeAll();
        for (int n = 1; n <= STAR_COUNT; n++) {
            Icon star = (value != null && n <= value) ? VaadinIcon.STAR.create() : VaadinIcon.STAR_O.create();
            star.addClassName("star-rating-field-star");
            star.getElement().setAttribute("data-rating", String.valueOf(n));
            int rating = n;
            star.addClickListener(_ -> setValue(rating));
            add(star);
        }
    }
}
