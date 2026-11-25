package com.finsight.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** A single bordered strip of headline figures, divided by hairlines (not a row of separate cards). */
public class KpiStrip extends Div {

    public KpiStrip() {
        setClassName("fs-kpis");
    }

    public KpiStrip three() {
        addClassName("three");
        return this;
    }

    /**
     * @param keyCssVar optional series colour key shown next to the label (ties the figure to a chart series)
     */
    public KpiStrip kpi(String label, String value, String keyCssVar, Component... meta) {
        Div labelRow = Ui.div("fs-kpi-label");
        if (keyCssVar != null) {
            Span key = new Span();
            key.setClassName("fs-kpi-key");
            key.getStyle().set("background", "var(" + keyCssVar + ")");
            key.getElement().setAttribute("aria-hidden", "true");
            labelRow.add(key);
        }
        labelRow.add(new Span(label));
        Div valueDiv = Ui.text("fs-kpi-value", value);
        valueDiv.getElement().setAttribute("title", value);
        Div metaRow = Ui.div("fs-kpi-meta", meta);
        add(Ui.div("fs-kpi", labelRow, valueDiv, metaRow));
        return this;
    }

    /** "+12%" style delta. {@code higherIsGood} decides the colour; null percent renders nothing. */
    public static Span delta(Double percent, boolean higherIsGood) {
        if (percent == null) {
            return new Span();
        }
        long rounded = Math.round(percent);
        String cls = rounded == 0 ? "flat" : (rounded > 0) == higherIsGood ? "good" : "bad";
        String arrow = rounded > 0 ? "↑ " : rounded < 0 ? "↓ " : "";
        return Ui.span("fs-delta " + cls, arrow + Math.abs(rounded) + "%");
    }
}
