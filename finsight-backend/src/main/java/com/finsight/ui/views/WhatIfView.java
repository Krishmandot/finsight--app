package com.finsight.ui.views;

import com.finsight.service.intelligence.IntelligenceService;
import com.finsight.service.intelligence.WhatIfSimulator;
import com.finsight.ui.charts.Chart;
import com.finsight.ui.charts.DataTable;
import com.finsight.ui.components.EmptyState;
import com.finsight.ui.components.KpiStrip;
import com.finsight.ui.components.Notice;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.RangeSlider;
import com.finsight.ui.components.SegmentedControl;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What-If Simulator: sliders cut spending categories and the projected savings are recalculated by
 * {@link WhatIfSimulator} (Java) on every movement.
 */
@Route(value = "what-if", layout = MainLayout.class)
@PageTitle("What-If Simulator | FinSight")
@PermitAll
public class WhatIfView extends AppPage {

    private final String currency;
    private final SegmentedControl<Integer> horizon =
        new SegmentedControl<>("Projection period", List.of(3, 6, 12, 24), months -> months + " months");
    private final Map<String, Long> reductions = new LinkedHashMap<>();
    private final Map<String, RangeSlider> sliders = new LinkedHashMap<>();
    private final Map<String, Span> sliderLabels = new LinkedHashMap<>();
    private final Div resultHost = new Div();
    private final WhatIfSimulator.Baseline baseline;

    public WhatIfView(IntelligenceService intelligence, CurrentUser currentUser) {
        this.currency = currentUser.currency();
        add(new PageHeader("What-If Simulator", "See how a change in spending changes your savings"));
        this.baseline = intelligence.whatIfBaseline(currentUser.id(), currency, LocalDate.now());

        if (!baseline.hasData() || baseline.categories().isEmpty()) {
            add(new Panel(null).content(new EmptyState(VaadinIcon.LIGHTBULB, "Nothing to simulate yet",
                "The simulator uses your approved, categorized spending. Import transactions, categorize and approve "
                    + "them, then come back.")));
            return;
        }
        add(new Notice("info", "Based on your average month over the last " + baseline.windowDays()
            + " days of approved transactions in " + currency + "."));
        add(new KpiStrip().three()
            .kpi("Income / month", Fmt.moneyWhole(baseline.monthlyIncome(), currency), "--fs-series-income")
            .kpi("Spending / month", Fmt.moneyWhole(baseline.monthlySpending(), currency), "--fs-series-spend")
            .kpi("Savings / month", DashboardView.signedWhole(baseline.monthlySavings(), currency), null));

        horizon.setValue(6);
        horizon.addValueChangeListener(value -> recalculate());
        Button reset = new Button("Reset", event -> resetAll());
        reset.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div list = Ui.div("fs-slider-list");
        for (WhatIfSimulator.CategorySpend category : baseline.categories()) {
            long average = category.monthlyAverage().longValue();
            long step = stepFor(average);
            long max = average / step * step;
            if (max <= 0) {
                continue;
            }
            String name = category.name();
            Span label = Ui.span("fs-slider-value", "No change");
            RangeSlider slider = new RangeSlider("Reduce " + name + " per month", 0, max, step, 0,
                amount -> onSlide(name, amount));
            sliders.put(name, slider);
            sliderLabels.put(name, label);
            list.add(Ui.div("fs-slider-row",
                Ui.div("fs-slider-head", Ui.text("fs-bar-name", name),
                    Ui.span("fs-faint", "Average " + Fmt.moneyWhole(category.monthlyAverage(), currency) + " / month")),
                slider, label));
        }
        add(new Panel("Cut spending", "Drag a slider to reduce a category by that amount every month")
            .headerEnd(reset).content(Ui.div("fs-toolbar", horizon), list));
        add(resultHost);
        recalculate();
    }

    private void onSlide(String category, long amount) {
        if (amount <= 0) {
            reductions.remove(category);
        } else {
            reductions.put(category, amount);
        }
        updateLabel(category, amount);
        recalculate();
    }

    private void updateLabel(String category, long amount) {
        Span label = sliderLabels.get(category);
        if (label != null) {
            label.setText(amount <= 0 ? "No change"
                : "Reduce by " + Fmt.moneyWhole(BigDecimal.valueOf(amount), currency) + " / month");
        }
    }

