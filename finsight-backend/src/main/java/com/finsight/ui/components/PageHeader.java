package com.finsight.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;

/** Page title, optional one-line description and right-aligned actions. */
public class PageHeader extends Div {

    private final Div actions = new Div();
    private final Paragraph subtitle = new Paragraph();

    public PageHeader(String title, String description) {
        setClassName("fs-page-header");
        H1 heading = new H1(title);
        heading.setClassName("fs-page-title");
        subtitle.setClassName("fs-page-subtitle");
        setDescription(description);
        actions.setClassName("fs-page-actions");
        add(new Div(heading, subtitle), actions);
    }

    public PageHeader actions(Component... components) {
        actions.add(components);
        return this;
    }

    public void setDescription(String description) {
        subtitle.setText(description == null ? "" : description);
        subtitle.setVisible(description != null && !description.isBlank());
    }
}
