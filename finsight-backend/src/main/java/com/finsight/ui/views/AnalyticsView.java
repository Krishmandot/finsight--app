package com.finsight.ui.views;

import com.finsight.service.analytics.AnalyticsService;
import com.finsight.service.analytics.CategoryAmount;
import com.finsight.service.analytics.CategoryComparison;
import com.finsight.service.analytics.MonthlyPoint;
import com.finsight.service.analytics.PeriodSummary;
import com.finsight.ui.charts.Chart;
import com.finsight.ui.charts.DataTable;
import com.finsight.ui.charts.SvgCharts;
import com.finsight.ui.components.CategoryBars;
import com.finsight.ui.components.EmptyState;
import com.finsight.ui.components.KpiStrip;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.SegmentedControl;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Route(value = "analytics", layout = MainLayout.class)
@PageTitle("Analytics | FinSight")
@PermitAll
public class AnalyticsView extends AppPage {

    private final transient AnalyticsService analytics;
    private final transient CurrentUser currentUser;
    private final SegmentedControl<Integer> range = new SegmentedControl<>("Time range", List.of(3, 6, 12),
        m -> "Last " + m + " months");
    private final Select<YearMonth> compareA = new Select<>();
    private final Select<YearMonth> compareB = new Select<>();
    private final Div content = new Div();
    private final Div comparison = new Div();
    private boolean compareReady;

