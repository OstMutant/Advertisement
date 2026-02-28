package org.ost.advertisement.ui.views.components;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class EmptyStateView extends VerticalLayout
        implements Configurable<EmptyStateView, EmptyStateView.Parameters>, Initialization<EmptyStateView> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull VaadinIcon icon;
        @NonNull String     title;
        @NonNull String     hint;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<EmptyStateView, Parameters> {
        @Getter
        private final ObjectProvider<EmptyStateView> provider;
    }

    @Override
    @PostConstruct
    public EmptyStateView init() {
        addClassName("empty-state");
        setAlignItems(Alignment.CENTER);
        return this;
    }

    @Override
    public EmptyStateView configure(Parameters p) {
        Icon icon = p.getIcon().create();
        icon.addClassName("empty-state-icon");

        Span title = new Span(p.getTitle());
        title.addClassName("empty-state-title");

        Span hint = new Span(p.getHint());
        hint.addClassName("empty-state-hint");

        add(icon, title, hint);
        return this;
    }
}
