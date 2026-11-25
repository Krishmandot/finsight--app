package com.finsight.ui.components;

import com.finsight.service.analytics.CategoryAmount;
import com.finsight.service.support.MoneyFormatter;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ranked horizontal bars for a category breakdown. Each row is directly labelled with its amount and
 * share, so no legend or hover is needed. Optionally clickable (drill into transactions).
 */
public class CategoryBars extends Div {

    public CategoryBars(List<CategoryAmount> items, String currency, int limit, boolean income,
                        Consumer<CategoryAmount> onClick) {
        setClassName("fs-bars");
        if (items.isEmpty()) {
            return;
        }
        BigDecimal max = items.get(0).amount();
        List<CategoryAmount> shown = items.size() > limit ? items.subList(0, limit) : items;
        for (CategoryAmount item : shown) {
            Div row = Ui.div("fs-bar-row");
            Span value = Ui.span("fs-bar-value", MoneyFormatter.formatWhole(item.amount(), currency));
            value.add(Ui.span("fs-faint", Math.round(item.share()) + "%"));
            Div fill = Ui.div("fs-bar-fill" + (income ? " income" : ""));
            double width = max.signum() == 0 ? 0 : item.amount().doubleValue() / max.doubleValue() * 100;
            fill.getStyle().set("width", String.format(java.util.Locale.ROOT, "%.2f%%", width));
            Div track = Ui.div("fs-bar-track", fill);
            track.getElement().setAttribute("aria-hidden", "true");
            row.add(Ui.text("fs-bar-name", item.name()), value, track);
            if (onClick != null) {
                row.addClassName("clickable");
                row.getElement().setAttribute("role", "link");
                row.getElement().setAttribute("tabindex", "0");
                row.addClickListener(event -> onClick.accept(item));
                row.getElement().addEventListener("keydown", event -> onClick.accept(item))
                    .setFilter("event.key === 'Enter'");
            }
            add(row);
        }
        if (items.size() > limit) {
            BigDecimal rest = items.subList(limit, items.size()).stream().map(CategoryAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            add(Ui.text("fs-faint", (items.size() - limit) + " more categories · "
                + MoneyFormatter.formatWhole(rest, currency)));
        }
    }
}
