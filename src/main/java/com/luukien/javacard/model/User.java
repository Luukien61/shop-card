package com.luukien.javacard.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record User(
        Long id,
        String userName,
        String pin,
        String address,
        String image,
        LocalDate dateOfBirth,
        String phone,
        String cardId,
        String publicKey,
        BigDecimal balance
) {

    public String getFormattedBalance() {
        if (balance == null) return "0 ₫";
        return String.format("%,.0f ₫", balance);
    }
}
