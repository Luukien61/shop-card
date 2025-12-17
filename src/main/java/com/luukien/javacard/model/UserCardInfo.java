package com.luukien.javacard.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class UserCardInfo {

    private String userName;
    private String address;
    private String phone;
    private String image;
    private String cardId;
    private Boolean isCardVerified;

}