    private void resetAll() {
        reductions.clear();
        sliders.forEach((name, slider) -> {
            slider.setValue(0);
            updateLabel(name, 0);
        });
        recalculate();
    }

    private void recalculate() {
        Map<String, BigDecimal> cuts = new LinkedHashMap<>();
        reductions.forEach((name, amount) -> cuts.put(name, BigDecimal.valueOf(amount)));
        WhatIfSimulator.Result result = WhatIfSimulator.simulate(baseline, cuts, horizon.getValue(), null, currency);
        resultHost.removeAll();
        resultHost.add(resultPanel(result));
    }

    private Panel resultPanel(WhatIfSimulator.Result result) {
        Panel panel = new Panel("Projected result", "Recalculated as you move the sliders");
        Div summary = Ui.text("fs-whatif-summary", result.summary());
        String rateAfter = result.savingsRateAfter() == null ? "—"
            : result.savingsRateAfter().setScale(0, RoundingMode.HALF_UP) + "%";
        String rateBefore = result.savingsRateBefore() == null ? ""
            : "was " + result.savingsRateBefore().setScale(0, RoundingMode.HALF_UP) + "%";
        KpiStrip kpis = new KpiStrip().three()
            .kpi("Extra saved", Fmt.moneyWhole(result.extraSaved(), currency), "--fs-series-income",
                Ui.span("", "over " + result.months() + " months"))
            .kpi("Savings / month", DashboardView.signedWhole(result.newMonthlySavings(), currency), null,
                Ui.span("", "was " + DashboardView.signedWhole(baseline.monthlySavings(), currency)))
            .kpi("Savings rate", rateAfter, null, Ui.span("", rateBefore));
        panel.content(summary, kpis);

        if (result.monthlyReduction().signum() > 0) {
            List<List<String>> rows = result.points().stream()
                .map(p -> List.of("Month " + p.month(), Fmt.moneyWhole(p.extraSaved(), currency))).toList();
            panel.content(new Chart(savingsChart(result), null,
                DataTable.of(List.of("Month", "Extra saved so far"), new boolean[]{false, true}, rows)));
        }
        return panel;
    }

    /** Bars of cumulative extra savings, one per month. */
    private String savingsChart(WhatIfSimulator.Result result) {
        List<WhatIfSimulator.Point> points = result.points();
        int width = 640;
        int height = 220;
        int left = 8;
        int right = 8;
        int top = 22;
        int bottom = 26;
        double max = Math.max(1, points.get(points.size() - 1).extraSaved().doubleValue());
        double slot = (double) (width - left - right) / points.size();
        double barWidth = Math.max(2, slot * 0.7);
        int labelEvery = points.size() > 12 ? 3 : 1;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(width).append(' ').append(height)
            .append("\" role=\"img\" aria-label=\"Cumulative extra savings over ").append(result.months())
            .append(" months\">");
        for (int i = 0; i < points.size(); i++) {
            WhatIfSimulator.Point point = points.get(i);
            double barHeight = point.extraSaved().doubleValue() / max * (height - top - bottom);
            double x = left + i * slot + (slot - barWidth) / 2;
            double y = height - bottom - barHeight;
            svg.append(String.format(Locale.ROOT,
                "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"3\" fill=\"var(--fs-series-income)\"/>",
                x, y, barWidth, Math.max(1, barHeight)));
            if ((i + 1) % labelEvery == 0 || i == points.size() - 1) {
                svg.append(String.format(Locale.ROOT,
                    "<text x=\"%.1f\" y=\"%d\" text-anchor=\"middle\" font-size=\"11\" fill=\"var(--fs-ink-3)\">%d</text>",
                    x + barWidth / 2, height - 8, point.month()));
            }
        }
        double lastX = left + (points.size() - 1) * slot + slot / 2;
        svg.append(String.format(Locale.ROOT,
            "<text x=\"%.1f\" y=\"14\" text-anchor=\"end\" font-size=\"12\" font-weight=\"600\" fill=\"var(--fs-ink)\">%s</text>",
            lastX + barWidth / 2, Fmt.moneyWhole(points.get(points.size() - 1).extraSaved(), currency)));
        return svg.append("</svg>").toString();
    }

    private static long stepFor(long average) {
        if (average >= 20_000) {
            return 500;
        }
        if (average >= 5_000) {
            return 100;
        }
        if (average >= 1_000) {
            return 50;
        }
        return average >= 200 ? 10 : 1;
    }
}
