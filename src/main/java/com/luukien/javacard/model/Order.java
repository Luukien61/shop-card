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
public class Order {

    private final Long id;
    private final String code;
    private final String userPhone;
    private final BigDecimal totalPrice;
    private final LocalDateTime createAt;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public String getFormattedTotal() {
        return String.format("%,.0f ₫", totalPrice);
    }

    public String getFormattedDate() {
        return createAt != null ? createAt.format(DATE_FORMATTER) : "";
    }
}