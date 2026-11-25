package com.finsight.ui.views;

import com.finsight.ui.layout.MainLayout;
import com.vaadin.flow.component.html.Div;

/** Base for signed-in pages: centred content column with consistent rhythm. */
abstract class AppPage extends Div {

    protected AppPage() {
        setClassName("fs-page");
    }

    /** Refreshes navigation badges (e.g. the review count) after data changes. */
    protected void refreshLayout() {
        findAncestor(MainLayout.class);
        getParent().ifPresent(parent -> {
            MainLayout layout = findAncestor(MainLayout.class);
            if (layout != null) {
                layout.refreshBadges();
            }
        });
    }
}
