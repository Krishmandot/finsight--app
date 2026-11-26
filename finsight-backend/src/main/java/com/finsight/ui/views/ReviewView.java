package com.finsight.ui.views;

import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.api.exception.BusinessRuleException;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.service.transaction.TransactionFilter;
import com.finsight.service.transaction.TransactionService;
import com.finsight.ui.components.EmptyState;
import com.finsight.ui.components.Notice;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.finsight.ui.support.Toasts;
import com.finsight.ui.transactions.TransactionDialogs;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The review queue: pending transactions are approved (counted) or rejected (never counted). */
@Route(value = "review", layout = MainLayout.class)
@PageTitle("Review | FinSight")
@PermitAll
public class ReviewView extends AppPage implements BeforeEnterObserver {

    private final transient TransactionService transactionService;
    private final transient TransactionDialogs dialogs;
    private final transient CurrentUser currentUser;
    private final Grid<TransactionResponse> grid = new Grid<>();
    private final Checkbox duplicatesOnly = new Checkbox("Only possible duplicates");
    private final Button approve = new Button("Approve selected");
    private final Button reject = new Button("Reject selected");
    private final Button selectAll = new Button("Select all");
    private final PageHeader header = new PageHeader("Review", "");
    private final Div batchNotice = new Div();
    private UUID batchId;

    public ReviewView(TransactionService transactionService, TransactionDialogs dialogs, CurrentUser currentUser) {
        this.transactionService = transactionService;
        this.dialogs = dialogs;
        this.currentUser = currentUser;

        approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        approve.addClickListener(e -> bulk(TransactionReviewStatus.APPROVED));
        reject.addClickListener(e -> bulk(TransactionReviewStatus.REJECTED));
        Button rejectDuplicates = new Button("Reject all duplicates", e -> rejectDuplicates());
        rejectDuplicates.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        selectAll.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        selectAll.addClickListener(e -> toggleSelectAll());
        header.actions(selectAll, rejectDuplicates, reject, approve);
        duplicatesOnly.addValueChangeListener(e -> refresh());

        grid.addClassName("fs-table");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addColumn(t -> Fmt.dateShort(t.transactionDate())).setHeader("Date").setWidth("110px").setFlexGrow(0);
        grid.addComponentColumn(t -> {
            Div cell = new Div(Ui.text("fs-cell-primary", t.description()));
            Div meta = Ui.div("fs-cell-secondary");
            meta.setText(Fmt.type(t.type()) + " · " + Fmt.source(t.source()));
            cell.add(meta);
            return cell;
        }).setHeader("Description").setFlexGrow(3);
        grid.addComponentColumn(t -> {
            Div cell = new Div(Ui.span("", t.category() == null ? "Uncategorized" : t.category().name()));
            if (t.categorySource() != null && t.categorySource() != com.finsight.domain.enums.CategorySource.USER) {
                cell.add(Ui.text("fs-cell-secondary", "Suggested"));
            }
            return cell;
        }).setHeader("Category").setFlexGrow(1);
        grid.addComponentColumn(t -> t.duplicate() ? Ui.badge("Possible duplicate", "info") : new Div())
            .setHeader("Checks").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(Ui::amount).setHeader("Amount").setTextAlign(ColumnTextAlign.END)
            .setAutoWidth(true).setFlexGrow(0);
        grid.setItems(query -> transactionService.search(currentUser.id(), filter(),
                com.vaadin.flow.spring.data.VaadinSpringDataHelpers.toSpringPageRequest(query)).stream(),
            query -> (int) transactionService.count(currentUser.id(), filter()));
        grid.addItemClickListener(e -> dialogs.openDetails(e.getItem().id(), this::changed));
        grid.asMultiSelect().addValueChangeListener(e -> updateButtons());
        grid.setEmptyStateComponent(new EmptyState(VaadinIcon.CHECK_CIRCLE_O, "You're all caught up",
            "Imported transactions and anything you send for review will appear here."));
        grid.setHeight("600px");

        Notice explain = new Notice("neutral", "Approved transactions count in your dashboard, analytics and reports. "
            + "Rejected ones are kept for reference but never counted. Click a row to edit details or the category first.");
        add(header, explain, batchNotice, Ui.div("fs-toolbar", duplicatesOnly), new Panel(null).flush().content(grid));
        updateButtons();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        batchId = event.getLocation().getQueryParameters().getSingleParameter("batch").map(value -> {
            try { return UUID.fromString(value); } catch (IllegalArgumentException e) { return null; }
        }).orElse(null);
        batchNotice.removeAll();
        if (batchId != null) {
            Button showAll = new Button("Show everything pending", e -> getUI().ifPresent(ui -> ui.navigate(ReviewView.class)));
            showAll.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            batchNotice.add(new Notice("info", "Showing transactions from one import. ").append(showAll));
        }
        refresh();
    }

