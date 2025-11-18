package com.luukien.javacard.model;

import java.math.BigDecimal;

public record Product(
        Long id,
        String code,
        String name,
        int remain,
        BigDecimal price
) {
    public String getFormattedPrice() {
        return String.format("%,.0f ₫", price);
    }
}