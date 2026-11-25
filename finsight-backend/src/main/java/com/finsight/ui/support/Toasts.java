package com.finsight.ui.support;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/** Consistent, short-lived feedback messages. Never include financial values in error toasts. */
public final class Toasts {

    private Toasts() {
    }

    public static void success(String message) {
        show(message, NotificationVariant.LUMO_SUCCESS, 3000);
    }

    public static void info(String message) {
        show(message, NotificationVariant.LUMO_CONTRAST, 3000);
    }

    public static void error(String message) {
        show(message, NotificationVariant.LUMO_ERROR, 6000);
    }

    private static void show(String message, NotificationVariant variant, int duration) {
        Notification notification = new Notification(message, duration, Notification.Position.BOTTOM_END);
        notification.addThemeVariants(variant);
        notification.open();
    }
}
