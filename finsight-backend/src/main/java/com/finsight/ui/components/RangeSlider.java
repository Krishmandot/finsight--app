package com.finsight.ui.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;
import java.util.function.LongConsumer;

/**
 * A native range slider. Every movement is sent to the server, so the Java code (not browser script)
 * recalculates whatever depends on it.
 */
public class RangeSlider extends Div {

    private final Element input = new Element("input");
    private long value;

    public RangeSlider(String ariaLabel, long min, long max, long step, long initial, LongConsumer onChange) {
        setClassName("fs-slider");
        input.setAttribute("type", "range");
        input.setAttribute("min", Long.toString(min));
        input.setAttribute("max", Long.toString(max));
        input.setAttribute("step", Long.toString(step));
        input.setAttribute("aria-label", ariaLabel);
        input.setAttribute("value", Long.toString(initial));
        input.setProperty("value", Long.toString(initial));
        value = initial;
        input.addPropertyChangeListener("value", "input", event -> {
            value = parse(event.getValue());
            onChange.accept(value);
        });
        getElement().appendChild(input);
    }

    public long getValue() {
        return value;
    }

    /** Moves the slider from the server without notifying the change listener. */
    public void setValue(long newValue) {
        value = newValue;
        input.setProperty("value", Long.toString(newValue));
    }

    private static long parse(Object raw) {
        try {
            return Math.round(Double.parseDouble(String.valueOf(raw)));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
