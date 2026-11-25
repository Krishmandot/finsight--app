package com.finsight.ui.views;

import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.analytics.AnalyticsService;
import com.finsight.service.analytics.CategoryAmount;
import com.finsight.service.analytics.CurrencyNotice;
import com.finsight.service.analytics.Insight;
import com.finsight.service.analytics.MonthlyPoint;
import com.finsight.service.analytics.PeriodSummary;
import com.finsight.service.transaction.TransactionService;
import com.finsight.ui.charts.Chart;
import com.finsight.ui.charts.DataTable;
import com.finsight.ui.charts.SvgCharts;
import com.finsight.ui.components.CategoryBars;
import com.finsight.ui.components.EmptyState;
import com.finsight.ui.components.KpiStrip;
import com.finsight.ui.components.Notice;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.finsight.ui.transactions.TransactionDialogs;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Overview | FinSight")
@PermitAll
public class DashboardView extends AppPage {

    private final transient AnalyticsService analyticsService;
    private final transient TransactionService transactionService;
    private final transient TransactionDialogs dialogs;
    private final transient CurrentUser currentUser;

    public DashboardView(AnalyticsService analyticsService, TransactionService transactionService,
                         TransactionDialogs dialogs, CurrentUser currentUser) {
        this.analyticsService = analyticsService;
        this.transactionService = transactionService;
        this.dialogs = dialogs;
        this.currentUser = currentUser;
        render();
    }

    private void render() {
        removeAll();
        UUID userId = currentUser.id();
        String currency = currentUser.currency();
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        String firstName = currentUser.profile().getDisplayName().split("\\s+")[0];

        Button add = new Button("Add transaction", Ui.icon(VaadinIcon.PLUS), event ->
            dialogs.openCreate(TransactionType.DEBIT, this::changed));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        RouterLink importLink = new RouterLink(ImportView.class);
        Button importButton = new Button("Import statement", Ui.icon(VaadinIcon.UPLOAD_ALT));
        importLink.add(importButton);
        PageHeader header = new PageHeader(greeting(today) + ", " + firstName,
            Fmt.month(month) + " · figures from approved transactions in " + currency)
            .actions(importLink, add);
        add(header);

        if (transactionService.activeCount(userId) == 0) {
            add(onboarding());
            return;
        }

        long pending = transactionService.pendingCount(userId);
        if (pending > 0) {
            add(reviewCallout(pending));
        }

        PeriodSummary current = analyticsService.summary(userId, currency, month.atDay(1), today);
        YearMonth previousMonth = month.minusMonths(1);
        LocalDate previousEnd = previousMonth.atDay(Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth()));
        PeriodSummary previous = analyticsService.summary(userId, currency, previousMonth.atDay(1), previousEnd);
        add(kpis(current, previous, currency, previousEnd));

        CurrencyNotice notice = analyticsService.currencyNotice(userId, currency, month.atDay(1), today);
        if (!notice.isEmpty()) {
            add(new Notice("neutral", notice.total() + " transaction(s) this month in "
                + String.join(", ", notice.counts().keySet()) + " aren't included because figures are shown in "
                + currency + ". You can change your currency in Settings."));
        }

        List<MonthlyPoint> trend = analyticsService.monthlyTrend(userId, currency, month, 6);
        List<CategoryAmount> categories = analyticsService.spendingByCategory(userId, currency, month.atDay(1), today);
        add(Ui.div("fs-grid-2", trendPanel(trend, currency), categoryPanel(categories, currency, month)));

