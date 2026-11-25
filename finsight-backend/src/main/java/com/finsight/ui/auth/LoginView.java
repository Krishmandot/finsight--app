package com.finsight.ui.auth;

import com.finsight.ui.components.Notice;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/** Session sign-in for the web app; the form posts to Spring Security's /login endpoint. */
@Route("login")
@PageTitle("Sign in | FinSight")
@AnonymousAllowed
public class LoginView extends AuthLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();
    private final Notice registered = new Notice("success", "Account created. Sign in to continue.");

    public LoginView() {
        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("");
        i18n.getForm().setUsername("Email");
        i18n.getForm().setSubmit("Sign in");
        i18n.getErrorMessage().setTitle("Couldn't sign you in");
        i18n.getErrorMessage().setMessage("Check your email and password. After several failed attempts sign-in is paused for 15 minutes.");
        loginForm.setI18n(i18n);
        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);
        loginForm.getElement().getStyle().set("--lumo-space-l", "0").set("margin", "0 -1rem");
        registered.setVisible(false);

        H1 title = new H1("Welcome back");
        Paragraph lead = new Paragraph("Sign in to your FinSight account.");
        lead.setClassName("lead");
        Paragraph switchLine = new Paragraph("New to FinSight? ");
        switchLine.setClassName("fs-auth-switch");
        RouterLink register = new RouterLink("Create an account", RegisterView.class);
        register.setClassName("fs-link");
        switchLine.add(register);
        card.add(title, lead, registered, loginForm, switchLine);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var params = event.getLocation().getQueryParameters().getParameters();
        loginForm.setError(params.containsKey("error"));
        registered.setVisible(params.containsKey("registered"));
    }
}
