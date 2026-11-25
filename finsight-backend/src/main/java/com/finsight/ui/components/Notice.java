package com.finsight.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;

/** Inline message. Kinds: info (default), warning, error, success, neutral. */
public class Notice extends Div {

    private final Div body = Ui.div("fs-notice-body");

    public Notice(String kind, String text) {
        setClassName("fs-notice" + (kind == null || kind.equals("info") ? "" : " " + kind));
        VaadinIcon icon = switch (kind == null ? "info" : kind) {
            case "warning" -> VaadinIcon.WARNING;
            case "error" -> VaadinIcon.EXCLAMATION_CIRCLE_O;
            case "success" -> VaadinIcon.CHECK_CIRCLE_O;
            default -> VaadinIcon.INFO_CIRCLE_O;
        };
        body.setText(text);
        add(Ui.icon(icon), body);
        getElement().setAttribute("role", kind != null && (kind.equals("error") || kind.equals("warning")) ? "alert" : "status");
    }

    public Notice append(Component... components) {
        body.add(components);
        return this;
    }

    public void setMessage(String text) {
        body.setText(text);
    }
}
