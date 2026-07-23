package org.ost.marketplace.ui.query.elements.action;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.shared.Registration;
import org.ost.marketplace.services.i18n.I18nService;

import java.util.Objects;

import static org.ost.marketplace.services.i18n.I18nKey.ACTIONS_APPLY_TOOLTIP;
import static org.ost.marketplace.services.i18n.I18nKey.ACTIONS_CLEAR_TOOLTIP;
import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDirtyOrClean;

public class QueryActionBlock extends HorizontalLayout implements QueryActionBlockHandler {

    private final QueryActionButton applyButton;
    private final QueryActionButton clearButton;

    private Registration applyButtonListener;
    private Registration clearButtonListener;

    public QueryActionBlock(I18nService i18nService) {
        applyButton = new QueryActionButton("apply.svg", i18nService.get(ACTIONS_APPLY_TOOLTIP), ButtonVariant.LUMO_PRIMARY);
        clearButton = new QueryActionButton("clear.svg", i18nService.get(ACTIONS_CLEAR_TOOLTIP), ButtonVariant.LUMO_TERTIARY);

        add(applyButton, clearButton);
        addClassName("query-action-block");
        setSpacing(false);
        setJustifyContentMode(FlexComponent.JustifyContentMode.START);
    }

    public void addEventListener(Runnable onApply, Runnable onClear) {
        if (Objects.nonNull(onApply)) {
            if (Objects.nonNull(applyButtonListener)) applyButtonListener.remove();
            applyButtonListener = applyButton.addClickListener(e -> onApply.run());
        }
        if (Objects.nonNull(onClear)) {
            if (Objects.nonNull(clearButtonListener)) clearButtonListener.remove();
            clearButtonListener = clearButton.addClickListener(e -> onClear.run());
        }
    }

    @Override
    public void updateDirtyState(boolean dirty) {
        setDirtyOrClean(applyButton, dirty);
    }
}
