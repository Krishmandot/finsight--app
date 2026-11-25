package com.finsight.ui.charts;

import com.finsight.service.analytics.DailyPoint;
import com.finsight.service.analytics.MonthlyPoint;
import com.finsight.service.support.MoneyFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-rendered SVG charts. Colours come from CSS custom properties (light and dark are both defined in
 * finsight.css), so markup here only carries semantic classes. Every mark group has a {@code data-tip}
 * tooltip and is keyboard focusable; tooltips enhance but never gate, as each chart also has a table view.
 *
 * <p>Specs: columns at most 24px wide with a 4px rounded data end and a square baseline, a 2px gap between
 * paired columns, hairline solid gridlines, a single y-axis, and selective direct labels (latest value only).</p>
 */
public final class SvgCharts {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 240;
    private static final int LEFT = 52;
    private static final int RIGHT = 8;
    private static final int TOP = 20;
    private static final int BOTTOM = 28;
    private static final double MAX_BAR = 24;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private SvgCharts() {
    }

    /** Paired income/spending columns per month. */
    public static String incomeVsSpending(List<MonthlyPoint> points, String currency) {
        double max = points.stream()
            .mapToDouble(p -> Math.max(p.income().doubleValue(), Math.max(0, p.spending().doubleValue())))
            .max().orElse(0);
        Scale scale = Scale.of(0, max);
        Svg svg = new Svg("Income and spending by month");
        svg.gridAndAxis(scale, currency);

        double plotWidth = WIDTH - LEFT - RIGHT;
        double band = plotWidth / Math.max(points.size(), 1);
        double bar = Math.min(MAX_BAR, (band * 0.62 - 2) / 2);
        for (int i = 0; i < points.size(); i++) {
            MonthlyPoint p = points.get(i);
            double center = LEFT + band * i + band / 2;
            double incomeX = center - bar - 1;
            double spendX = center + 1;
            svg.column("s-income", incomeX, bar, scale.y(0), scale.y(p.income().doubleValue()));
            svg.column("s-spend", spendX, bar, scale.y(0), scale.y(Math.max(0, p.spending().doubleValue())));
            boolean last = i == points.size() - 1;
            if (last && p.hasData()) {
                svg.valueLabel(incomeX + bar / 2, scale.y(p.income().doubleValue()) - 6,
                    MoneyFormatter.compact(p.income(), currency));
                svg.valueLabel(spendX + bar / 2, scale.y(Math.max(0, p.spending().doubleValue())) - 6,
                    MoneyFormatter.compact(p.spending(), currency));
            }
            svg.xLabel(center, monthLabel(p.month(), i, points));
            svg.hit(LEFT + band * i, TOP, band, HEIGHT - TOP - BOTTOM, tooltip(p.month().format(MONTH_YEAR),
                row("income", "Income", MoneyFormatter.formatWhole(p.income(), currency)),
                row("spend", "Spending", MoneyFormatter.formatWhole(p.spending(), currency)),
                row("", "Net", MoneyFormatter.formatWhole(p.net(), currency))));
        }
        return svg.close();
    }

