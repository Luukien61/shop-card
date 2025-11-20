package com.luukien.javacard.model;

import lombok.*;

@Getter
@AllArgsConstructor
@Builder
@ToString(exclude = "password")
@EqualsAndHashCode
public class SystemUser {

    private final Long id;
    private final String email;
    private final String password;
    private final String role;
}