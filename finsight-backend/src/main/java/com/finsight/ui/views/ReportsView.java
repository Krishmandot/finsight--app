package com.finsight.ui.views;

import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.service.analytics.AnalyticsService;
import com.finsight.service.analytics.CategoryComparison;
import com.finsight.service.analytics.PeriodSummary;
import com.finsight.service.report.CsvExportService;
import com.finsight.service.report.FinancialReport;
import com.finsight.service.report.ReportPeriodType;
import com.finsight.service.report.ReportService;
import com.finsight.ui.charts.Chart;
import com.finsight.ui.charts.DataTable;
import com.finsight.ui.charts.SvgCharts;
import com.finsight.ui.components.EmptyState;
import com.finsight.ui.components.KpiStrip;
import com.finsight.ui.components.Notice;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.SegmentedControl;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Route(value = "reports", layout = MainLayout.class)
@PageTitle("Reports | FinSight")
@PermitAll
@SuppressWarnings("deprecation")
public class ReportsView extends AppPage {

    private final transient ReportService reportService;
    private final transient CsvExportService csvExportService;
    private final transient CurrentUser currentUser;
    private final SegmentedControl<ReportPeriodType> periodType = new SegmentedControl<>("Report period",
        List.of(ReportPeriodType.WEEKLY, ReportPeriodType.FORTNIGHTLY, ReportPeriodType.MONTHLY), ReportPeriodType::label);
    private final H2 periodLabel = new H2();
    private final Div content = new Div();
    private final Div exportHolder = new Div();
    private LocalDate anchor = LocalDate.now();

