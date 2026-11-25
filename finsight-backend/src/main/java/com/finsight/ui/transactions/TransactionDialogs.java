package com.finsight.ui.transactions;

import com.finsight.api.dto.request.CategorizationRuleRequest;
import com.finsight.api.dto.request.CreateTransactionRequest;
import com.finsight.api.dto.request.UpdateTransactionRequest;
import com.finsight.api.dto.request.ValidTransactionDate;
import com.finsight.api.dto.response.CategoryResponse;
import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.api.exception.BusinessRuleException;
import com.finsight.api.exception.ResourceNotFoundException;
import com.finsight.domain.enums.CategorySource;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.categorization.CategorizationService;
import com.finsight.service.categorization.TextNormalizer;
import com.finsight.service.category.CategoryService;
import com.finsight.service.support.MoneyFormatter;
import com.finsight.service.transaction.TransactionService;
import com.finsight.ui.components.Confirm;
import com.finsight.ui.components.Notice;
import com.finsight.ui.components.SegmentedControl;
import com.finsight.ui.components.Ui;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.finsight.ui.support.Toasts;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Add, edit and detail dialogs for transactions. All business rules are enforced by the services. */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class TransactionDialogs {

    static final List<String> CURRENCIES = List.of("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY", "CHF");

    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final CategorizationService categorizationService;
    private final CurrentUser currentUser;
    private final Validator validator;

    public void openCreate(TransactionType initialType, Runnable onSaved) {
        new FormDialog(null, initialType, onSaved).open();
    }

    public void openEdit(TransactionResponse existing, Runnable onSaved) {
        new FormDialog(existing, existing.type(), onSaved).open();
    }

    public void openDetails(UUID transactionId, Runnable onChanged) {
        UUID userId = currentUser.id();
        TransactionResponse transaction;
        try {
            transaction = transactionService.findById(userId, transactionId);
        } catch (ResourceNotFoundException e) {
            Toasts.error("This transaction no longer exists");
            onChanged.run();
            return;
        }
        new DetailsDialog(transaction, onChanged).open();
    }

    // ===================================================================================================

    private final class FormDialog extends Dialog {

        private final TransactionResponse existing;
        private final Runnable onSaved;
        private final UUID userId = currentUser.id();

        private final SegmentedControl<TransactionType> type = new SegmentedControl<>("Transaction type",
            List.of(TransactionType.DEBIT, TransactionType.CREDIT, TransactionType.REFUND, TransactionType.TRANSFER), Fmt::type);
        private final BigDecimalField amount = new BigDecimalField("Amount");
        private final Span currencySymbol = new Span();
        private final ComboBox<String> currency = new ComboBox<>("Currency");
        private final DatePicker date = new DatePicker("Date");
        private final TextField description = new TextField("Description or merchant");
        private final ComboBox<CategoryResponse> category = new ComboBox<>("Category");
        private final TextArea notes = new TextArea("Notes");
        private final Checkbox countNow = new Checkbox("Count in reports right away");
        private final Checkbox remember = new Checkbox();
        private final TextField keyword = new TextField();
        private final Notice error = new Notice("error", "");
        private final Map<String, HasValidation> fieldsByProperty = new HashMap<>();

        FormDialog(TransactionResponse existing, TransactionType initialType, Runnable onSaved) {
            this.existing = existing;
            this.onSaved = onSaved;
            setHeaderTitle(existing == null ? "Add transaction" : "Edit transaction");
            setCloseOnOutsideClick(false);

            TransactionType startType = initialType == null || initialType == TransactionType.UNKNOWN
                ? TransactionType.DEBIT : initialType;
            type.setValue(startType);
            type.addValueChangeListener(value -> reloadCategories());

            amount.setPrefixComponent(currencySymbol);
            amount.setPlaceholder("0.00");
            amount.setRequiredIndicatorVisible(true);
            amount.getElement().setAttribute("inputmode", "decimal");

            currency.setItems(CURRENCIES);
            currency.setAllowCustomValue(true);
            currency.addCustomValueSetListener(event -> {
                String code = event.getDetail() == null ? "" : event.getDetail().trim().toUpperCase();
                if (code.matches("[A-Z]{3}")) {
                    List<String> items = new ArrayList<>(CURRENCIES);
                    if (!items.contains(code)) {
                        items.add(code);
                    }
                    currency.setItems(items);
                    currency.setValue(code);
                }
            });
            currency.addValueChangeListener(event -> currencySymbol.setText(MoneyFormatter.symbol(event.getValue())));

            date.setRequiredIndicatorVisible(true);
            DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
            i18n.setDateFormats("dd/MM/yyyy", "d/M/yyyy", "d/M/yy", "yyyy-MM-dd");
            i18n.setFirstDayOfWeek(1);
            date.setI18n(i18n);
            date.setMin(ValidTransactionDate.Rules.EARLIEST);
            date.setMax(ValidTransactionDate.Rules.latest());

            description.setRequiredIndicatorVisible(true);
            description.setMaxLength(500);
            description.setPlaceholder("e.g. BigBasket groceries");

            category.setItemLabelGenerator(CategoryResponse::name);
            category.setPlaceholder("Uncategorized");
            category.setClearButtonVisible(true);

            notes.setMaxLength(1000);
            notes.setPlaceholder("Optional — only you can see this");
            notes.setMinHeight("72px");

            countNow.setValue(true);
            countNow.setHelperText("Untick to send it to the review queue first");

            keyword.setPlaceholder("keyword");
            keyword.setWidth("160px");
            keyword.setMaxLength(100);
            keyword.addThemeName("small");

            error.setVisible(false);

            fieldsByProperty.put("amount", amount);
            fieldsByProperty.put("currency", currency);
            fieldsByProperty.put("transactionDate", date);
            fieldsByProperty.put("description", description);
            fieldsByProperty.put("notes", notes);

            Div typeRow = Ui.div("full", type);
            typeRow.getStyle().set("margin-bottom", "8px");
            Div form = Ui.div("fs-form", typeRow, amount, currency, date, category);
            description.addClassName("full");
            notes.addClassName("full");
            form.add(description, notes);
            amount.setWidthFull();
            currency.setWidthFull();
            date.setWidthFull();
            category.setWidthFull();
            description.setWidthFull();
            notes.setWidthFull();

            if (existing == null) {
                currency.setValue(currentUser.currency());
                date.setValue(LocalDate.now());
                countNow.addClassName("full");
                form.add(countNow);
            } else {
                amount.setValue(existing.amount());
                if (!CURRENCIES.contains(existing.currency())) {
                    List<String> items = new ArrayList<>(CURRENCIES);
                    items.add(existing.currency());
                    currency.setItems(items);
                }
                currency.setValue(existing.currency());
                date.setValue(existing.transactionDate());
                description.setValue(Objects.requireNonNullElse(existing.description(), ""));
                notes.setValue(Objects.requireNonNullElse(existing.notes(), ""));
                if (existing.source() != IngestionSource.MANUAL) {
                    Div rememberRow = Ui.div("full");
                    rememberRow.getStyle().set("display", "flex").set("align-items", "center").set("gap", "8px")
                        .set("flex-wrap", "wrap").set("font-size", "0.8125rem").set("color", "var(--fs-ink-2)");
                    keyword.setValue(TextNormalizer.suggestKeyword(existing.description()));
                    remember.setLabel("Always use this category for descriptions containing");
                    rememberRow.add(remember, keyword);
                    keyword.setEnabled(false);
                    remember.addValueChangeListener(event -> keyword.setEnabled(event.getValue()));
                    form.add(rememberRow);
                }
            }
            currencySymbol.setText(MoneyFormatter.symbol(currency.getValue()));
            reloadCategories();
            if (existing != null && existing.category() != null) {
                category.getListDataView().getItems()
                    .filter(c -> c.id().equals(existing.category().id())).findFirst()
                    .ifPresent(category::setValue);
            }

            add(error, form);

            Button cancel = new Button("Cancel", event -> close());
            Button save = new Button(existing == null ? "Add transaction" : "Save changes", event -> save());
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            // Enter submits from single-line fields; the notes area keeps Enter for new lines.
            save.addClickShortcut(Key.ENTER).listenOn(amount, date, description, category, currency);
            getFooter().add(cancel, save);
            addOpenedChangeListener(event -> {
                if (event.isOpened()) {
                    amount.focus();
                }
            });
        }

        private void reloadCategories() {
            CategoryResponse selected = category.getValue();
            List<CategoryResponse> items = categoryService.findAllForUser(userId, type.getValue());
            category.setItems(items);
            if (selected != null) {
                items.stream().filter(c -> c.id().equals(selected.id())).findFirst().ifPresent(category::setValue);
            }
        }

        private void save() {
            fieldsByProperty.values().forEach(field -> field.setInvalid(false));
            error.setVisible(false);
            if (amount.isInvalid() && amount.getValue() == null) {
                showFieldError("amount", "Enter an amount like 1250.50");
                return;
            }
            UUID categoryId = category.getValue() == null ? null : category.getValue().id();
            String currencyCode = currency.getValue();
            try {
                if (existing == null) {
                    CreateTransactionRequest request = new CreateTransactionRequest(amount.getValue(), currencyCode,
                        date.getValue(), description.getValue(), type.getValue(), categoryId, notes.getValue(),
                        countNow.getValue());
                    if (!validate(validator.validate(request))) {
                        return;
                    }
                    transactionService.create(userId, request);
                    Toasts.success(countNow.getValue() ? "Transaction added" : "Transaction added to the review queue");
                } else {
                    UpdateTransactionRequest request = new UpdateTransactionRequest(amount.getValue(), currencyCode,
                        date.getValue(), description.getValue(), type.getValue(), categoryId, notes.getValue());
                    if (!validate(validator.validate(request))) {
                        return;
                    }
                    transactionService.update(userId, existing.id(), request);
                    int recategorized = saveRuleIfRequested(categoryId);
                    Toasts.success(recategorized > 0
                        ? "Saved. " + recategorized + " other pending transaction(s) were re-categorized."
                        : "Changes saved");
                }
                close();
                onSaved.run();
            } catch (BusinessRuleException | ResourceNotFoundException e) {
                error.setMessage(e.getMessage());
                error.setVisible(true);
            }
        }

        private int saveRuleIfRequested(UUID categoryId) {
            if (!remember.getValue() || categoryId == null) {
                return 0;
            }
            String word = keyword.getValue() == null ? "" : keyword.getValue().trim();
            if (word.length() < 3) {
                Toasts.info("The rule wasn't saved: the keyword needs at least three characters");
                return 0;
            }
            return categorizationService.saveRule(userId, new CategorizationRuleRequest(word, categoryId, true));
        }

        private <T> boolean validate(Set<ConstraintViolation<T>> violations) {
            if (violations.isEmpty()) {
                return true;
            }
            for (ConstraintViolation<T> violation : violations) {
                showFieldError(violation.getPropertyPath().toString(), violation.getMessage());
            }
            return false;
        }

        private void showFieldError(String property, String message) {
            HasValidation field = fieldsByProperty.get(property);
            if (field != null) {
                field.setErrorMessage(message);
                field.setInvalid(true);
            } else {
                error.setMessage(message);
                error.setVisible(true);
            }
        }
    }

    // ===================================================================================================

    private final class DetailsDialog extends Dialog {

        private final Runnable onChanged;
        private TransactionResponse transaction;

        DetailsDialog(TransactionResponse transaction, Runnable onChanged) {
            this.transaction = transaction;
            this.onChanged = onChanged;
            setHeaderTitle("Transaction");
            Button close = new Button(Ui.icon(VaadinIcon.CLOSE_SMALL), event -> close());
            close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
            Ui.label(close, "Close");
            getHeader().add(close);
            render();
        }

        private void render() {
            removeAll();
            getFooter().removeAll();
            TransactionResponse t = transaction;

            Div badges = Ui.div("fs-detail-badges", Ui.statusBadge(t), Ui.badge(Fmt.type(t.type()), "plain"));
            if (t.duplicate()) {
                badges.add(Ui.badge("Possible duplicate", "info"));
            }
            Div amountDiv = Ui.text("fs-detail-amount", Fmt.signed(t));
            if (Fmt.isMoneyIn(t.type())) {
                amountDiv.addClassName("fs-pos");
            }
            Div head = Ui.div("fs-detail-head", badges, amountDiv, Ui.text("fs-strong", t.description()));
            add(head);

            if (t.duplicate() && t.duplicateOfId() != null) {
                describeOriginal(t.duplicateOfId()).ifPresent(this::add);
            }
            if (t.reviewStatus() == TransactionReviewStatus.PENDING) {
                Notice pending = new Notice("warning",
                    "Pending transactions aren't counted in your dashboard, analytics or reports until you approve them.");
                pending.getStyle().set("margin-bottom", "16px");
                add(pending);
            }

            com.vaadin.flow.dom.Element dl = new com.vaadin.flow.dom.Element("dl");
            dl.getClassList().add("fs-dl");
            addTerm(dl, "Date", Fmt.date(t.transactionDate()));
            String categoryText = t.category() == null ? "Uncategorized" : t.category().name();
            if (t.categorySource() == CategorySource.RULE) {
                categoryText += " · suggested automatically";
            } else if (t.categorySource() == CategorySource.PERSONAL_RULE) {
                categoryText += " · from your rule";
            }
            addTerm(dl, "Category", categoryText);
            addTerm(dl, "Currency", t.currency());
            addTerm(dl, "Source", Fmt.source(t.source()));
            addTerm(dl, "In reports", t.countsInReports() ? "Yes" : "No");
            if (t.notes() != null && !t.notes().isBlank()) {
                addTerm(dl, "Notes", t.notes());
            }
            addTerm(dl, "Added", Fmt.dateTime(t.createdAt()));
            Div details = new Div();
            details.getElement().appendChild(dl);
            add(details);

            Button delete = new Button("Delete", Ui.icon(VaadinIcon.TRASH), event -> confirmDelete());
            delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            delete.getStyle().set("margin-inline-end", "auto");
            Button edit = new Button("Edit", event -> {
                close();
                openEdit(transaction, onChanged);
            });
            getFooter().add(delete, edit);

            switch (t.reviewStatus()) {
                case PENDING -> {
                    getFooter().add(action("Reject", false, () -> review(TransactionReviewStatus.REJECTED, null)),
                        action("Approve", true, () -> review(TransactionReviewStatus.APPROVED, true)));
                }
                case APPROVED -> {
                    getFooter().add(action("Move to review", false, () -> review(TransactionReviewStatus.PENDING, null)));
                    getFooter().add(t.includedInReports()
                        ? action("Exclude from reports", false, () -> include(false))
                        : action("Include in reports", true, () -> include(true)));
                }
                case REJECTED -> getFooter().add(
                    action("Move to review", false, () -> review(TransactionReviewStatus.PENDING, null)),
                    action("Approve", true, () -> review(TransactionReviewStatus.APPROVED, true)));
            }
        }

        private java.util.Optional<Component> describeOriginal(UUID originalId) {
            try {
                TransactionResponse original = transactionService.findById(currentUser.id(), originalId);
                return java.util.Optional.of(new Notice("info", "This looks like a duplicate of “"
                    + original.description() + "” on " + Fmt.date(original.transactionDate()) + " ("
                    + Fmt.money(original.amount(), original.currency()) + ", " + Fmt.status(original.reviewStatus(),
                    original.includedInReports()).toLowerCase() + "). Reject it if it's the same payment."));
            } catch (ResourceNotFoundException e) {
                return java.util.Optional.empty();
            }
        }

        private void addTerm(com.vaadin.flow.dom.Element dl, String term, String value) {
            com.vaadin.flow.dom.Element dt = new com.vaadin.flow.dom.Element("dt");
            dt.setText(term);
            com.vaadin.flow.dom.Element dd = new com.vaadin.flow.dom.Element("dd");
            dd.setText(value);
            dl.appendChild(dt, dd);
        }

        private Button action(String label, boolean primary, Runnable runnable) {
            Button button = new Button(label, event -> runnable.run());
            if (primary) {
                button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
            return button;
        }

        private void review(TransactionReviewStatus status, Boolean include) {
            transaction = transactionService.updateReviewStatus(currentUser.id(), transaction.id(), status, include);
            Toasts.success(switch (status) {
                case APPROVED -> "Approved and counted in reports";
                case REJECTED -> "Rejected";
                case PENDING -> "Moved back to review";
            });
            onChanged.run();
            render();
        }

        private void include(boolean included) {
            try {
                transaction = transactionService.updateInclusion(currentUser.id(), transaction.id(), included);
                Toasts.success(included ? "Included in reports" : "Excluded from reports");
                onChanged.run();
                render();
            } catch (BusinessRuleException e) {
                Toasts.error(e.getMessage());
            }
        }

        private void confirmDelete() {
            Confirm.ask("Delete transaction?", "“" + transaction.description()
                    + "” will be removed from your history and from all figures.", "Delete", true, () -> {
                    transactionService.delete(currentUser.id(), transaction.id());
                    Toasts.success("Transaction deleted");
                    close();
                    onChanged.run();
                });
        }
    }

    /** Ids of the given transactions, preserving order. */
    static List<UUID> ids(Set<TransactionResponse> items) {
        return new ArrayList<>(new LinkedHashSet<>(items.stream().map(TransactionResponse::id).toList()));
    }
}