    private TransactionFilter filter() {
        return TransactionFilter.builder().statuses(Set.of(TransactionReviewStatus.PENDING))
            .duplicatesOnly(duplicatesOnly.getValue()).importBatchId(batchId).build();
    }

    private void bulk(TransactionReviewStatus status) {
        List<UUID> ids = new ArrayList<>(grid.getSelectedItems().stream().map(TransactionResponse::id).toList());
        if (ids.isEmpty()) {
            return;
        }
        try {
            int updated = transactionService.bulkReview(currentUser.id(), ids, status);
            Toasts.success(updated + (status == TransactionReviewStatus.APPROVED ? " approved" : " rejected"));
            changed();
        } catch (BusinessRuleException ex) {
            Toasts.error(ex.getMessage());
        }
    }

    /** Selects every waiting transaction (up to the bulk limit), or clears the selection if there is one. */
    private void toggleSelectAll() {
        if (!grid.getSelectedItems().isEmpty()) {
            grid.deselectAll();
            return;
        }
        List<TransactionResponse> all = transactionService.findForExport(currentUser.id(), filter());
        if (all.isEmpty()) {
            Toasts.info("Nothing is waiting for review");
            return;
        }
        List<TransactionResponse> chunk = all.stream().limit(TransactionService.MAX_BULK).toList();
        grid.asMultiSelect().select(chunk.toArray(new TransactionResponse[0]));
        if (all.size() > chunk.size()) {
            Toasts.info("Selected the first " + chunk.size() + " of " + all.size()
                + ". Approve them, then select the rest.");
        }
    }

    private void rejectDuplicates() {
        List<UUID> ids = transactionService.findForExport(currentUser.id(),
            filter().toBuilder().duplicatesOnly(true).build()).stream().map(TransactionResponse::id).limit(TransactionService.MAX_BULK).toList();
        if (ids.isEmpty()) {
            Toasts.info("No possible duplicates are waiting for review");
            return;
        }
        Toasts.success(transactionService.bulkReview(currentUser.id(), ids, TransactionReviewStatus.REJECTED)
            + " duplicates rejected");
        changed();
    }

    private void changed() {
        grid.deselectAll();
        refresh();
        refreshLayout();
    }

    private void refresh() {
        grid.getDataProvider().refreshAll();
        long count = transactionService.count(currentUser.id(), filter());
        header.setDescription(count == 0 ? "Nothing waiting for review"
            : count + " transaction" + (count == 1 ? "" : "s") + " waiting · select rows to approve or reject in bulk");
        updateButtons();
    }

    private void updateButtons() {
        int selected = grid.getSelectedItems().size();
        approve.setEnabled(selected > 0);
        reject.setEnabled(selected > 0);
        approve.setText(selected > 0 ? "Approve " + selected : "Approve selected");
        reject.setText(selected > 0 ? "Reject " + selected : "Reject selected");
        selectAll.setText(selected > 0 ? "Clear selection" : "Select all");
    }
}