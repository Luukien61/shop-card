package com.luukien.javacard.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor      // cần cho một số trường hợp (JPA, JSON, test...)
@AllArgsConstructor     // tiện tạo object nhanh: new Product(id, code, ...)
@ToString
public class Product {

    private Long id;
    private String code;
    private String name;
    private int remain;
    private BigDecimal price;

    // Phương thức hiển thị giá đẹp (giữ nguyên như cũ)
    public String getFormattedPrice() {
        if (price == null) return "0 ₫";
        return String.format("%,.0f ₫", price);
    }

    // Bonus: để dùng trực tiếp trong TableView nếu muốn
    public String formattedPrice() {
        return getFormattedPrice();
    }
}