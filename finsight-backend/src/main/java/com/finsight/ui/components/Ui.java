package com.finsight.ui.components;

import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.ui.support.Fmt;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/** Tiny builders for the design system's HTML building blocks. */
public final class Ui {

    private Ui() {
    }

    public static Div div(String className, Component... children) {
        Div div = new Div(children);
        if (className != null && !className.isBlank()) {
            div.setClassName(className);
        }
        return div;
    }

    public static Div text(String className, String text) {
        Div div = new Div();
        div.setText(text);
        div.setClassName(className);
        return div;
    }

    public static Span span(String className, String text) {
        Span span = new Span(text);
        if (className != null && !className.isBlank()) {
            span.setClassName(className);
        }
        return span;
    }

    public static Icon icon(VaadinIcon vaadinIcon) {
        Icon icon = vaadinIcon.create();
        icon.getElement().setAttribute("aria-hidden", "true");
        return icon;
    }

    /** Variants: pending, approved, rejected, info, accent or null (neutral). */
    public static Span badge(String text, String variant) {
        return span("fs-badge" + (variant == null ? "" : " " + variant), text);
    }

    public static Span statusBadge(TransactionReviewStatus status, boolean included) {
        String variant = switch (status) {
            case PENDING -> "pending";
            case REJECTED -> "rejected";
            case APPROVED -> included ? "approved" : null;
        };
        return badge(Fmt.status(status, included), variant);
    }

    public static Span statusBadge(TransactionResponse t) {
        return statusBadge(t.reviewStatus(), t.includedInReports());
    }

    /** Signed, colour-coded amount for tables and lists. Rejected rows are struck through. */
    public static Span amount(TransactionResponse t) {
        String variant = t.reviewStatus() == TransactionReviewStatus.REJECTED ? " muted"
            : Fmt.isMoneyIn(t.type()) ? " in" : "";
        return span("fs-amount" + variant, Fmt.signed(t));
    }

    /** Small text that tells assistive technology what an icon-only control does. */
    public static void label(Component component, String label) {
        component.getElement().setAttribute("aria-label", label);
        component.getElement().setAttribute("title", label);
    }
}
