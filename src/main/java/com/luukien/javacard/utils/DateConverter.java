package com.luukien.javacard.utils;

import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateConverter {

    private static StringConverter<LocalDate> converter;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");


    public static StringConverter<LocalDate> getLocalDateConverter() {
        if (converter == null) {
            converter = new StringConverter<LocalDate>() {
                @Override
                public String toString(LocalDate date) {
                    return (date != null) ? formatter.format(date) : "";
                }

                @Override
                public LocalDate fromString(String s) {
                    return (s != null && !s.isEmpty())
                            ? LocalDate.parse(s, formatter)
                            : null;
                }
            };
        }
        return converter;
    }
}
