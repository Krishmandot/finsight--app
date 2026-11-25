package com.finsight.ui.auth;

import com.finsight.api.dto.request.RegisterRequest;
import com.finsight.api.exception.BusinessRuleException;
import com.finsight.service.auth.AuthService;
import com.finsight.ui.components.Notice;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;

@Route("register")
@PageTitle("Create account | FinSight")
@AnonymousAllowed
public class RegisterView extends AuthLayout {

    private final transient AuthService authService;
    private final transient Validator validator;
    private final TextField displayName = new TextField("Name");
    private final EmailField email = new EmailField("Email");
    private final PasswordField password = new PasswordField("Password");
    private final PasswordField confirm = new PasswordField("Confirm password");
    private final Notice error = new Notice("error", "");

    public RegisterView(AuthService authService, Validator validator) {
        this.authService = authService;
        this.validator = validator;
        displayName.setMaxLength(100);
        displayName.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.NAME);
        email.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.EMAIL);
        password.setHelperText("At least 8 characters");
        password.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.NEW_PASSWORD);
        confirm.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.NEW_PASSWORD);
        error.setVisible(false);
        error.addClassName("fs-auth-error");

        Button create = new Button("Create account", e -> register());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickShortcut(Key.ENTER);

        H1 title = new H1("Create your account");
        Paragraph lead = new Paragraph("It takes a minute. You can import a statement right after.");
        lead.setClassName("lead");
        Paragraph switchLine = new Paragraph("Already have an account? ");
        switchLine.setClassName("fs-auth-switch");
        RouterLink signIn = new RouterLink("Sign in", LoginView.class);
        signIn.setClassName("fs-link");
        switchLine.add(signIn);
        card.add(title, lead, error, displayName, email, password, confirm, create, switchLine);
    }

    private void register() {
        Map<String, HasValidation> fields = Map.of("displayName", displayName, "email", email, "password", password);
        fields.values().forEach(f -> f.setInvalid(false));
        confirm.setInvalid(false);
        error.setVisible(false);
        RegisterRequest request = new RegisterRequest(AuthService.normalizeEmail(email.getValue()), password.getValue(),
            displayName.getValue() == null ? "" : displayName.getValue().trim());
        var violations = validator.validate(request);
        for (ConstraintViolation<RegisterRequest> v : violations) {
            HasValidation field = fields.get(v.getPropertyPath().toString());
            if (field != null) {
                field.setErrorMessage(v.getMessage());
                field.setInvalid(true);
            }
        }
        if (!password.getValue().equals(confirm.getValue())) {
            confirm.setErrorMessage("Passwords don't match");
            confirm.setInvalid(true);
            return;
        }
        if (!violations.isEmpty()) {
            return;
        }
        try {
            authService.registerAccount(request);
            getUI().ifPresent(ui -> ui.navigate(LoginView.class, QueryParameters.of("registered", "1")));
        } catch (BusinessRuleException e) {
            error.setMessage(e.getMessage());
            error.setVisible(true);
        }
    }
}