    /** Monthly net cash flow as diverging columns around zero (saved vs overspent). */
    public static String netCashFlow(List<MonthlyPoint> points, String currency) {
        double max = points.stream().mapToDouble(p -> p.net().doubleValue()).max().orElse(0);
        double min = points.stream().mapToDouble(p -> p.net().doubleValue()).min().orElse(0);
        Scale scale = Scale.of(Math.min(0, min), Math.max(0, max));
        Svg svg = new Svg("Net cash flow by month");
        svg.gridAndAxis(scale, currency);

        double band = (WIDTH - LEFT - RIGHT) / (double) Math.max(points.size(), 1);
        double bar = Math.min(MAX_BAR, band * 0.5);
        for (int i = 0; i < points.size(); i++) {
            MonthlyPoint p = points.get(i);
            double center = LEFT + band * i + band / 2;
            double value = p.net().doubleValue();
            svg.column(value >= 0 ? "s-pos" : "s-neg", center - bar / 2, bar, scale.y(0), scale.y(value));
            if (i == points.size() - 1 && p.hasData()) {
                double labelY = value >= 0 ? scale.y(value) - 6 : scale.y(value) + 14;
                svg.valueLabel(center, labelY, MoneyFormatter.compact(p.net(), currency));
            }
            svg.xLabel(center, monthLabel(p.month(), i, points));
            svg.hit(LEFT + band * i, TOP, band, HEIGHT - TOP - BOTTOM, tooltip(p.month().format(MONTH_YEAR),
                row(value >= 0 ? "pos" : "neg", value >= 0 ? "Saved" : "Overspent",
                    MoneyFormatter.formatWhole(p.net().abs(), currency)),
                row("", "Savings rate", p.savingsRate() == null ? "—" : p.savingsRate().setScale(0, RoundingMode.HALF_UP) + "%")));
        }
        return svg.close();
    }

