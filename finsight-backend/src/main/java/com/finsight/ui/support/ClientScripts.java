package com.finsight.ui.support;

import com.vaadin.flow.component.UI;

/**
 * Small client-side behaviours installed once per page: the persisted light/dark appearance and the chart
 * tooltip layer. Tooltip content comes from {@code data-tip} JSON attributes and is inserted with
 * {@code textContent} only, never as HTML.
 */
public final class ClientScripts {

    public static final String THEME_KEY = "finsight.theme";

    private static final String THEME_SCRIPT = """
        (function(){
          try {
            var pref = localStorage.getItem('%s') || 'system';
            var dark = pref === 'dark' || (pref === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
            if (dark) { document.documentElement.setAttribute('theme', 'dark'); }
            else { document.documentElement.removeAttribute('theme'); }
          } catch (e) { /* storage unavailable: keep light */ }
        })();
        """.formatted(THEME_KEY);

    private static final String TOOLTIP_SCRIPT = """
        (function(){
          if (window.__fsTooltips) { return; }
          window.__fsTooltips = true;
          var tip = document.createElement('div');
          tip.className = 'fs-tooltip';
          tip.setAttribute('role', 'tooltip');
          document.body.appendChild(tip);
          function render(data) {
            while (tip.firstChild) { tip.removeChild(tip.firstChild); }
            if (data.t) {
              var title = document.createElement('div');
              title.className = 't-title';
              title.textContent = data.t;
              tip.appendChild(title);
            }
            (data.r || []).forEach(function(row){
              var line = document.createElement('div');
              line.className = 't-row';
              var key = document.createElement('i');
              key.className = 't-key ' + (row[0] || '');
              var label = document.createElement('span');
              label.textContent = row[1];
              var value = document.createElement('b');
              value.textContent = row[2];
              line.appendChild(key); line.appendChild(label); line.appendChild(value);
              tip.appendChild(line);
            });
          }
          function place(x, y) {
            var r = tip.getBoundingClientRect();
            var left = Math.min(window.innerWidth - r.width - 8, x + 14);
            var top = y - r.height - 12;
            if (top < 8) { top = y + 16; }
            tip.style.left = Math.max(8, left) + 'px';
            tip.style.top = top + 'px';
          }
          function show(el, x, y) {
            try { render(JSON.parse(el.getAttribute('data-tip'))); } catch (e) { return; }
            tip.classList.add('visible');
            place(x, y);
          }
          function hide() { tip.classList.remove('visible'); }
          document.addEventListener('pointermove', function(e){
            var el = e.target && e.target.closest ? e.target.closest('[data-tip]') : null;
            if (el) { show(el, e.clientX, e.clientY); } else { hide(); }
          }, { passive: true });
          document.addEventListener('focusin', function(e){
            var el = e.target && e.target.closest ? e.target.closest('[data-tip]') : null;
            if (el) { var r = el.getBoundingClientRect(); show(el, r.left + r.width / 2, r.top); }
          });
          document.addEventListener('focusout', hide);
          document.addEventListener('scroll', hide, true);
        })();
        """;

    private ClientScripts() {
    }

    public static void applyTheme(UI ui) {
        ui.getPage().executeJs(THEME_SCRIPT);
    }

    public static void installTooltips(UI ui) {
        ui.getPage().executeJs(TOOLTIP_SCRIPT);
    }

    /** Stores the appearance preference ("light", "dark" or "system") in this browser and applies it. */
    public static void setTheme(UI ui, String preference) {
        ui.getPage().executeJs("try { localStorage.setItem($0, $1); } catch (e) {}", THEME_KEY, preference);
        applyTheme(ui);
    }
}
