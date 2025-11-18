package com.luukien.javacard.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Order(
        Long id,
        String code,
        Long userId,
        BigDecimal totalPrice,
        LocalDateTime createAt
) {
    public String getFormattedTotal() {
        return String.format("%,.0f ₫", totalPrice);
    }

    public String getFormattedDate() {
        return createAt != null ? createAt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "";
    }
}