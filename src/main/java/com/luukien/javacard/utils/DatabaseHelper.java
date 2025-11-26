package com.luukien.javacard.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.luukien.javacard.state.AppState;

import java.sql.*;
import java.time.LocalDate;


public class DatabaseHelper {

    private static final String URL = "jdbc:postgresql://localhost:5432/shop";
    private static final String USER = "postgres";
    private static final String PASSWORD = "12345678";

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
            insertUser(conn, "admin@gmail.com", "admin123", "ADMIN", "123456");

            // Insert staff
            insertUser(conn, "staff@gmail.com", "12345678", "STAFF", null);

            System.out.println("Users created successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertUser(Connection conn, String email, String pass, String role, String pin) throws Exception {

        String hashPassword = BCrypt.withDefaults().hashToString(12, pass.toCharArray());

        if (pin != null && !pin.isEmpty()) {
            String encryptedKey = Argon2KeyDerivation.createEncryptedKey(pass);
            String encryptedPin = Argon2KeyDerivation.encryptData(pin, encryptedKey, pass);


            String sql = "INSERT INTO system_users(email, password, role, pin_hash, pin_encrypted, master_key_encrypted) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, hashPassword);
                ps.setString(3, role);

                String hashPin = BCrypt.withDefaults().hashToString(12, pin.toCharArray());
                ps.setString(4, hashPin);
                ps.setString(5, encryptedPin);
                ps.setString(6, encryptedKey);

                ps.executeUpdate();
            }
        } else {

            String sql = "INSERT INTO system_users(email, password, role) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, hashPassword);
                ps.setString(3, role);
                ps.executeUpdate();
            }
        }
    }

    public static boolean verifySysUserPin(String plainPIN) {
        String currentUserEmail = AppState.getInstance().getCurrentUserEmail();

        if (currentUserEmail == null || currentUserEmail.isEmpty()) {
            return false;
        }

        String sql = "SELECT pin FROM system_users WHERE email = ?";

        try (Connection conn = DatabaseHelper.getConnection()) {
            assert conn != null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, currentUserEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String hashedPin = rs.getString("pin");

                        if (hashedPin == null || hashedPin.isEmpty()) {
                            return false;
                        }

                        boolean verified = BCrypt.verifyer().verify(plainPIN.toCharArray(), hashedPin).verified;
                        System.out.println("Verify: " + verified);
                        return verified;
                    }
                    return false;
                }

            }
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra PIN hệ thống: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static int deleteIncompleteUser(String phone, String cardId) {
        if (cardId == null || cardId.isBlank() || phone == null || phone.isBlank()) {
            return 0;
        }
        String sql = "delete from users where card_id = ? and phone = ?";
        try (Connection conn = DatabaseHelper.getConnection()) {
            assert conn != null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, cardId);
                ps.setString(2, phone);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra PIN hệ thống: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
}
