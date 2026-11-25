package com.finsight.ui.layout;

import com.finsight.domain.entity.AppUser;
import com.finsight.service.transaction.TransactionService;
import com.finsight.ui.components.Ui;
import com.finsight.ui.support.ClientScripts;
import com.finsight.ui.support.CurrentUser;
import com.finsight.ui.support.Fmt;
import com.finsight.ui.views.AnalyticsView;
import com.finsight.ui.views.CategoriesView;
import com.finsight.ui.views.DashboardView;
import com.finsight.ui.views.ImportView;
import com.finsight.ui.views.ReportsView;
import com.finsight.ui.views.ReviewView;
import com.finsight.ui.views.SettingsView;
import com.finsight.ui.views.TransactionsView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

@PermitAll
@StyleSheet("context://styles/finsight.css")
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    static final String BRAND_MARK = "<span class=\"fs-brand-mark\" aria-hidden=\"true\"><svg viewBox=\"0 0 14 14\">"
        + "<path d=\"M2 11.5 L5.2 7.6 L7.8 9.6 L12 3.5\" fill=\"none\" stroke=\"var(--fs-surface, #fff)\" "
        + "stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg></span>";

    private final transient CurrentUser currentUser;
    private final transient TransactionService transactionService;
    private final Span reviewCount = new Span();

    public MainLayout(CurrentUser currentUser, TransactionService transactionService) {
        this.currentUser = currentUser;
        this.transactionService = transactionService;
        setPrimarySection(Section.DRAWER);
        addToDrawer(createDrawer());
        addToNavbar(createMobileBar());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        ClientScripts.applyTheme(attachEvent.getUI());
        ClientScripts.installTooltips(attachEvent.getUI());
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        refreshBadges();
        if (isOverlay()) {
            setDrawerOpened(false);
        }
    }

    /** Re-reads the pending review count shown in the navigation. */
    public void refreshBadges() {
        long pending = transactionService.pendingCount(currentUser.id());
        reviewCount.setText(pending > 999 ? "999+" : String.valueOf(pending));
        reviewCount.setVisible(pending > 0);
        reviewCount.getElement().setAttribute("aria-label", pending + " transactions to review");
    }

    private Component createDrawer() {
        Div drawer = Ui.div("fs-drawer");
        drawer.add(brand());

        reviewCount.setClassName("fs-nav-count");
        SideNavItem review = item("Review", ReviewView.class, VaadinIcon.INBOX);
        review.setSuffixComponent(reviewCount);

        SideNav main = nav("Main",
            item("Overview", DashboardView.class, VaadinIcon.HOME_O),
            item("Transactions", TransactionsView.class, VaadinIcon.LIST_UL),
            review,
            item("Import", ImportView.class, VaadinIcon.UPLOAD_ALT));
        SideNav insight = nav("Insights",
            item("Analytics", AnalyticsView.class, VaadinIcon.BAR_CHART_H),
            item("Reports", ReportsView.class, VaadinIcon.FILE_TEXT_O));
        SideNav manage = nav("Manage",
            item("Categories", CategoriesView.class, VaadinIcon.TAGS),
            item("Settings", SettingsView.class, VaadinIcon.COG_O));

        drawer.add(main, Ui.text("fs-nav-label", "Insights"), insight, Ui.text("fs-nav-label", "Manage"), manage);
        drawer.add(userFooter());
        return drawer;
    }

    private Component createMobileBar() {
        DrawerToggle toggle = new DrawerToggle();
        Ui.label(toggle, "Open navigation");
        Div bar = Ui.div("fs-mobile-bar", toggle, brand());
        return bar;
    }

    private static Component brand() {
        RouterLink link = new RouterLink(DashboardView.class);
        link.setClassName("fs-brand");
        link.add(new Html(BRAND_MARK), Ui.span("fs-brand-name", "FinSight"));
        Ui.label(link, "FinSight overview");
        return link;
    }

    private static SideNav nav(String label, SideNavItem... items) {
        SideNav nav = new SideNav();
        nav.setLabel(null);
        nav.getElement().setAttribute("aria-label", label);
        nav.addItem(items);
        return nav;
    }

    private static SideNavItem item(String label, Class<? extends Component> route, VaadinIcon icon) {
        SideNavItem item = new SideNavItem(label, route);
        item.setPrefixComponent(Ui.icon(icon));
        return item;
    }

    private Component userFooter() {
        AppUser user = currentUser.profile();
        Div avatar = Ui.text("fs-avatar", Fmt.initials(user.getDisplayName()));
        avatar.getElement().setAttribute("aria-hidden", "true");
        Div who = Ui.div("fs-user", Ui.text("fs-user-name", user.getDisplayName()), Ui.text("fs-user-email", user.getEmail()));
        Button logout = new Button(Ui.icon(VaadinIcon.SIGN_OUT), event -> currentUser.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        Ui.label(logout, "Sign out");
        return Ui.div("fs-drawer-footer", avatar, who, logout);
    }
}
