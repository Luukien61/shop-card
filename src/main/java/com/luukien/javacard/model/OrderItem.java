package com.luukien.javacard.model;

import lombok.*;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class OrderItem {

    private final Long id;
    private final String orderCode;
    private final String productCode;
    private final String productName;
    private final long quantity;
    private final BigDecimal price;

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