package com.luukien.javacard.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Setter
@NoArgsConstructor
public class Order {

    private Long id;
    private String code;
    private String userPhone;
    private BigDecimal totalPrice;
    private LocalDateTime createAt;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public String getFormattedTotal() {
        return String.format("%,.0f ₫", totalPrice);
    }

    public String getFormattedDate() {
        return createAt != null ? createAt.format(DATE_FORMATTER) : "";
    }
}