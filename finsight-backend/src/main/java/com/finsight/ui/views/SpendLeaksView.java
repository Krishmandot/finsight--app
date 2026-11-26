package com.finsight.ui.views;

import com.finsight.service.intelligence.IntelligenceService;
import com.finsight.service.intelligence.LeakReport;
import com.finsight.service.intelligence.SpendLeak;
import com.finsight.ui.components.EmptyState;
import com.finsight.ui.components.KpiStrip;
import com.finsight.ui.components.Notice;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import java.util.List;

/** SpendLeak Detector: recurring payments, unusual spikes, possible double charges and category surges. */
@Route(value = "spend-leaks", layout = MainLayout.class)
@PageTitle("SpendLeaks | FinSight")
@PermitAll
public class SpendLeaksView extends AppPage {

    public SpendLeaksView(IntelligenceService intelligence, CurrentUser currentUser) {
        String currency = currentUser.currency();
        add(new PageHeader("SpendLeak Detector",
            "Recurring payments, unusual spikes, possible double charges and categories running above normal"));

        LeakReport report = intelligence.leaks(currentUser.id(), currency, LocalDate.now());
        Div content = new Div();
        content.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "16px");

        if (report.historyNote() != null) {
            content.add(new Notice("info", report.historyNote()));
        }
        long recurring = report.leaks().stream().filter(l -> l.type() == SpendLeak.Type.RECURRING_PAYMENT).count();
        content.add(new KpiStrip().three()
            .kpi("Flagged every month", Fmt.moneyWhole(report.monthlyImpact(), currency), "--fs-series-spend",
                Ui.span("", "Subscriptions and categories running high"))
            .kpi("One-off amounts flagged", Fmt.moneyWhole(report.oneOffImpact(), currency), null,
                Ui.span("", "Spikes and possible double charges"))
            .kpi("Findings", String.valueOf(report.leaks().size()), null,
                Ui.span("", recurring + " recurring " + (recurring == 1 ? "payment" : "payments"))));

        if (report.leaks().isEmpty()) {
            content.add(new Panel(null).content(new EmptyState(VaadinIcon.CHECK_CIRCLE_O, "No leaks found",
                "Nothing unusual in your approved transactions. This page checks again every time you open it.")));
        } else {
            addSection(content, "Recurring payments", "Same payee, steady amount, repeated about monthly",
                report.leaks(), SpendLeak.Type.RECURRING_PAYMENT, currency);
            addSection(content, "Categories running high", "Last 30 days against your earlier monthly average",
                report.leaks(), SpendLeak.Type.CATEGORY_SURGE, currency);
            addSection(content, "Unusually large expenses", "Far above what is normal for the category",
                report.leaks(), SpendLeak.Type.UNUSUAL_SPIKE, currency);
            addSection(content, "Possible double charges", "Same payee and amount within a day",
                report.leaks(), SpendLeak.Type.POSSIBLE_DUPLICATE, currency);
        }
        add(content);
    }

    private static void addSection(Div content, String title, String caption, List<SpendLeak> all,
                                   SpendLeak.Type type, String currency) {
        List<SpendLeak> items = all.stream().filter(l -> l.type() == type).toList();
        if (items.isEmpty()) {
            return;
        }
        Div list = Ui.div("fs-insights");
        items.forEach(leak -> list.add(row(leak, currency)));
        content.add(new Panel(title, caption).flush().content(list));
    }

    private static Component row(SpendLeak leak, String currency) {
        VaadinIcon icon = switch (leak.type()) {
            case RECURRING_PAYMENT -> VaadinIcon.REFRESH;
            case UNUSUAL_SPIKE -> VaadinIcon.EXCLAMATION;
            case POSSIBLE_DUPLICATE -> VaadinIcon.COPY;
            case CATEGORY_SURGE -> VaadinIcon.BAR_CHART;
        };
        String tone = leak.severity() == SpendLeak.Severity.HIGH ? "warning" : "neutral";
        String variant = switch (leak.severity()) {
            case HIGH -> "rejected";
            case MEDIUM -> "pending";
            case LOW -> null;
        };
        String severityText = switch (leak.severity()) {
            case HIGH -> "High priority";
            case MEDIUM -> "Worth a look";
            case LOW -> "Low";
        };
        String impact;
        if (leak.impact().signum() == 0) {
            impact = "Fixed cost";
        } else if (leak.monthlyImpact()) {
            impact = Fmt.moneyWhole(leak.impact(), currency) + " / month";
        } else {
            impact = Fmt.moneyWhole(leak.impact(), currency) + " at stake";
        }
        Div head = Ui.div("fs-leak-head", Ui.text("fs-insight-title", leak.title()),
            Ui.div("fs-leak-badges", Ui.badge(severityText, variant), Ui.badge(impact, "info")));
        Div body = new Div(head, Ui.text("fs-insight-detail", leak.detail()),
            Ui.text("fs-insight-detail fs-leak-action", "What you can do: " + leak.action()));
        return Ui.div("fs-insight " + tone, Ui.div("fs-insight-icon", Ui.icon(icon)), body);
    }
}
