package com.finsight.ui.views;

import com.finsight.api.exception.BusinessRuleException;
import com.finsight.domain.entity.AppUser;
import com.finsight.service.auth.AuthService;
import com.finsight.service.report.CsvExportService;
import com.finsight.service.transaction.TransactionFilter;
import com.finsight.service.transaction.TransactionService;
import com.finsight.ui.components.PageHeader;
import com.finsight.ui.components.Panel;
import com.finsight.ui.components.SegmentedControl;
import com.finsight.ui.components.Ui;
import com.finsight.ui.layout.MainLayout;
import com.finsight.ui.support.ClientScripts;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Toasts;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings | FinSight")
@PermitAll
@SuppressWarnings("deprecation")
public class SettingsView extends AppPage {

    public SettingsView(AuthService authService, CurrentUser currentUser, TransactionService transactionService,
                        CsvExportService csvExportService) {
        AppUser user = currentUser.profile();
        UUID userId = user.getId();

        TextField name = new TextField("Name");
        name.setValue(user.getDisplayName());
        name.setMaxLength(100);
        EmailField email = new EmailField("Email");
        email.setValue(user.getEmail());
        email.setReadOnly(true);
        ComboBox<String> currency = new ComboBox<>("Reporting currency");
        currency.setItems(List.of("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY", "CHF"));
        currency.setValue(user.getPreferredCurrency());
        currency.setHelperText("Dashboard, analytics and reports use this currency. Others are listed separately, never converted.");
        Button saveProfile = new Button("Save profile", e -> {
            try {
                authService.updateProfile(userId, name.getValue(), currency.getValue());
                Toasts.success("Profile saved");
                getUI().ifPresent(ui -> ui.getPage().reload());
            } catch (BusinessRuleException ex) {
                Toasts.error(ex.getMessage());
            }
        });
        saveProfile.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        PasswordField current = new PasswordField("Current password");
        PasswordField next = new PasswordField("New password");
        next.setHelperText("At least 8 characters");
        PasswordField confirm = new PasswordField("Confirm new password");
        Button changePassword = new Button("Change password", e -> {
            if (!next.getValue().equals(confirm.getValue())) {
                confirm.setErrorMessage("Passwords don't match");
                confirm.setInvalid(true);
                return;
            }
            try {
                authService.changePassword(userId, current.getValue(), next.getValue());
                current.clear(); next.clear(); confirm.clear();
                confirm.setInvalid(false);
                Toasts.success("Password changed");
            } catch (BusinessRuleException ex) {
                Toasts.error(ex.getMessage());
            }
        });

        SegmentedControl<String> theme = new SegmentedControl<>("Appearance", List.of("light", "dark", "system"),
            v -> v.substring(0, 1).toUpperCase() + v.substring(1));
        theme.setValue("system");
        getElement().executeJs("return localStorage.getItem($0) || 'system'", ClientScripts.THEME_KEY)
            .then(String.class, theme::setValue);
        theme.addValueChangeListener(v -> getUI().ifPresent(ui -> ClientScripts.setTheme(ui, v)));

        StreamResource csv = new StreamResource("finsight-all-transactions.csv", () -> new ByteArrayInputStream(
            csvExportService.transactions(transactionService.findForExport(userId, TransactionFilter.none()))));
        Anchor export = new Anchor(csv, "");
        export.getElement().setAttribute("download", true);
        export.add(new Button("Download all transactions (CSV)", Ui.icon(VaadinIcon.DOWNLOAD_ALT)));

        com.vaadin.flow.dom.Element list = new com.vaadin.flow.dom.Element("ul");
        list.getClassList().add("fs-privacy-list");
        for (String item : List.of(
            "Your data is visible only to your account; every query is scoped to you.",
            "Long numbers such as account and card numbers are masked (••••1234) before storage.",
            "Uploaded statement files are processed in memory and never stored.",
            "Passwords are hashed with BCrypt; repeated failed sign-ins are temporarily blocked.",
            "Activity logs record what happened (e.g. “transaction updated”), never amounts or descriptions.")) {
            com.vaadin.flow.dom.Element li = new com.vaadin.flow.dom.Element("li");
            li.setText(item);
            list.appendChild(li);
        }
        Div privacy = new Div();
        privacy.getElement().appendChild(list);

        Panel panel = new Panel(null).content(
            section("Profile", "How you appear in FinSight and the currency figures are reported in.",
                fields(name, email, currency, actions(saveProfile))),
            section("Password", "Changing it also signs out API sessions.", fields(current, next, confirm, actions(changePassword))),
            section("Appearance", "Stored in this browser.", fields(theme)),
            section("Privacy & data", "What FinSight stores and how to take it with you.", fields(privacy, actions(export))));
        add(new PageHeader("Settings", null), panel);
    }

    private static Component section(String title, String description, Component body) {
        return Ui.div("fs-settings-section", new Div(new H3(title), new Paragraph(description)), body);
    }

    private static Div fields(Component... components) {
        return Ui.div("fs-settings-fields", components);
    }

    private static Div actions(Component... components) {
        return Ui.div("fs-settings-actions", components);
    }
}
