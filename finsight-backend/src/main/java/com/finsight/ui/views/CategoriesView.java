package com.finsight.ui.views;

import com.finsight.api.dto.request.CategoryRequest;
import com.finsight.api.dto.response.CategorizationRuleResponse;
import com.finsight.api.dto.response.CategoryDetailResponse;
import com.finsight.api.dto.response.CategoryResponse;
import com.finsight.api.exception.BusinessRuleException;
import com.finsight.domain.enums.CategoryKind;
import com.finsight.service.categorization.CategorizationService;
import com.finsight.service.category.CategoryService;
import com.finsight.ui.components.Confirm;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Toasts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import java.util.UUID;

@Route(value = "categories", layout = MainLayout.class)
@PageTitle("Categories | FinSight")
@PermitAll
public class CategoriesView extends AppPage {

    private final transient CategoryService categoryService;
    private final transient CategorizationService categorizationService;
    private final transient CurrentUser currentUser;
    private final Div lists = new Div();

    public CategoriesView(CategoryService categoryService, CategorizationService categorizationService, CurrentUser currentUser) {
        this.categoryService = categoryService;
        this.categorizationService = categorizationService;
        this.currentUser = currentUser;
        Button add = new Button("New category", Ui.icon(VaadinIcon.PLUS), e -> edit(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        lists.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "16px");
        add(new PageHeader("Categories", "Built-in categories plus your own, and the rules that categorize imports").actions(add), lists);
        render();
    }

    private void render() {
        lists.removeAll();
        UUID userId = currentUser.id();
        List<CategoryDetailResponse> all = categoryService.findAllWithUsage(userId);
        Div custom = Ui.div("fs-list");
        Div system = Ui.div("fs-list");
        for (CategoryDetailResponse d : all) {
            CategoryResponse c = d.category();
            Div left = new Div(Ui.text("fs-cell-primary", c.name()), Ui.text("fs-cell-secondary",
                kind(c.kind()) + " · " + d.transactionCount() + " transaction" + (d.transactionCount() == 1 ? "" : "s")));
            Div right = Ui.div("fs-page-actions");
            if (!c.systemDefined()) {
                Button rename = new Button("Edit", e -> edit(c));
                rename.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                Button delete = new Button("Delete", e -> delete(d, all));
                delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
                right.add(rename, delete);
                custom.add(Ui.div("fs-list-row", left, right));
            } else {
                system.add(Ui.div("fs-list-row", left, Ui.badge("Built-in", "plain")));
            }
        }
        Panel customPanel = new Panel("Your categories").flush();
        customPanel.content(custom.getChildren().findAny().isPresent() ? custom
            : Ui.div("fs-list", Ui.div("fs-list-row", Ui.span("fs-muted", "You haven't created any categories yet."), new Div())));
        lists.add(Ui.div("fs-grid-2 even", customPanel, rulesPanel(userId)), new Panel("Built-in categories").flush().content(system));
    }

    private Panel rulesPanel(UUID userId) {
        Panel panel = new Panel("Categorization rules", "Learned when you correct an imported transaction's category").flush();
        List<CategorizationRuleResponse> rules = categorizationService.listRules(userId);
        Div list = Ui.div("fs-list");
        if (rules.isEmpty()) {
            list.add(Ui.div("fs-list-row", Ui.span("fs-muted",
                "No personal rules yet. Built-in rules recognize common merchants automatically."), new Div()));
        }
        for (CategorizationRuleResponse r : rules) {
            Button remove = new Button("Remove", e -> {
                categorizationService.deleteRule(userId, r.id());
                Toasts.success("Rule removed");
                render();
            });
            remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            list.add(Ui.div("fs-list-row", new Div(Ui.text("fs-cell-primary", "“" + r.keyword() + "”"),
                Ui.text("fs-cell-secondary", "→ " + r.category().name())), remove));
        }
        panel.content(list);
        return panel;
    }

    private void edit(CategoryResponse existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "New category" : "Edit category");
        TextField name = new TextField("Name");
        name.setMaxLength(60);
        name.setWidth("320px");
        Select<CategoryKind> kind = new Select<>();
        kind.setLabel("Used for");
        kind.setItems(CategoryKind.values());
        kind.setItemLabelGenerator(CategoriesView::kind);
        kind.setValue(existing == null ? CategoryKind.EXPENSE : existing.kind());
        if (existing != null) {
            name.setValue(existing.name());
        }
        dialog.add(new Div(name), new Div(kind));
        Button save = new Button("Save", e -> {
            try {
                CategoryRequest request = new CategoryRequest(name.getValue(), kind.getValue());
                if (existing == null) {
                    categoryService.create(currentUser.id(), request);
                } else {
                    categoryService.update(currentUser.id(), existing.id(), request);
                }
                dialog.close();
                Toasts.success("Category saved");
                render();
            } catch (BusinessRuleException ex) {
                name.setErrorMessage(ex.getMessage());
                name.setInvalid(true);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), save);
        dialog.open();
        name.focus();
    }

    private void delete(CategoryDetailResponse detail, List<CategoryDetailResponse> all) {
        CategoryResponse c = detail.category();
        ComboBox<CategoryResponse> target = new ComboBox<>("Move its transactions to");
        target.setItems(all.stream().map(CategoryDetailResponse::category)
            .filter(o -> !o.id().equals(c.id()) && (o.kind() == CategoryKind.ANY || o.kind() == c.kind())).toList());
        target.setItemLabelGenerator(CategoryResponse::name);
        target.setWidth("320px");
        boolean inUse = detail.transactionCount() > 0;
        Confirm.ask("Delete “" + c.name() + "”?", inUse
                ? detail.transactionCount() + " transaction(s) use this category. Choose where to move them."
                : "This category isn't used by any transactions.", inUse ? target : null, "Delete", true, () -> {
                try {
                    int moved = categoryService.delete(currentUser.id(), c.id(), target.getValue() == null ? null : target.getValue().id());
                    Toasts.success(moved > 0 ? "Category deleted; " + moved + " transaction(s) moved" : "Category deleted");
                    render();
                } catch (BusinessRuleException ex) {
                    Toasts.error(ex.getMessage());
                }
            });
    }

    static String kind(CategoryKind kind) {
        return switch (kind) {
            case EXPENSE -> "Expenses";
            case INCOME -> "Income";
            case ANY -> "Any transaction";
        };
    }
}
