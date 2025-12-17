package com.luukien.javacard.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public class CartItem {
    @Setter
    @Getter
    private Product product;
    @Setter
    @Getter
    private int quantity;
    private boolean isPlaceholder = false;

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public boolean isPlaceholder() {
        return isPlaceholder;
    }

    public void setPlaceholder(boolean placeholder) {
        isPlaceholder = placeholder;
    }

    public BigDecimal getSubPrice() {
        if (isPlaceholder || product.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        return product.getPrice().multiply(new BigDecimal(quantity));
    }

    public String getFormattedSubPrice() {
        if (isPlaceholder) return "";
        return String.format("%,.0f ₫", getSubPrice());
    }
}