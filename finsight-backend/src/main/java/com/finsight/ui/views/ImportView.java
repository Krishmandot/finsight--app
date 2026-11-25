package com.finsight.ui.views;

import com.finsight.api.exception.IngestionException;
import com.finsight.service.importing.DateDetector;
import com.finsight.service.importing.DuplicateDetector;
import com.finsight.service.importing.ImportBatchSummary;
import com.finsight.service.importing.ImportOptions;
import com.finsight.service.importing.ImportPreview;
import com.finsight.service.importing.ImportResult;
import com.finsight.service.importing.PasswordRequiredException;
import com.finsight.service.importing.StatementImportService;
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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.io.IOException;
import java.util.List;

/** Upload → preview (nothing saved) → import as pending transactions → review. */
@Route(value = "import", layout = MainLayout.class)
@PageTitle("Import | FinSight")
@PermitAll
@SuppressWarnings("deprecation")
public class ImportView extends AppPage {

    private final transient StatementImportService importService;
    private final transient CurrentUser currentUser;
    private final Div previewArea = new Div();
    private final Panel history = new Panel("Recent imports").flush();
    private final Select<String> dateFormat = new Select<>();
    private final Select<ImportOptions.AmountConvention> convention = new Select<>();
    private final PasswordField pdfPassword = new PasswordField("PDF password");
    private final Checkbox skipDuplicates = new Checkbox("Skip rows that exactly match existing transactions", true);
    private byte[] fileBytes;
    private String fileName;

    public ImportView(StatementImportService importService, CurrentUser currentUser) {
        this.importService = importService;
        this.currentUser = currentUser;

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.addClassName("fs-upload");
        upload.setAcceptedFileTypes(".csv", ".pdf", "text/csv", "application/pdf");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(StatementImportService.MAX_FILE_BYTES);
        upload.setDropLabel(Ui.span("fs-muted", "Drop a CSV or PDF bank statement here (max 5 MB)"));
        upload.addSucceededListener(event -> {
            try {
                fileBytes = buffer.getInputStream().readAllBytes();
                fileName = event.getFileName();
                pdfPassword.clear();
                dateFormat.setValue("");
                convention.clear();
                refreshPreview();
            } catch (IOException e) {
                Toasts.error("The file could not be read");
            }
            upload.clearFileList();
        });
        upload.addFileRejectedListener(event -> Toasts.error(event.getErrorMessage()));

        dateFormat.setLabel("Date format");
        java.util.List<String> formats = new java.util.ArrayList<>();
        formats.add("");
        formats.addAll(DateDetector.PATTERNS);
        dateFormat.setItems(formats);
        dateFormat.setItemLabelGenerator(p -> p == null || p.isEmpty() ? "Detect automatically" : p + "  (" + DateDetector.example(p) + ")");
        dateFormat.setValue("");
        convention.setLabel("Single amount column");
        convention.setItems(ImportOptions.AmountConvention.values());
        convention.setEmptySelectionAllowed(true);
        convention.setEmptySelectionCaption("Negative = money out");
        convention.setItemLabelGenerator(c -> c == null ? "Negative = money out"
            : c == ImportOptions.AmountConvention.NEGATIVE_IS_EXPENSE ? "Negative = money out" : "Positive = money out");
        pdfPassword.setVisible(false);
        dateFormat.addValueChangeListener(e -> { if (e.isFromClient()) { refreshPreview(); } });
        convention.addValueChangeListener(e -> { if (e.isFromClient()) { refreshPreview(); } });
        pdfPassword.addValueChangeListener(e -> { if (e.isFromClient()) { refreshPreview(); } });

        Panel uploadPanel = new Panel("Upload a statement",
            "CSV exports from most banks and cards work. Text-based PDF statements are supported on a best-effort basis.")
            .content(upload, new Paragraph(), new Notice("neutral",
                "The file itself is never stored. Rows are imported as pending transactions with account numbers masked, "
                    + "and nothing counts in your figures until you approve it."));
        add(new PageHeader("Import", "Bring in transactions from a bank or card statement"), uploadPanel, previewArea, history);
        renderHistory();
    }

    private ImportOptions options() {
        String pattern = dateFormat.getValue();
        return ImportOptions.defaults(currentUser.currency())
            .withDateFormat(pattern == null || pattern.isEmpty() ? null : pattern)
            .withAmountConvention(convention.getValue())
            .withPdfPassword(pdfPassword.getValue())
            .withSkipExactDuplicates(skipDuplicates.getValue());
    }

