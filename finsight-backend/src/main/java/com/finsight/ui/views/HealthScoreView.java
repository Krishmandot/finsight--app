package com.finsight.ui.views;

import com.finsight.api.exception.BusinessRuleException;
import com.finsight.service.analytics.Insight;
import com.finsight.service.intelligence.BudgetService;
import com.finsight.service.intelligence.HealthScore;
import com.finsight.service.intelligence.IntelligenceService;
import com.finsight.ui.components.EmptyState;
import com.finsight.ui.components.Notice;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.finsight.ui.support.Toasts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Explainable financial health score out of 100, with the factors behind it and a monthly budget setting. */
@Route(value = "health-score", layout = MainLayout.class)
@PageTitle("Health Score | FinSight")
@PermitAll
public class HealthScoreView extends AppPage {

    private final transient IntelligenceService intelligence;
    private final transient BudgetService budgets;
    private final transient CurrentUser currentUser;
    private final Div content = new Div();

    public HealthScoreView(IntelligenceService intelligence, BudgetService budgets, CurrentUser currentUser) {
        this.intelligence = intelligence;
        this.budgets = budgets;
        this.currentUser = currentUser;
        content.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "16px");
        add(new PageHeader("Financial Health Score",
            "One explainable number from your savings, budget, consistency and cash flow"), content);
        render();
    }

    private void render() {
        content.removeAll();
        UUID userId = currentUser.id();
        String currency = currentUser.currency();
        HealthScore score = intelligence.healthScore(userId, currency, LocalDate.now());

        if (!score.available()) {
            content.add(new Panel(null).content(new EmptyState(VaadinIcon.CHECK_CIRCLE_O, "No score yet",
                score.tips().isEmpty() ? "Add and approve some transactions to see your score." : score.tips().get(0))));
            content.add(budgetPanel(userId, currency));
            return;
        }
        if (score.provisional()) {
            content.add(new Notice("info", "This score uses only the current month so far, so it will change as the month goes on."));
        }

        String tone = score.score() >= 65 ? "good" : score.score() >= 50 ? "fair" : "low";
        Div ring = Ui.div("fs-score-ring " + tone,
            Ui.div("fs-score-inner", Ui.text("fs-score-value", String.valueOf(score.score())),
                Ui.text("fs-score-of", "out of 100")));
        ring.getStyle().set("--fs-score", String.valueOf(score.score()));
        ring.getElement().setAttribute("role", "img");
        ring.getElement().setAttribute("aria-label", "Financial health score " + score.score() + " out of 100, " + score.label());
        String badgeVariant = score.score() >= 65 ? "approved" : score.score() >= 50 ? "pending" : "rejected";
        Div meta = Ui.div("fs-score-meta", Ui.badge(score.label(), badgeVariant),
            Ui.text("fs-score-note", "Each factor below is scored from 0 to 100 and weighted. A factor without enough data "
                + "is left out, and the remaining weights are scaled to 100, so missing data never lowers your score."));
        content.add(new Panel("Your score", "Based on your last few months of approved transactions")
            .content(Ui.div("fs-score-card", ring, meta)));

        Div factors = Ui.div("fs-factors");
        for (HealthScore.Factor factor : score.factors()) {
            Div fill = Ui.div("fs-bar-fill");
            fill.getStyle().set("width", factor.score() + "%").set("background", "var(--fs-accent)");
            Div track = Ui.div("fs-bar-track", fill);
            track.getElement().setAttribute("aria-hidden", "true");
            String right = factor.included() ? factor.score() + " / 100" : "Not included";
            String detail = factor.included()
                ? factor.detail() + " Counts for " + factor.weight() + "% of your score."
                : factor.detail();
            factors.add(Ui.div("fs-factor" + (factor.included() ? "" : " off"),
                Ui.div("fs-factor-head", Ui.text("fs-factor-name", factor.name()), Ui.span("fs-factor-score", right)),
                track, Ui.text("fs-factor-detail", detail)));
        }
        content.add(new Panel("What makes up your score", "Weights add up to 100%").content(factors));

        if (!score.tips().isEmpty()) {
            Div list = Ui.div("fs-insights");
            score.tips().forEach(tip -> list.add(DashboardView.insightRow(
                new Insight(Insight.Tone.NEUTRAL, "Suggestion", tip))));
            content.add(new Panel("How to improve", "Weakest factor first").flush().content(list));
        }
        content.add(budgetPanel(userId, currency));
    }

    private Panel budgetPanel(UUID userId, String currency) {
        BigDecimalField field = new BigDecimalField("Monthly budget (" + currency + ")");
        field.setHelperText("The most you want to spend in a month. Leave empty to skip this factor.");
        field.setWidth("280px");
        budgets.find(userId).ifPresent(field::setValue);

        Button save = new Button("Save budget", event -> {
            try {
                budgets.save(userId, field.getValue());
                Toasts.success("Budget saved");
                render();
            } catch (BusinessRuleException ex) {
                Toasts.error(ex.getMessage());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button remove = new Button("Remove budget", event -> {
            budgets.clear(userId);
            Toasts.info("Budget removed");
            render();
        });
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        remove.setVisible(budgets.find(userId).isPresent());

        return new Panel("Monthly budget", "Used for the budget adherence factor")
            .content(Ui.div("fs-toolbar", field, save, remove));
    }
}
