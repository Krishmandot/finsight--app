package com.finsight.ui.auth;

import com.finsight.ui.charts.SvgCharts;
import com.finsight.ui.components.Ui;
import com.finsight.ui.support.ClientScripts;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;

/** Two-column sign-in/registration frame: product statement on the left, form on the right. */
@StyleSheet("context://styles/finsight.css")
abstract class AuthLayout extends Div {

    protected final Div card = Ui.div("fs-auth-card");

    protected AuthLayout() {
        setClassName("fs-auth");
        Div brand = Ui.div("fs-brand", new Html("<span class=\"fs-brand-mark\" aria-hidden=\"true\"><svg viewBox=\"0 0 14 14\">"
            + "<path d=\"M2 11.5 L5.2 7.6 L7.8 9.6 L12 3.5\" fill=\"none\" stroke=\"#121a16\" stroke-width=\"1.8\" "
            + "stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg></span>"), Ui.span("fs-brand-name", "FinSight"));
        H2 headline = new H2("See where your money goes, without giving it away.");
        com.vaadin.flow.dom.Element points = new com.vaadin.flow.dom.Element("ul");
        points.getClassList().add("fs-auth-points");
        for (String text : new String[]{"Import CSV or PDF statements from any bank",
            "Every imported transaction is reviewed before it counts",
            "Weekly, fortnightly and monthly reports from your real data"}) {
            com.vaadin.flow.dom.Element li = new com.vaadin.flow.dom.Element("li");
            li.appendChild(Ui.icon(VaadinIcon.CHECK).getElement());
            li.appendChild(new com.vaadin.flow.dom.Element("span").setText(text));
            points.appendChild(li);
        }
        Div quote = Ui.div("fs-auth-quote", headline, new Paragraph(
            "FinSight keeps a single, private ledger of your income and spending and turns it into clear, honest insight."));
        quote.getElement().appendChild(points);
        Div aside = Ui.div("fs-auth-aside", brand, quote, Ui.text("fs-auth-foot", "Account numbers are masked before storage."));
        add(aside, Ui.div("fs-auth-main", card));
        SvgCharts.authSparkline();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        ClientScripts.applyTheme(attachEvent.getUI());
    }
}