    private void refreshPreview() {
        previewArea.removeAll();
        if (fileBytes == null) {
            return;
        }
        Panel panel = new Panel("Preview: " + fileName);
        Div options = Ui.div("fs-toolbar", dateFormat, convention, pdfPassword);
        try {
            ImportPreview preview = importService.preview(currentUser.id(), fileName, fileBytes, options());
            pdfPassword.setVisible(pdfPassword.isVisible() || !pdfPassword.isEmpty());
            panel.content(options);
            preview.statement().warnings().forEach(w -> panel.content(spaced(new Notice("warning", w))));
            Div stats = Ui.div("fs-stat-row",
                stat(preview.rows().size(), "rows ready"),
                stat(preview.exactDuplicates(), "exact duplicates"),
                stat(preview.possibleDuplicates(), "possible duplicates"),
                stat(preview.categorized(), "auto-categorized"),
                stat(preview.statement().skipped().size(), "rows skipped"));
            panel.content(spaced(stats));

            Grid<ImportPreview.PreviewRow> grid = new Grid<>();
            grid.addClassName("fs-table");
            grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT);
            grid.addColumn(r -> Fmt.date(r.row().date())).setHeader("Date").setWidth("120px").setFlexGrow(0);
            grid.addColumn(r -> r.row().description()).setHeader("Description").setFlexGrow(3);
            grid.addColumn(r -> Fmt.type(r.row().type())).setHeader("Type").setAutoWidth(true).setFlexGrow(0);
            grid.addColumn(r -> r.suggestedCategory() == null ? "—" : r.suggestedCategory().name()).setHeader("Suggested category");
            grid.addComponentColumn(r -> r.duplicate().kind() == DuplicateDetector.Kind.EXACT ? Ui.badge("Duplicate", "rejected")
                : r.duplicate().kind() == DuplicateDetector.Kind.POSSIBLE ? Ui.badge("Possible duplicate", "info") : new Div())
                .setHeader("Checks").setAutoWidth(true).setFlexGrow(0);
            grid.addColumn(r -> Fmt.money(r.row().amount(), r.row().currency())).setHeader("Amount")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true).setFlexGrow(0);
            grid.setItems(preview.rows());
            grid.setHeight("360px");
            panel.content(grid);

            if (!preview.statement().skipped().isEmpty()) {
                String sample = preview.statement().skipped().stream().limit(8)
                    .map(s -> "line " + s.lineNumber() + ": " + s.reason()).reduce((a, b) -> a + "; " + b).orElse("");
                panel.content(spaced(new Notice("neutral", "Skipped " + preview.statement().skipped().size()
                    + " row(s) — " + sample + (preview.statement().skipped().size() > 8 ? "; …" : ""))));
            }
            Button importButton = new Button("Import " + preview.rows().size() + " rows for review", e -> commit());
            importButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            importButton.setEnabled(!preview.rows().isEmpty());
            Button cancel = new Button("Cancel", e -> { fileBytes = null; previewArea.removeAll(); });
            panel.footer(skipDuplicates, Ui.div("fs-page-actions", cancel, importButton));
        } catch (PasswordRequiredException e) {
            pdfPassword.setVisible(true);
            panel.content(options, spaced(new Notice("warning", e.getMessage())));
        } catch (IngestionException e) {
            panel.content(options, spaced(new Notice("error", e.getMessage())));
        }
        previewArea.add(panel);
    }

    private void commit() {
        try {
            ImportResult result = importService.commit(currentUser.id(), fileName, fileBytes, options());
            fileBytes = null;
            previewArea.removeAll();
            renderHistory();
            refreshLayout();
            Toasts.success(result.imported() + " transactions imported for review"
                + (result.duplicatesSkipped() > 0 ? ", " + result.duplicatesSkipped() + " duplicates skipped" : ""));
            if (result.imported() > 0) {
                getUI().ifPresent(ui -> ui.navigate(ReviewView.class,
                    QueryParameters.of("batch", result.batchId().toString())));
            }
        } catch (IngestionException e) {
            Toasts.error(e.getMessage());
        }
    }

    private void renderHistory() {
        List<ImportBatchSummary> batches = importService.recentBatches(currentUser.id());
        history.setVisible(!batches.isEmpty());
        Div list = Ui.div("fs-list");
        for (ImportBatchSummary b : batches) {
            Div left = new Div(Ui.text("fs-cell-primary", b.fileName()), Ui.text("fs-cell-secondary",
                Fmt.dateTime(b.createdAt()) + " · " + b.importedRows() + " imported · " + b.duplicateRows()
                    + " duplicates · " + b.skippedRows() + " skipped"));
            left.getStyle().set("min-width", "0");
            Div right = Ui.div("fs-list-end");
            if (b.pendingRows() > 0) {
                Button review = new Button(b.pendingRows() + " to review", e -> getUI().ifPresent(ui ->
                    ui.navigate(ReviewView.class, QueryParameters.of("batch", b.id().toString()))));
                review.addThemeVariants(ButtonVariant.LUMO_SMALL);
                right.add(review);
            } else {
                right.add(Ui.badge("Reviewed", "approved"));
            }
            list.add(Ui.div("fs-list-row", left, right));
        }
        history.setContent(list);
    }

    private static Div stat(long value, String label) {
        Div div = new Div();
        com.vaadin.flow.dom.Element b = new com.vaadin.flow.dom.Element("b");
        b.setText(String.valueOf(value));
        div.getElement().appendChild(b);
        div.add(Ui.span("", label));
        return div;
    }

    private static Div spaced(com.vaadin.flow.component.Component c) {
        Div d = new Div(c);
        d.getStyle().set("margin-bottom", "12px");
        return d;
    }
}
