package com.luukien.javacard.model;

public record SystemUser(
        Long id,
        String email,
        String password,  // đã hash bằng BCrypt
        String role       // "ADMIN" hoặc "STAFF"
) {}