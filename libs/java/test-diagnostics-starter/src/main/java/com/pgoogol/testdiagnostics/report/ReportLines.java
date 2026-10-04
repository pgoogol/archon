package com.pgoogol.testdiagnostics.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Linie raportu w budowie. Każda dostaje przedrostek, po którym da się wyłowić raport
 * z wyjścia Mavena, a wiersze z etykietą wyrównują się kropkami do jednej kolumny.
 * Kropki liczy kod, nie pliki tekstów, bo etykiety w obu językach mają różną długość.
 */
final class ReportLines {

    static final String PREFIX = "[TEST-DIAGNOSTICS] ";

    private static final String INDENT = "   ";

    private static final int LABEL_WIDTH = 34;

    private static final String LABEL_PADDING = "%-" + LABEL_WIDTH + "s";

    private final ReportMessages messages;

    private final List<String> lines = new ArrayList<>();

    ReportLines(ReportMessages messages) {

        this.messages = messages;
    }

    String message(String key, Object... args) {

        return messages.text(key, args);
    }

    void raw(String text) {

        lines.add(PREFIX + text);
    }

    void blank() {

        raw("");
    }

    void section(String key, Object... args) {

        String title = messages.text(key, args);
        raw(" " + title);
    }

    void text(String key, Object... args) {

        String text = messages.text(key, args);
        indented(text);
    }

    void note(String key) {

        messages.lines(key).forEach(this::indented);
    }

    void row(String labelKey, String valueKey, Object... args) {

        String value = messages.text(valueKey, args);
        rowValue(labelKey, value);
    }

    void rowValue(String labelKey, String value) {

        String label = messages.text(labelKey);
        labeledRow(label, value);
    }

    /** Wiersz z etykietą już przetłumaczoną, np. nazwą atrybutu konfiguracji. */
    void labeledRow(String label, String value) {

        String leader = leader(label);
        indented(leader + " " + value);
    }

    String attributeLabel(String attribute) {

        return messages.attributeLabel(attribute);
    }

    void indented(String text) {

        raw(INDENT + text);
    }

    List<String> lines() {

        return List.copyOf(lines);
    }

    /** {@code Verdict ......} do stałej szerokości; etykieta za długa na kropki dostaje same spacje. */
    private static String leader(String label) {

        int dots = LABEL_WIDTH - label.length() - 1;
        if (dots < 1) {

            return String.format(Locale.ROOT, LABEL_PADDING, label);
        }
        return label + " " + ".".repeat(dots);
    }
}
