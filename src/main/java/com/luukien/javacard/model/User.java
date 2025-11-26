package com.luukien.javacard.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class User {

    private Long id;
    private String userName;
    private String address;
    private String image;
    private LocalDate dateOfBirth;
    private String phone;
    private String cardId;
    private String publicKey;
    private BigDecimal balance;
    private String memberTier;
    private String gender;
    private BigDecimal quarter_spending;

    public String getFormattedBalance() {
        if (balance == null) {
            return "0 ₫";
        }
        return String.format("%,.0f ₫", balance);
    }

    public User(String userName, String address, String phone, String memberTier) {
        this.userName = userName;
        this.address = address;
        this.phone = phone;
        this.memberTier = memberTier;
    }
}