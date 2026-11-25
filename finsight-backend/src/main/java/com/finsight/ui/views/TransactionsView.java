package com.finsight.ui.views;

import com.finsight.api.dto.response.CategoryResponse;
import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.category.CategoryService;
import com.finsight.service.report.CsvExportService;
import com.finsight.service.transaction.TransactionFilter;
import com.finsight.service.transaction.TransactionService;
import com.finsight.ui.components.EmptyState;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.finsight.ui.transactions.TransactionDialogs;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.data.VaadinSpringDataHelpers;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Route(value = "transactions", layout = MainLayout.class)
@PageTitle("Transactions | FinSight")
@PermitAll
public class TransactionsView extends AppPage implements BeforeEnterObserver {

    private static final CategoryResponse UNCATEGORIZED =
        new CategoryResponse(new UUID(0, 0), "Uncategorized", null, null, true);

    private final transient TransactionService transactionService;
    private final transient TransactionDialogs dialogs;
    private final transient CurrentUser currentUser;

    private final TextField search = new TextField();
    private final DatePicker from = new DatePicker("From");
    private final DatePicker to = new DatePicker("To");
    private final ComboBox<CategoryResponse> category = new ComboBox<>("Category");
    private final Select<TransactionType> type = new Select<>();
    private final Select<TransactionReviewStatus> status = new Select<>();
    private final Grid<TransactionResponse> grid = new Grid<>();
    private final PageHeader header = new PageHeader("Transactions", "");

