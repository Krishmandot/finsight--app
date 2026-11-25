package com.finsight.ui.charts;

import com.finsight.ui.components.Ui;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import java.util.List;

/**
 * Hosts an SVG produced by {@link SvgCharts}, with an optional legend above it and a collapsible data table
 * below it (the accessible, hover-free way to read every value).
 */
public class Chart extends Div {

    public record LegendItem(String cssVar, String label, boolean line) {
        public static LegendItem bar(String cssVar, String label) {
            return new LegendItem(cssVar, label, false);
        }

        public static LegendItem line(String cssVar, String label) {
            return new LegendItem(cssVar, label, true);
        }
    }

    public Chart(String svg, List<LegendItem> legend, Component table) {
        getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "12px");
        if (legend != null && legend.size() > 1) {
            Div legendRow = Ui.div("fs-legend");
            for (LegendItem item : legend) {
                Span entry = new Span();
                com.vaadin.flow.dom.Element swatch = new com.vaadin.flow.dom.Element("i");
                swatch.setAttribute("aria-hidden", "true");
                swatch.getStyle().set("background", "var(" + item.cssVar() + ")");
                if (item.line()) {
                    swatch.getClassList().add("line");
                }
                entry.getElement().appendChild(swatch);
                entry.add(item.label());
                legendRow.add(entry);
            }
            add(legendRow);
        }
        Div host = Ui.div("fs-chart fs-chart-scroll");
        host.getElement().setProperty("innerHTML", svg);
        add(host);
        if (table != null) {
            Details details = new Details("Show data table", table);
            details.addClassName("fs-details");
            add(details);
        }
    }
}
