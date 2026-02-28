package org.ost.advertisement.ui.views.components.query.elements.action;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;

import java.util.Objects;

import static org.ost.advertisement.constants.I18nKey.ACTIONS_APPLY_TOOLTIP;
import static org.ost.advertisement.constants.I18nKey.ACTIONS_CLEAR_TOOLTIP;
import static org.ost.advertisement.ui.utils.HighlighterUtil.setDirtyOrClean;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class QueryActionBlock extends HorizontalLayout implements QueryActionBlockHandler {

    private final QueryActionButton applyButton;
    private final QueryActionButton clearButton;

    private Registration      applyButtonListener;
    private Registration      clearButtonListener;

    @PostConstruct
    private void initLayout() {
        applyButton.configure(QueryActionButton.Parameters.builder()
                .svgPath("apply.svg")
                .tooltipKey(ACTIONS_APPLY_TOOLTIP)
                .variant(ButtonVariant.LUMO_PRIMARY)
                .build());

        clearButton.configure(QueryActionButton.Parameters.builder()
                .svgPath("clear.svg")
                .tooltipKey(ACTIONS_CLEAR_TOOLTIP)
                .variant(ButtonVariant.LUMO_TERTIARY)
                .build());

        add(applyButton, clearButton);
        setSpacing(false);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
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