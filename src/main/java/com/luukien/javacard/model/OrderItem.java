package com.luukien.javacard.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class OrderItem {

    private Long id;
    private String orderCode;
    private String productCode;
    private String productName;
    private int quantity;
    private BigDecimal price;

    public OrderItem(String productCode, String productName, int quantity, BigDecimal price) {
        this.productCode = productCode;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
    }

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