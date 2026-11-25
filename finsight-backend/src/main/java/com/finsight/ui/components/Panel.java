package com.finsight.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;

/** A bordered section with a title row and a body. */
public class Panel extends Div {

    private final Div body = new Div();
    private final Div headerEnd = new Div();
    private final Paragraph caption = new Paragraph();
    private final Div footer = new Div();

    public Panel(String title) {
        this(title, null);
    }

    public Panel(String title, String captionText) {
        setClassName("fs-panel");
        if (title != null) {
            H2 heading = new H2(title);
            heading.setClassName("fs-panel-title");
            caption.setClassName("fs-panel-caption");
            setCaption(captionText);
            Div header = new Div(new Div(heading, caption), headerEnd);
            header.setClassName("fs-panel-header");
            headerEnd.getStyle().set("display", "flex").set("gap", "8px").set("align-items", "center");
            add(header);
        }
        body.setClassName("fs-panel-body");
        footer.setClassName("fs-panel-footer");
        footer.setVisible(false);
        add(body, footer);
    }

    public Panel content(Component... components) {
        body.add(components);
        return this;
    }

    public Panel flush() {
        body.addClassName("flush");
        return this;
    }

    public Panel headerEnd(Component... components) {
        headerEnd.add(components);
        return this;
    }

    public Panel footer(Component... components) {
        footer.removeAll();
        footer.add(components);
        footer.setVisible(components.length > 0);
        return this;
    }

    public void setCaption(String text) {
        caption.setText(text == null ? "" : text);
        caption.setVisible(text != null && !text.isBlank());
    }

    public Div body() {
        return body;
    }

    public void setContent(Component... components) {
        body.removeAll();
        body.add(components);
    }
}
