package com.finsight.ui.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** A compact single-choice toggle (e.g. Weekly / Fortnightly / Monthly). Keyboard accessible. */
public class SegmentedControl<T> extends Div {

    private final Map<T, NativeButton> buttons = new LinkedHashMap<>();
    private final List<Consumer<T>> listeners = new ArrayList<>();
    private T value;

    public SegmentedControl(String ariaLabel, List<T> items, Function<T, String> label) {
        setClassName("fs-segmented");
        getElement().setAttribute("role", "group");
        getElement().setAttribute("aria-label", ariaLabel);
        for (T item : items) {
            NativeButton button = new NativeButton(label.apply(item));
            button.getElement().setAttribute("type", "button");
            button.getElement().setAttribute("aria-pressed", "false");
            button.addClickListener(event -> select(item, true));
            buttons.put(item, button);
            add(button);
        }
    }

    public T getValue() {
        return value;
    }

    public void setValue(T item) {
        select(item, false);
    }

    public void addValueChangeListener(Consumer<T> listener) {
        listeners.add(listener);
    }

    private void select(T item, boolean fromClient) {
        boolean changed = !Objects.equals(item, value);
        value = item;
        buttons.forEach((key, button) ->
            button.getElement().setAttribute("aria-pressed", String.valueOf(Objects.equals(key, item))));
        if (changed && fromClient) {
            listeners.forEach(listener -> listener.accept(item));
        }
    }
}
