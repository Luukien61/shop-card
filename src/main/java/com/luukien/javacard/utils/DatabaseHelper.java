package com.luukien.javacard.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;


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
