package org.ost.advertisement.ui.views.components.query.action;

import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.shared.Registration;
import org.ost.advertisement.ui.views.components.query.action.elements.QueryActionApplyButton;
import org.ost.advertisement.ui.views.components.query.action.elements.QueryActionClearButton;
import org.springframework.context.annotation.Scope;

import java.util.Objects;

import static org.ost.advertisement.ui.utils.HighlighterUtil.setDirtyOrClean;

@org.springframework.stereotype.Component
@Scope("prototype")
public class QueryActionBlock extends HorizontalLayout implements QueryActionBlockHandler {

    private final QueryActionApplyButton applyButton;
    private final QueryActionClearButton clearButton;
    private Registration applyButtonListener;
    private Registration clearButtonListener;

    public QueryActionBlock(QueryActionApplyButton applyButton, QueryActionClearButton clearButton) {
        this.applyButton = applyButton;
        this.clearButton = clearButton;
        initLayout();
    }

    public void addEventListener(Runnable onApply, Runnable onClear) {
        if (Objects.nonNull(onApply)) {
            if (Objects.nonNull(applyButtonListener)) {
                applyButtonListener.remove();
            }
            applyButtonListener = applyButton.addClickListener(e -> onApply.run());
        }
        if (Objects.nonNull(onClear)) {
            if (Objects.nonNull(clearButtonListener)) {
                clearButtonListener.remove();
            }
            clearButtonListener = clearButton.addClickListener(e -> onClear.run());
        }
    }

    @Override
    public void updateDirtyState(boolean dirty) {
        setDirtyOrClean(applyButton, dirty);
    }

    private void initLayout() {
        add(applyButton, clearButton);
        setSpacing(false);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
    }
}

