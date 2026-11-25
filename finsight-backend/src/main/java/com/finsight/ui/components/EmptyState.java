package com.finsight.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;

/** Friendly explanation plus next step, used wherever a list or chart has nothing to show yet. */
public class EmptyState extends Div {

    private final Div actions = Ui.div("fs-empty-actions");

    public EmptyState(VaadinIcon icon, String title, String text) {
        setClassName("fs-empty");
        add(Ui.div("fs-empty-icon", Ui.icon(icon)), Ui.text("fs-empty-title", title));
        if (text != null) {
            add(Ui.text("fs-empty-text", text));
        }
        actions.setVisible(false);
        add(actions);
    }

    public EmptyState actions(Component... components) {
        actions.add(components);
        actions.setVisible(true);
        return this;
    }
}
