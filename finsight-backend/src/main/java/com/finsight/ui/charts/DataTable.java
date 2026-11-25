package com.finsight.ui.charts;

import com.vaadin.flow.component.Html;
import java.util.List;

/** A plain HTML table for chart data. All cell text is escaped. */
public final class DataTable {

    private DataTable() {
    }

    /**
     * @param numeric which columns are right-aligned numbers
     */
    public static Html of(List<String> headers, boolean[] numeric, List<List<String>> rows) {
        StringBuilder html = new StringBuilder("<div style=\"overflow-x:auto\"><table class=\"fs-data-table\"><thead><tr>");
        for (int i = 0; i < headers.size(); i++) {
            html.append("<th").append(numeric[i] ? " class=\"num\"" : "").append(" scope=\"col\">")
                .append(SvgCharts.xml(headers.get(i))).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (List<String> row : rows) {
            html.append("<tr>");
            for (int i = 0; i < row.size(); i++) {
                html.append("<td").append(numeric[i] ? " class=\"num\"" : "").append(">")
                    .append(SvgCharts.xml(row.get(i))).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");
        return new Html(html.toString());
    }
}
