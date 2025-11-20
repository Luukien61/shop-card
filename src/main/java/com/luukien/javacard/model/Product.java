package com.luukien.javacard.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Product {

    private Long id;
    private String code;
    private String name;
    private int remain;
    private BigDecimal price;

    public String getFormattedPrice() {
        if (price == null) return "0 ₫";
        return String.format("%,.0f ₫", price);
    }

    public String formattedPrice() {
        return getFormattedPrice();
    }
}