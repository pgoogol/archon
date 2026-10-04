package com.pgoogol.diagnostics.core.report;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Mały zapis obiektu JSON w jednej linii, bez zależności. Pola idą w kolejności dodania,
 * pole bez wartości jest pomijane, a tekst jest escapowany według RFC 8259, więc znak
 * nowej linii w zapytaniu nie rozbija linii pliku JSONL.
 */
final class JsonObjectWriter {

    private final StringBuilder json = new StringBuilder("{");

    private boolean empty = true;

    /** Pole tekstowe; {@code null} pomija pole. */
    JsonObjectWriter string(String name, @Nullable String value) {

        if (Objects.isNull(value)) {

            return this;
        }
        name(name);
        quote(value);
        return this;
    }

    JsonObjectWriter number(String name, long value) {

        name(name);
        json.append(value);
        return this;
    }

    /** Pole z gotowym obiektem JSON z innego {@code JsonObjectWriter}. */
    JsonObjectWriter object(String name, String objectJson) {

        name(name);
        json.append(objectJson);
        return this;
    }

    /** Tablica gotowych obiektów JSON; pusta lista pomija pole. */
    JsonObjectWriter array(String name, List<String> objectsJson) {

        if (objectsJson.isEmpty()) {

            return this;
        }
        name(name);
        json.append('[');
        json.append(String.join(",", objectsJson));
        json.append(']');
        return this;
    }

    String toJson() {

        return json + "}";
    }

    private void name(String name) {

        if (!empty) {

            json.append(',');
        }
        empty = false;
        quote(name);
        json.append(':');
    }

    private void quote(String value) {

        json.append('"');
        // pętla po znakach zamiast strumienia: jeden przebieg po tekście bez boksowania znaków
        for (int index = 0; index < value.length(); index++) {

            escape(value.charAt(index));
        }
        json.append('"');
    }

    /**
     * Cudzysłów, ukośnik wsteczny i znaki sterujące według RFC 8259; do tego U+2028
     * i U+2029, które JavaScript traktuje jak koniec linii.
     */
    private void escape(char character) {

        switch (character) {

            case '"' -> json.append("\\\"");
            case '\\' -> json.append("\\\\");
            case '\n' -> json.append("\\n");
            case '\r' -> json.append("\\r");
            case '\t' -> json.append("\\t");
            case '\b' -> json.append("\\b");
            case '\f' -> json.append("\\f");
            case ' ', ' ' -> json.append("\\u%04x".formatted((int) character));
            default -> appendPlainOrControl(character);
        }
    }

    private void appendPlainOrControl(char character) {

        if (character < 0x20) {

            json.append("\\u%04x".formatted((int) character));
            return;
        }
        json.append(character);
    }
}
