package org.ost.marketplace.ui.views.services;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.History;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Fans a single {@link History} state-change slot out to every overlay that registers a listener, since Vaadin's own setter accepts only one handler per {@code UI} and would otherwise let each new overlay silently replace the previous one's. */
@SpringComponent
@UIScope
public class OverlayNavigationRegistry {

    private final List<History.HistoryStateChangeHandler> listeners = new ArrayList<>();

    @PostConstruct
    private void init() {
        UI.getCurrent().getPage().getHistory().setHistoryStateChangeHandler(this::dispatch);
    }

    public void register(@NonNull History.HistoryStateChangeHandler listener) {
        listeners.add(listener);
    }

    private void dispatch(History.HistoryStateChangeEvent event) {
        listeners.forEach(listener -> listener.onHistoryStateChange(event));
    }
}
