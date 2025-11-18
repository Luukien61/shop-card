package com.luukien.javacard.model;

import java.math.BigDecimal;

public record OrderItem(
        Long id,
        String orderCode,
        String productCode,
        String productName,
        long quantity,
        BigDecimal price
) {
    public BigDecimal getSubTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public String getFormattedSubTotal() {
        return String.format("%,.0f ₫", getSubTotal());
    }

    public String getFormattedPrice() {
        return String.format("%,.0f ₫", price);
    }
}