        List<TransactionResponse> recent = transactionService.recent(userId, 8);
        List<Insight> insights = analyticsService.insights(userId, currency, month, today, pending);
        add(Ui.div("fs-grid-2", recentPanel(recent), insightsPanel(insights)));
    }

    private void changed() {
        render();
        refreshLayout();
    }

    private Component onboarding() {
        Button add = new Button("Add a transaction", event -> dialogs.openCreate(TransactionType.DEBIT, this::changed));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        RouterLink importLink = new RouterLink(ImportView.class);
        importLink.add(new Button("Import a statement"));
        EmptyState empty = new EmptyState(VaadinIcon.CHART_LINE, "Your overview starts with your first transaction",
            "Add income and expenses as they happen, or import a CSV or PDF statement from your bank. "
                + "FinSight builds your dashboard, analytics and reports from the transactions you approve.")
            .actions(add, importLink);
        return new Panel(null).content(empty);
    }

    private Component reviewCallout(long pending) {
        Div text = Ui.div("fs-callout-text", Ui.badge(pending + " to review", "pending"),
            Ui.span("fs-muted", "Imported or unreviewed transactions aren't counted until you approve them."));
        RouterLink link = new RouterLink(ReviewView.class);
        link.add(new Button("Review now"));
        return Ui.div("fs-callout", text, link);
    }

    private Component kpis(PeriodSummary current, PeriodSummary previous, String currency, LocalDate previousEnd) {
        boolean comparable = previous.hasData();
        String vs = comparable ? "vs same period last month" : "No data for last month yet";
        KpiStrip strip = new KpiStrip()
            .kpi("Income", Fmt.moneyWhole(current.income(), currency), "--fs-series-income",
                comparable ? KpiStrip.delta(change(current.income(), previous.income()), true) : Ui.span("", ""),
                Ui.span("", vs))
            .kpi("Spending", Fmt.moneyWhole(current.spending(), currency), "--fs-series-spend",
                comparable ? KpiStrip.delta(change(current.spending(), previous.spending()), false) : Ui.span("", ""),
                Ui.span("", comparable ? vs : current.expenseCount() + " expenses this month"))
            .kpi("Net savings", signedWhole(current.net(), currency), null,
                Ui.span("", current.net().signum() < 0 ? "Spent more than earned" : "Income minus spending"));
        String rate = current.savingsRate() == null ? "—" : current.savingsRate().setScale(0, java.math.RoundingMode.HALF_UP) + "%";
        strip.kpi("Savings rate", rate, null, Ui.span("", current.savingsRate() == null
            ? "Shown once income is recorded" : "Share of income kept"));
        return strip;
    }

    private Component trendPanel(List<MonthlyPoint> trend, String currency) {
        Panel panel = new Panel("Income vs spending", "Last 6 months");
        RouterLink more = new RouterLink("Analytics", AnalyticsView.class);
        more.setClassName("fs-link");
        panel.headerEnd(more);
        boolean any = trend.stream().anyMatch(MonthlyPoint::hasData);
        if (!any) {
            panel.content(new EmptyState(VaadinIcon.BAR_CHART, "No approved transactions yet",
                "Approve transactions to see your monthly trend."));
            return panel;
        }
        List<List<String>> rows = trend.stream().map(p -> List.of(Fmt.month(p.month()),
            Fmt.moneyWhole(p.income(), currency), Fmt.moneyWhole(p.spending(), currency),
            Fmt.moneyWhole(p.net(), currency))).toList();
        panel.content(new Chart(SvgCharts.incomeVsSpending(trend, currency),
            List.of(Chart.LegendItem.bar("--fs-series-income", "Income"), Chart.LegendItem.bar("--fs-series-spend", "Spending")),
            DataTable.of(List.of("Month", "Income", "Spending", "Net"), new boolean[]{false, true, true, true}, rows)));
        return panel;
    }

    private Component categoryPanel(List<CategoryAmount> categories, String currency, YearMonth month) {
        Panel panel = new Panel("Spending by category", month.format(Fmt.MONTH) + " so far");
        if (categories.isEmpty()) {
            panel.content(new EmptyState(VaadinIcon.PIE_CHART, "No spending this month",
                "Approved expenses for " + month.format(Fmt.MONTH) + " will be broken down here."));
            return panel;
        }
        panel.content(new CategoryBars(categories, currency, 6, false, item -> getUI().ifPresent(ui -> ui.navigate(
            TransactionsView.class, categoryQuery(item, month)))));
        return panel;
    }

    static QueryParameters categoryQuery(CategoryAmount item, YearMonth month) {
        Map<String, String> params = new java.util.HashMap<>();
        params.put(item.categoryId() == null ? "uncategorized" : "category",
            item.categoryId() == null ? "true" : item.categoryId().toString());
        params.put("from", month.atDay(1).toString());
        params.put("to", month.atEndOfMonth().toString());
        params.put("type", "DEBIT");
        return QueryParameters.simple(params);
    }

    private Component recentPanel(List<TransactionResponse> recent) {
        Panel panel = new Panel("Recent activity").flush();
        Div list = Ui.div("fs-list");
        for (TransactionResponse t : recent) {
            Div left = new Div(Ui.text("fs-cell-primary", t.description()),
                Ui.text("fs-cell-secondary", Fmt.dateShort(t.transactionDate()) + " · "
                    + (t.category() == null ? "Uncategorized" : t.category().name())));
            left.getStyle().set("min-width", "0");
            Div right = Ui.div("fs-list-end", Ui.amount(t));
            if (!t.countsInReports()) {
                right.add(Ui.statusBadge(t));
            }
            Div row = Ui.div("fs-list-row clickable", left, right);
            row.getElement().setAttribute("role", "button");
            row.getElement().setAttribute("tabindex", "0");
            row.addClickListener(event -> dialogs.openDetails(t.id(), this::changed));
            row.getElement().addEventListener("keydown", event -> dialogs.openDetails(t.id(), this::changed))
                .setFilter("event.key === 'Enter'");
            list.add(row);
        }
        panel.content(list);
        RouterLink all = new RouterLink("View all transactions", TransactionsView.class);
        all.setClassName("fs-link");
        panel.footer(all);
        return panel;
    }

    private Component insightsPanel(List<Insight> insights) {
        Panel panel = new Panel("Insights", "Observations from your own data").flush();
        if (insights.isEmpty()) {
            panel.content(new EmptyState(VaadinIcon.LIGHTBULB, "Not enough data yet",
                "Insights appear once there's enough approved history to compare, usually after a few weeks."));
            return panel;
        }
        Div list = Ui.div("fs-insights");
        insights.stream().limit(5).forEach(insight -> list.add(insightRow(insight)));
        panel.content(list);
        return panel;
    }

    static Component insightRow(Insight insight) {
        VaadinIcon icon = switch (insight.tone()) {
            case POSITIVE -> VaadinIcon.ARROW_DOWN;
            case WARNING -> VaadinIcon.EXCLAMATION;
            case NEUTRAL -> VaadinIcon.INFO;
        };
        String tone = insight.tone().name().toLowerCase();
        Div iconBox = Ui.div("fs-insight-icon", Ui.icon(insight.tone() == Insight.Tone.POSITIVE ? VaadinIcon.CHECK : icon));
        return Ui.div("fs-insight " + tone, iconBox,
            new Div(Ui.text("fs-insight-title", insight.title()), Ui.text("fs-insight-detail", insight.detail())));
    }

    private static Double change(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100))
            .divide(previous.abs(), 2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    static String signedWhole(BigDecimal value, String currency) {
        String formatted = Fmt.moneyWhole(value.abs(), currency);
        return value.signum() < 0 ? "−" + formatted : formatted;
    }

    private static String greeting(LocalDate today) {
        int hour = java.time.LocalTime.now().getHour();
        return hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
    }

    @SuppressWarnings("unused")
    private static String joinCurrencies(Map<String, Long> counts) {
        return counts.keySet().stream().collect(Collectors.joining(", "));
    }
}
