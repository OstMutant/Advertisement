package org.ost.marketplace.ui.views.utils;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import lombok.NoArgsConstructor;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

/** Builds a card's category/city line: an already-resolved label span (also used as the row aria-label) followed by one chip span per name. */
@NoArgsConstructor(access = PRIVATE)
public final class ChipRowUtil {

    public static Div labelled(String label, List<String> names, String rowCssClass, String chipCssClass) {
        Div row = new Div();
        row.addClassName(rowCssClass);
        row.getElement().setAttribute("role", "list");
        row.getElement().setAttribute("aria-label", label);
        Span labelSpan = new Span(label);
        labelSpan.addClassName("overlay-chips-label");
        row.add(labelSpan);
        names.forEach(name -> {
            Span chip = new Span(name);
            chip.addClassName(chipCssClass);
            chip.getElement().setAttribute("role", "listitem");
            row.add(chip);
        });
        return row;
    }
}
