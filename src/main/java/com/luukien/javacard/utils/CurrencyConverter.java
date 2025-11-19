package com.luukien.javacard.utils;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.UnaryOperator;

public class CurrencyConverter {

    private static final Locale VND_LOCALE = Locale.of("vi", "VN");
    private static final NumberFormat VND_FORMAT = NumberFormat.getNumberInstance(VND_LOCALE);

    public static void apply(TextField field) {

        UnaryOperator<TextFormatter.Change> filter = change -> {

            String raw = removeNonDigits(change.getControlNewText());

            if (raw.isEmpty()) {
                field.setText("");
                return change;
            }

            try {
                long value = Long.parseLong(raw);
                String formatted = VND_FORMAT.format(value) + " VND";

                field.setText(formatted);
                field.positionCaret(formatted.length());

            } catch (NumberFormatException e) {
                return null;
            }

            return null;
        };

        field.setTextFormatter(new TextFormatter<>(filter));
    }

    private static String removeNonDigits(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    public static java.math.BigDecimal getNumericValue(TextField field) {
        String numeric = removeNonDigits(field.getText());
        if (numeric.isEmpty()) return java.math.BigDecimal.ZERO;
        return new java.math.BigDecimal(numeric);
    }
}
