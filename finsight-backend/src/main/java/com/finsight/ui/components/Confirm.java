package com.finsight.ui.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;

/** A small modal confirmation. Destructive actions use the danger style. */
public final class Confirm {

    private Confirm() {
    }

    public static Dialog ask(String title, String message, String confirmText, boolean danger, Runnable onConfirm) {
        return ask(title, message, null, confirmText, danger, onConfirm);
    }

    public static Dialog ask(String title, String message, Component extra, String confirmText, boolean danger,
                             Runnable onConfirm) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        Paragraph text = new Paragraph(message);
        text.getStyle().set("margin", "0").set("color", "var(--fs-ink-2)").set("max-width", "420px");
        dialog.add(text);
        if (extra != null) {
            dialog.add(extra);
        }
        Button cancel = new Button("Cancel", event -> dialog.close());
        Button confirm = new Button(confirmText, event -> {
            dialog.close();
            onConfirm.run();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (danger) {
            confirm.addThemeVariants(ButtonVariant.LUMO_ERROR);
        }
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
        confirm.focus();
        return dialog;
    }
}