    public ReportsView(ReportService reportService, CsvExportService csvExportService, AnalyticsService unused,
                       CurrentUser currentUser) {
        this.reportService = reportService;
        this.csvExportService = csvExportService;
        this.currentUser = currentUser;
        periodType.setValue(ReportPeriodType.MONTHLY);
        periodType.addValueChangeListener(v -> render());
        Button prev = new Button(Ui.icon(VaadinIcon.ANGLE_LEFT), e -> shift(-1));
        Button next = new Button(Ui.icon(VaadinIcon.ANGLE_RIGHT), e -> shift(1));
        Button current = new Button("Current", e -> { anchor = LocalDate.now(); render(); });
        current.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Ui.label(prev, "Previous period");
        Ui.label(next, "Next period");
        Button print = new Button("Print / PDF", Ui.icon(VaadinIcon.PRINT), e -> getUI().ifPresent(ui -> ui.getPage().executeJs("window.print()")));
        periodLabel.addClassName("fs-panel-title");
        periodLabel.getStyle().set("font-size", "1.0625rem").set("margin", "0 8px");
        Div nav = Ui.div("fs-toolbar fs-no-print", periodType, prev, periodLabel, next, current);
        nav.getStyle().set("align-items", "center");
        content.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "16px");
        PageHeader header = new PageHeader("Reports", "Weekly, fortnightly (15-day) and monthly summaries of approved transactions");
        header.actions(exportHolder, print);
        exportHolder.addClassName("fs-no-print");
        print.addClassName("fs-no-print");
        add(header, nav, content);
        render();
    }

    private void shift(int direction) {
        FinancialReport r = reportService.generate(currentUser.id(), currentUser.currency(), periodType.getValue(), anchor);
        anchor = direction < 0 ? r.period().previous().start() : r.period().next().start();
        render();
    }

    private void render() {
        content.removeAll();
        String currency = currentUser.currency();
        FinancialReport report = reportService.generate(currentUser.id(), currency, periodType.getValue(), anchor);
        periodLabel.setText(report.period().label());

        exportHolder.removeAll();
        StreamResource csv = new StreamResource("finsight-" + report.period().type().noun() + "-" + report.period().start() + ".csv",
            () -> new ByteArrayInputStream(csvExportService.report(
                reportService.generate(currentUser.id(), currency, report.period().type(), report.period().start()))));
        Anchor download = new Anchor(csv, "");
        download.getElement().setAttribute("download", true);
        download.add(new Button("Export CSV", Ui.icon(VaadinIcon.DOWNLOAD_ALT)));
        exportHolder.add(download);

        PeriodSummary s = report.summary();
        PeriodSummary p = report.previousSummary();
        String noun = report.period().type().noun();
        KpiStrip kpis = new KpiStrip()
            .kpi("Income", Fmt.moneyWhole(s.income(), currency), "--fs-series-income",
                p == null ? Ui.span("", "No previous " + noun + " data") : KpiStrip.delta(pct(s.income(), p.income()), true),
                Ui.span("", p == null ? "" : "vs " + Fmt.moneyWhole(p.income(), currency)))
            .kpi("Spending", Fmt.moneyWhole(s.spending(), currency), "--fs-series-spend",
                p == null ? Ui.span("", "No previous " + noun + " data") : KpiStrip.delta(pct(s.spending(), p.spending()), false),
                Ui.span("", p == null ? "" : "vs " + Fmt.moneyWhole(p.spending(), currency)))
            .kpi("Net savings", DashboardView.signedWhole(s.net(), currency), null,
                Ui.span("", p == null ? "" : "previous " + DashboardView.signedWhole(p.net(), currency)))
            .kpi("Transactions", String.valueOf(s.transactionCount()), null,
                Ui.span("", s.incomeCount() + " income · " + s.expenseCount() + " expenses"));
        content.add(kpis);

        if (report.excluded().total() > 0) {
            content.add(new Notice("neutral", "Not counted in this " + noun + ": " + report.excluded().pending() + " pending, "
                + report.excluded().rejected() + " rejected and " + report.excluded().excludedApproved() + " excluded transaction(s)."));
        }
        if (!report.otherCurrencies().isEmpty()) {
            content.add(new Notice("neutral", report.otherCurrencies().total() + " transaction(s) in other currencies aren't included."));
        }
        if (!s.hasData()) {
            content.add(new Panel(null).content(new EmptyState(VaadinIcon.FILE_TEXT_O, "No approved transactions in this " + noun,
                "Use the arrows to look at another period, or approve pending transactions in Review.")));
            return;
        }

        List<List<String>> dailyRows = report.daily().stream().filter(d -> d.income().signum() != 0 || d.spending().signum() != 0)
            .map(d -> List.of(Fmt.date(d.date()), Fmt.moneyWhole(d.income(), currency), Fmt.moneyWhole(d.spending(), currency))).toList();
        Panel daily = new Panel("Daily spending").content(new Chart(SvgCharts.dailySpending(report.daily(), currency), null,
            DataTable.of(List.of("Date", "Income", "Spending"), new boolean[]{false, true, true}, dailyRows)));

        List<List<String>> catRows = new ArrayList<>();
        for (CategoryComparison c : report.categories()) {
            if (c.current().signum() == 0 && c.previous().signum() == 0) {
                continue;
            }
            catRows.add(List.of(c.name(), Fmt.moneyWhole(c.current(), currency),
                report.hasComparison() ? Fmt.moneyWhole(c.previous(), currency) : "—",
                report.hasComparison() && c.changePercent() != null ? Fmt.percentChange(c.changePercent()) : "—"));
        }
        Panel categories = new Panel("Spending by category", report.hasComparison() ? "Compared with " + report.previousPeriod().label() : null)
            .content(catRows.isEmpty() ? new EmptyState(VaadinIcon.PIE_CHART, "No spending", null)
                : DataTable.of(List.of("Category", "This " + noun, "Previous", "Change"), new boolean[]{false, true, true, true}, catRows));
        content.add(Ui.div("fs-grid-2 even", daily, categories));

        List<List<String>> txRows = report.transactions().stream().limit(200).map(t -> List.of(Fmt.date(t.transactionDate()),
            t.description(), t.category() == null ? "Uncategorized" : t.category().name(), Fmt.type(t.type()), Fmt.signed(t))).toList();
        Panel transactions = new Panel("Transactions", report.transactions().size() + " counted"
            + (report.transactions().size() > 200 ? " · first 200 shown; export CSV for all" : ""))
            .content(DataTable.of(List.of("Date", "Description", "Category", "Type", "Amount"),
                new boolean[]{false, false, false, false, true}, txRows));
        if (!report.largestExpenses().isEmpty()) {
            Div list = Ui.div("fs-list");
            for (TransactionResponse t : report.largestExpenses()) {
                list.add(Ui.div("fs-list-row", new Div(Ui.text("fs-cell-primary", t.description()),
                    Ui.text("fs-cell-secondary", Fmt.dateShort(t.transactionDate()))), Ui.amount(t)));
            }
            content.add(Ui.div("fs-grid-2", transactions, new Panel("Largest expenses").flush().content(list)));
        } else {
            content.add(transactions);
        }
    }

    private static Double pct(BigDecimal now, BigDecimal before) {
        if (before.signum() == 0) {
            return null;
        }
        return now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before.abs(), 2, RoundingMode.HALF_UP).doubleValue();
    }
}