    public TransactionsView(TransactionService transactionService, CategoryService categoryService,
                            CsvExportService csvExportService, TransactionDialogs dialogs, CurrentUser currentUser) {
        this.transactionService = transactionService;
        this.dialogs = dialogs;
        this.currentUser = currentUser;
        UUID userId = currentUser.id();

        Button addExpense = new Button("Add expense", Ui.icon(VaadinIcon.MINUS), e -> dialogs.openCreate(TransactionType.DEBIT, this::changed));
        Button addIncome = new Button("Add income", Ui.icon(VaadinIcon.PLUS), e -> dialogs.openCreate(TransactionType.CREDIT, this::changed));
        addIncome.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        StreamResource csv = new StreamResource("finsight-transactions.csv", () -> new ByteArrayInputStream(
            csvExportService.transactions(transactionService.findForExport(userId, filter()))));
        Anchor export = new Anchor(csv, "");
        export.getElement().setAttribute("download", true);
        export.add(new Button("Export CSV", Ui.icon(VaadinIcon.DOWNLOAD_ALT)));
        header.actions(export, addExpense, addIncome);

        search.setPlaceholder("Search description or notes");
        search.setPrefixComponent(Ui.icon(VaadinIcon.SEARCH));
        search.setLabel("Search");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addClassName("grow");
        from.setClearButtonVisible(true);
        to.setClearButtonVisible(true);
        from.setWidth("150px");
        to.setWidth("150px");
        List<CategoryResponse> categories = new java.util.ArrayList<>(categoryService.findAllForUser(userId));
        categories.add(0, UNCATEGORIZED);
        category.setItems(categories);
        category.setItemLabelGenerator(CategoryResponse::name);
        category.setClearButtonVisible(true);
        category.setPlaceholder("All");
        type.setLabel("Type");
        type.setItems(TransactionType.DEBIT, TransactionType.CREDIT, TransactionType.REFUND, TransactionType.TRANSFER);
        type.setEmptySelectionAllowed(true);
        type.setEmptySelectionCaption("All types");
        type.setItemLabelGenerator(t -> t == null ? "All types" : Fmt.type(t));
        status.setLabel("Status");
        status.setItems(TransactionReviewStatus.values());
        status.setEmptySelectionAllowed(true);
        status.setEmptySelectionCaption("All statuses");
        status.setItemLabelGenerator(s -> s == null ? "All statuses" : switch (s) {
            case PENDING -> "Pending review";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        });
        Button clear = new Button("Clear", e -> {
            search.clear(); from.clear(); to.clear(); category.clear(); type.clear(); status.clear();
        });
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        search.addValueChangeListener(e -> refresh());
        from.addValueChangeListener(e -> refresh());
        to.addValueChangeListener(e -> refresh());
        category.addValueChangeListener(e -> refresh());
        type.addValueChangeListener(e -> refresh());
        status.addValueChangeListener(e -> refresh());
        Div toolbar = Ui.div("fs-toolbar", search, from, to, category, type, status, clear);

        grid.addClassName("fs-table");
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);
        grid.addColumn(t -> Fmt.dateShort(t.transactionDate())).setHeader("Date").setKey("date")
            .setSortable(true).setSortProperty("date").setWidth("110px").setFlexGrow(0);
        grid.addComponentColumn(t -> new Div(Ui.text("fs-cell-primary", t.description()),
                Ui.text("fs-cell-secondary", Fmt.type(t.type()) + " · " + Fmt.source(t.source()))))
            .setHeader("Description").setKey("description").setSortable(true).setSortProperty("description").setFlexGrow(3);
        grid.addColumn(t -> t.category() == null ? "Uncategorized" : t.category().name()).setHeader("Category").setFlexGrow(1);
        grid.addComponentColumn(Ui::statusBadge).setHeader("Status").setKey("status").setSortable(true)
            .setSortProperty("status").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(Ui::amount).setHeader("Amount").setKey("amount").setSortable(true)
            .setSortProperty("amount").setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END)
            .setAutoWidth(true).setFlexGrow(0);
        grid.setItems(query -> transactionService.search(userId, filter(),
                VaadinSpringDataHelpers.toSpringPageRequest(query)).stream(),
            query -> (int) transactionService.count(userId, filter()));
        grid.addItemClickListener(e -> dialogs.openDetails(e.getItem().id(), this::changed));
        grid.setEmptyStateComponent(new EmptyState(VaadinIcon.SEARCH, "No transactions found",
            "Try changing the filters, add a transaction, or import a statement."));
        grid.setAllRowsVisible(false);
        grid.setHeight("640px");

        add(header, toolbar, new Panel(null).flush().content(grid));
        updateCount();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Map<String, List<String>> params = event.getLocation().getQueryParameters().getParameters();
        first(params, "from").map(LocalDate::parse).ifPresent(from::setValue);
        first(params, "to").map(LocalDate::parse).ifPresent(to::setValue);
        if (first(params, "uncategorized").isPresent()) {
            category.setValue(UNCATEGORIZED);
        }
        first(params, "category").ifPresent(id -> category.getListDataView().getItems()
            .filter(c -> c.id().toString().equals(id)).findFirst().ifPresent(category::setValue));
        first(params, "type").ifPresent(value -> {
            try { type.setValue(TransactionType.valueOf(value)); } catch (IllegalArgumentException ignored) { }
        });
    }

    private static java.util.Optional<String> first(Map<String, List<String>> params, String key) {
        try {
            return java.util.Optional.ofNullable(params.get(key)).filter(l -> !l.isEmpty()).map(l -> l.get(0));
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    TransactionFilter filter() {
        CategoryResponse c = category.getValue();
        return TransactionFilter.builder()
            .query(search.getValue())
            .from(from.getValue())
            .to(to.getValue())
            .uncategorized(c != null && c.id().equals(UNCATEGORIZED.id()))
            .categoryId(c == null || c.id().equals(UNCATEGORIZED.id()) ? null : c.id())
            .types(type.getValue() == null ? null : Set.of(type.getValue()))
            .statuses(status.getValue() == null ? null : Set.of(status.getValue()))
            .build();
    }

    private void refresh() {
        grid.getDataProvider().refreshAll();
        updateCount();
    }

    private void changed() {
        refresh();
        refreshLayout();
    }

    private void updateCount() {
        long count = transactionService.count(currentUser.id(), filter());
        header.setDescription(count + (count == 1 ? " transaction" : " transactions")
            + " · click a row for details, review and editing");
    }
}