    public AnalyticsView(AnalyticsService analytics, CurrentUser currentUser) {
        this.analytics = analytics;
        this.currentUser = currentUser;
        range.setValue(6);
        range.addValueChangeListener(v -> render());
        content.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "16px");
        add(new PageHeader("Analytics", "Trends from approved transactions in " + currentUser.currency()), Ui.div("fs-toolbar", range), content);
        render();
    }

    private void render() {
        content.removeAll();
        UUID userId = currentUser.id();
        String currency = currentUser.currency();
        int months = range.getValue();
        YearMonth end = YearMonth.now();
        LocalDate from = end.minusMonths(months - 1L).atDay(1);
        LocalDate to = LocalDate.now();
        List<MonthlyPoint> trend = analytics.monthlyTrend(userId, currency, end, months);
        if (trend.stream().noneMatch(MonthlyPoint::hasData)) {
            content.add(new Panel(null).content(new EmptyState(VaadinIcon.BAR_CHART, "No approved transactions in this range",
                "Analytics are built from approved transactions. Add or import transactions, then approve them in Review.")));
            return;
        }
        PeriodSummary total = analytics.summary(userId, currency, from, to);
        long monthsWithData = trend.stream().filter(MonthlyPoint::hasData).count();
        BigDecimal avgSpend = total.spending().divide(BigDecimal.valueOf(Math.max(1, monthsWithData)), 0, java.math.RoundingMode.HALF_UP);
        content.add(new KpiStrip()
            .kpi("Income", Fmt.moneyWhole(total.income(), currency), "--fs-series-income", Ui.span("", total.incomeCount() + " transactions"))
            .kpi("Spending", Fmt.moneyWhole(total.spending(), currency), "--fs-series-spend", Ui.span("", "Avg " + Fmt.moneyWhole(avgSpend, currency) + " / month"))
            .kpi("Net cash flow", DashboardView.signedWhole(total.net(), currency), null, Ui.span("", "Income minus spending"))
            .kpi("Savings rate", total.savingsRate() == null ? "—" : total.savingsRate().setScale(0, java.math.RoundingMode.HALF_UP) + "%", null,
                Ui.span("", "Over the whole range")));

        List<List<String>> rows = trend.stream().map(p -> List.of(Fmt.month(p.month()), Fmt.moneyWhole(p.income(), currency),
            Fmt.moneyWhole(p.spending(), currency), Fmt.moneyWhole(p.net(), currency),
            p.savingsRate() == null ? "—" : p.savingsRate().setScale(0, java.math.RoundingMode.HALF_UP) + "%")).toList();
        Component table = DataTable.of(List.of("Month", "Income", "Spending", "Net", "Savings rate"),
            new boolean[]{false, true, true, true, true}, rows);
        Panel incomeSpend = new Panel("Income vs spending", "Monthly totals").content(new Chart(SvgCharts.incomeVsSpending(trend, currency),
            List.of(Chart.LegendItem.bar("--fs-series-income", "Income"), Chart.LegendItem.bar("--fs-series-spend", "Spending")), table));
        Panel net = new Panel("Net cash flow", "Blue: saved · Red: overspent").content(new Chart(SvgCharts.netCashFlow(trend, currency), null, null));
        content.add(Ui.div("fs-grid-2 even", incomeSpend, net));

        List<CategoryAmount> spending = analytics.spendingByCategory(userId, currency, from, to);
        List<CategoryAmount> income = analytics.incomeByCategory(userId, currency, from, to);
        Panel topCategories = new Panel("Highest spending categories", "Selected range");
        topCategories.content(spending.isEmpty() ? new EmptyState(VaadinIcon.PIE_CHART, "No spending", null)
            : new CategoryBars(spending, currency, 8, false, null));
        Panel savings = new Panel("Savings rate", "Share of income kept each month")
            .content(new Chart(SvgCharts.savingsRate(trend), null, null));
        content.add(Ui.div("fs-grid-2 even", topCategories, savings));
        if (!income.isEmpty()) {
            content.add(new Panel("Income sources", "Selected range").content(new CategoryBars(income, currency, 6, true, null)));
        }

        // Month comparison
        List<YearMonth> monthsList = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            monthsList.add(end.minusMonths(i));
        }
        if (!compareReady) {
            compareReady = true;
            compareA.setLabel("Compare");
            compareB.setLabel("with");
            compareA.setItems(monthsList);
            compareB.setItems(monthsList);
            compareA.setItemLabelGenerator(Fmt::month);
            compareB.setItemLabelGenerator(Fmt::month);
            compareA.setValue(end);
            compareB.setValue(end.minusMonths(1));
            compareA.addValueChangeListener(e -> renderComparison());
            compareB.addValueChangeListener(e -> renderComparison());
        }
        Panel comparePanel = new Panel("Compare months", "Spending by category").content(Ui.div("fs-toolbar", compareA, compareB), comparison);
        content.add(comparePanel);
        renderComparison();
    }

    private void renderComparison() {
        comparison.removeAll();
        YearMonth a = compareA.getValue();
        YearMonth b = compareB.getValue();
        if (a == null || b == null) {
            return;
        }
        UUID userId = currentUser.id();
        String currency = currentUser.currency();
        PeriodSummary sa = analytics.summary(userId, currency, a.atDay(1), a.atEndOfMonth());
        PeriodSummary sb = analytics.summary(userId, currency, b.atDay(1), b.atEndOfMonth());
        if (!sa.hasData() && !sb.hasData()) {
            comparison.add(new EmptyState(VaadinIcon.CALENDAR, "No data for these months", null));
            return;
        }
        List<CategoryComparison> rows = analytics.compareCategories(userId, currency, a.atDay(1), a.atEndOfMonth(),
            b.atDay(1), b.atEndOfMonth());
        List<List<String>> table = new ArrayList<>();
        table.add(List.of("Income", Fmt.moneyWhole(sa.income(), currency), Fmt.moneyWhole(sb.income(), currency),
            DashboardView.signedWhole(sa.income().subtract(sb.income()), currency)));
        table.add(List.of("Spending", Fmt.moneyWhole(sa.spending(), currency), Fmt.moneyWhole(sb.spending(), currency),
            DashboardView.signedWhole(sa.spending().subtract(sb.spending()), currency)));
        table.add(List.of("Net", Fmt.moneyWhole(sa.net(), currency), Fmt.moneyWhole(sb.net(), currency),
            DashboardView.signedWhole(sa.net().subtract(sb.net()), currency)));
        for (CategoryComparison c : rows) {
            table.add(List.of("  " + c.name(), Fmt.moneyWhole(c.current(), currency), Fmt.moneyWhole(c.previous(), currency),
                DashboardView.signedWhole(c.change(), currency)
                    + (c.changePercent() == null ? "" : " (" + Fmt.percentChange(c.changePercent()) + ")")));
        }
        Div wrap = new Div(DataTable.of(List.of("", Fmt.month(a), Fmt.month(b), "Difference"),
            new boolean[]{false, true, true, true}, table));
        wrap.getStyle().set("margin-top", "12px");
        comparison.add(wrap);
    }
}
