package com.luukien.javacard.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.sql.*;
import java.time.LocalDate;


public class DatabaseHelper {

    private static final String URL = "jdbc:postgresql://localhost:5432/shop";
    private static final String USER = "postgres";
    private static final String PASSWORD = "Dat27102003";

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            System.err.println("Kết nối DB thất bại: " + e.getMessage());
            return null;
        }
    }

    public static boolean insertUser(String userName,
                                     String address,
                                     String imageBase64,
                                     LocalDate dateOfBirth,
                                     String gender,
                                     String phone,
                                     String cardId,
                                     String publicKey) {

        String sql = """
                INSERT INTO users (
                    user_name, address, image, date_of_birth, gender,
                    phone, card_id, public_key, balance
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0.0)
                """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userName);
            pstmt.setString(2, address);
            pstmt.setString(3, imageBase64);
            pstmt.setDate(4, Date.valueOf(dateOfBirth));
            pstmt.setString(5, gender);
            pstmt.setString(6, phone);
            pstmt.setString(7, cardId);
            pstmt.setString(8, publicKey);

            int rows = pstmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi khi thêm user vào DB: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void seedUsers() {

        try (Connection conn = DatabaseHelper.getConnection()) {

            // Insert admin
            assert conn != null;
            insertUser(conn, "admin@gmail.com", "admin123", "ADMIN");

            // Insert staff
            insertUser(conn, "staff@gmail.com", "12345678", "STAFF");

            System.out.println("Users created successfully!");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void insertUser(Connection conn, String email, String pass, String role) throws SQLException {

        String hashPassword = BCrypt.withDefaults().hashToString(12, pass.toCharArray()); // 2^12 = 4.096

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, email);
        ps.setString(2, hashPassword);
        ps.setString(3, role);
        ps.executeUpdate();
        ps.close();
    }

    private static final String sql =
            "INSERT INTO system_users(email, password, role) VALUES (?, ?, ?)";
}