    /** Savings rate (% of income kept) as a line; months without income leave a gap rather than a fake zero. */
    public static String savingsRate(List<MonthlyPoint> points) {
        double max = points.stream().filter(p -> p.savingsRate() != null)
            .mapToDouble(p -> p.savingsRate().doubleValue()).max().orElse(100);
        double min = points.stream().filter(p -> p.savingsRate() != null)
            .mapToDouble(p -> p.savingsRate().doubleValue()).min().orElse(0);
        Scale scale = Scale.of(Math.min(0, min), Math.max(10, max));
        Svg svg = new Svg("Savings rate by month");
        svg.gridAndAxisPercent(scale);

        double band = (WIDTH - LEFT - RIGHT) / (double) Math.max(points.size(), 1);
        StringBuilder path = new StringBuilder();
        boolean penDown = false;
        List<double[]> dots = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            MonthlyPoint p = points.get(i);
            double x = LEFT + band * i + band / 2;
            if (p.savingsRate() == null) {
                penDown = false;
            } else {
                double y = scale.y(p.savingsRate().doubleValue());
                path.append(penDown ? " L" : " M").append(fmt(x)).append(',').append(fmt(y));
                penDown = true;
                dots.add(new double[]{x, y, i});
            }
            svg.xLabel(x, monthLabel(p.month(), i, points));
            svg.hit(LEFT + band * i, TOP, band, HEIGHT - TOP - BOTTOM, tooltip(p.month().format(MONTH_YEAR),
                row("", "Savings rate", p.savingsRate() == null ? "No income" : p.savingsRate().setScale(1, RoundingMode.HALF_UP) + "%")));
        }
        if (!path.isEmpty()) {
            svg.raw("<path class=\"line-net\" d=\"" + path.toString().trim() + "\"/>");
        }
        for (int i = 0; i < dots.size(); i++) {
            double[] d = dots.get(i);
            svg.raw("<circle class=\"dot-net\" r=\"4\" cx=\"" + fmt(d[0]) + "\" cy=\"" + fmt(d[1]) + "\"/>");
            if (i == dots.size() - 1) {
                MonthlyPoint p = points.get((int) d[2]);
                svg.valueLabel(d[0], d[1] - 10, p.savingsRate().setScale(0, RoundingMode.HALF_UP) + "%");
            }
        }
        return svg.close();
    }

    /** Daily spending columns for a report period, with income shown in the tooltip. */
    public static String dailySpending(List<DailyPoint> points, String currency) {
        double max = points.stream().mapToDouble(p -> Math.max(0, p.spending().doubleValue())).max().orElse(0);
        Scale scale = Scale.of(0, max);
        Svg svg = new Svg("Spending by day");
        svg.gridAndAxis(scale, currency);

        double band = (WIDTH - LEFT - RIGHT) / (double) Math.max(points.size(), 1);
        double bar = Math.min(MAX_BAR, Math.max(3, band - 4));
        int labelEvery = points.size() <= 8 ? 1 : points.size() <= 16 ? 2 : 5;
        int peak = -1;
        for (int i = 0; i < points.size(); i++) {
            if (peak < 0 || points.get(i).spending().compareTo(points.get(peak).spending()) > 0) {
                peak = i;
            }
        }
        for (int i = 0; i < points.size(); i++) {
            DailyPoint p = points.get(i);
            double center = LEFT + band * i + band / 2;
            double value = Math.max(0, p.spending().doubleValue());
            svg.column("s-spend", center - bar / 2, bar, scale.y(0), scale.y(value));
            if (i == peak && value > 0) {
                svg.valueLabel(center, scale.y(value) - 6, MoneyFormatter.compact(p.spending(), currency));
            }
            if (i % labelEvery == 0 || i == points.size() - 1) {
                svg.xLabel(center, points.size() <= 8 ? p.date().format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
                    : String.valueOf(p.date().getDayOfMonth()));
            }
            svg.hit(LEFT + band * i, TOP, band, HEIGHT - TOP - BOTTOM, tooltip(p.date().format(WEEKDAY),
                row("spend", "Spending", MoneyFormatter.formatWhole(p.spending(), currency)),
                row("income", "Income", MoneyFormatter.formatWhole(p.income(), currency))));
        }
        return svg.close();
    }

    /** A decorative-free sparkline for the sign-in screen; static sample geometry, no data. */
    public static String authSparkline() {
        return "<svg viewBox=\"0 0 300 56\" preserveAspectRatio=\"none\" aria-hidden=\"true\">"
            + "<path d=\"M0,44 L25,40 L50,42 L75,34 L100,36 L125,28 L150,30 L175,22 L200,25 L225,16 L250,19 L275,10 L300,12\""
            + " fill=\"none\" stroke=\"#7fcca7\" stroke-width=\"2\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/></svg>";
    }

    private static String monthLabel(YearMonth month, int index, List<MonthlyPoint> points) {
        boolean showYear = index == 0 || month.getMonthValue() == 1;
        return showYear && points.size() > 1 ? month.format(DateTimeFormatter.ofPattern("MMM ''yy", Locale.ENGLISH))
            : month.format(MONTH);
    }

    // ------------------------------------------------------------------------------------------- helpers

    private static String[] row(String key, String label, String value) {
        return new String[]{key, label, value};
    }

    private static String tooltip(String title, String[]... rows) {
        StringBuilder json = new StringBuilder("{\"t\":").append(jsonString(title)).append(",\"r\":[");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('[').append(jsonString(rows[i][0])).append(',').append(jsonString(rows[i][1])).append(',')
                .append(jsonString(rows[i][2])).append(']');
        }
        return json.append("]}").toString();
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : (value == null ? "" : value).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    static String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Linear y-scale with "nice" rounded ticks. */
    record Scale(double min, double max, double step) {
        static Scale of(double rawMin, double rawMax) {
            double min = Math.min(0, rawMin);
            double max = Math.max(rawMax, min + 1);
            double step = niceStep((max - min) / 4);
            double niceMin = Math.floor(min / step) * step;
            double niceMax = Math.ceil(max / step) * step;
            if (niceMax <= niceMin) {
                niceMax = niceMin + step;
            }
            return new Scale(niceMin, niceMax, step);
        }

        double y(double value) {
            double plot = HEIGHT - TOP - BOTTOM;
            return TOP + plot - (value - min) / (max - min) * plot;
        }

        private static double niceStep(double raw) {
            if (raw <= 0) {
                return 1;
            }
            double exponent = Math.pow(10, Math.floor(Math.log10(raw)));
            double fraction = raw / exponent;
            double nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10;
            return nice * exponent;
        }
    }

    private static final class Svg {
        private final StringBuilder out = new StringBuilder();

        Svg(String title) {
            out.append("<svg viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\" role=\"img\" aria-label=\"").append(xml(title)).append("\">")
                .append("<title>").append(xml(title)).append("</title>");
        }

        void gridAndAxis(Scale scale, String currency) {
            for (double v = scale.min(); v <= scale.max() + scale.step() / 2; v += scale.step()) {
                double y = scale.y(v);
                boolean baseline = Math.abs(v) < scale.step() / 1000;
                out.append("<line class=\"").append(baseline ? "axis" : "grid").append("\" x1=\"").append(LEFT)
                    .append("\" x2=\"").append(WIDTH - RIGHT).append("\" y1=\"").append(fmt(y)).append("\" y2=\"")
                    .append(fmt(y)).append("\"/>");
                out.append("<text x=\"").append(LEFT - 8).append("\" y=\"").append(fmt(y + 4))
                    .append("\" text-anchor=\"end\">")
                    .append(xml(MoneyFormatter.compact(BigDecimal.valueOf(v), currency))).append("</text>");
            }
        }

        void gridAndAxisPercent(Scale scale) {
            for (double v = scale.min(); v <= scale.max() + scale.step() / 2; v += scale.step()) {
                double y = scale.y(v);
                boolean baseline = Math.abs(v) < scale.step() / 1000;
                out.append("<line class=\"").append(baseline ? "axis" : "grid").append("\" x1=\"").append(LEFT)
                    .append("\" x2=\"").append(WIDTH - RIGHT).append("\" y1=\"").append(fmt(y)).append("\" y2=\"")
                    .append(fmt(y)).append("\"/>");
                out.append("<text x=\"").append(LEFT - 8).append("\" y=\"").append(fmt(y + 4))
                    .append("\" text-anchor=\"end\">").append(Math.round(v)).append("%</text>");
            }
        }

        /** Column from the baseline to {@code yValue}; the data end is rounded, the baseline end square. */
        void column(String cssClass, double x, double width, double yBase, double yValue) {
            double height = Math.abs(yBase - yValue);
            if (height < 0.5 || width <= 0) {
                return;
            }
            double r = Math.min(4, Math.min(width / 2, height));
            String d;
            if (yValue < yBase) {
                d = "M" + fmt(x) + "," + fmt(yBase) + " V" + fmt(yValue + r)
                    + " Q" + fmt(x) + "," + fmt(yValue) + " " + fmt(x + r) + "," + fmt(yValue)
                    + " H" + fmt(x + width - r)
                    + " Q" + fmt(x + width) + "," + fmt(yValue) + " " + fmt(x + width) + "," + fmt(yValue + r)
                    + " V" + fmt(yBase) + " Z";
            } else {
                d = "M" + fmt(x) + "," + fmt(yBase) + " V" + fmt(yValue - r)
                    + " Q" + fmt(x) + "," + fmt(yValue) + " " + fmt(x + r) + "," + fmt(yValue)
                    + " H" + fmt(x + width - r)
                    + " Q" + fmt(x + width) + "," + fmt(yValue) + " " + fmt(x + width) + "," + fmt(yValue - r)
                    + " V" + fmt(yBase) + " Z";
            }
            out.append("<path class=\"").append(cssClass).append("\" d=\"").append(d).append("\"/>");
        }

        void valueLabel(double x, double y, String text) {
            out.append("<text class=\"label-strong\" x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
                .append("\" text-anchor=\"middle\">").append(xml(text)).append("</text>");
        }

        void xLabel(double x, String text) {
            out.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(HEIGHT - 8)
                .append("\" text-anchor=\"middle\">").append(xml(text)).append("</text>");
        }

        void hit(double x, double y, double width, double height, String tooltipJson) {
            out.append("<rect class=\"hit\" tabindex=\"0\" x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
                .append("\" width=\"").append(fmt(width)).append("\" height=\"").append(fmt(height))
                .append("\" data-tip=\"").append(xml(tooltipJson)).append("\"/>");
        }

        void raw(String markup) {
            out.append(markup);
        }

        String close() {
            return out.append("</svg>").toString();
        }
    }

    /** Exposed for the report's day labels. */
    public static String dayLabel(LocalDate date) {
        return date.format(DAY);
    }
}
