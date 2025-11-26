package com.luukien.javacard.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.luukien.javacard.sql.SqlQueries;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.DatabaseHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class AccountService {


    public static boolean verifyPassword(String pass, String email) {
        if (email == null || email.isBlank()) {
            ApplicationHelper.showAlert("Mã người dùng sai", true);
            return false;
        }
        String sql = "select password from system_users where email = ?";
        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn == null) throw new RuntimeException(ApplicationHelper.CONN_DB_MESSAGE);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ResultSet resultSet = ps.executeQuery();
                if (resultSet.next()) {
                    String hashedPass = resultSet.getString("password");
                    return BCrypt.verifyer().verify(pass.getBytes(), hashedPass.getBytes()).verified;
                }
            }
        } catch (Exception e) {
            ApplicationHelper.showAlert(e.getMessage(), true);
        }
        return false;
    }

    public static String[] getEncryptedKeyAndPin(String email) {

        if (email == null || email.isBlank()) {
            ApplicationHelper.showAlert("Mã người dùng sai", true);
            return null;
        }
        String sql = "select pin_encrypted, master_key_encrypted from system_users where email = ?";
        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn == null) throw new RuntimeException(ApplicationHelper.CONN_DB_MESSAGE);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ResultSet resultSet = ps.executeQuery();
                if (resultSet.next()) {
                    String encryptedPin = resultSet.getString("pin_encrypted");
                    String encryptedMasterKey = resultSet.getString("master_key_encrypted");
                    return new String[]{encryptedPin, encryptedMasterKey};
                }
            }
        } catch (Exception e) {
            ApplicationHelper.showAlert(e.getMessage(), true);
        }
        return null;
    }
}
