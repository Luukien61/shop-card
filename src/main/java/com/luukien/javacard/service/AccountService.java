package com.luukien.javacard.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.Argon2KeyDerivation;
import com.luukien.javacard.utils.DatabaseHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AccountService {

    public static String hashPass(String pass) {
        return BCrypt.withDefaults().hashToString(12, pass.toCharArray());
    }


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


    public static boolean updatePassword(String oldPass, String newPass, String email) {
        if (email == null || email.isBlank() || oldPass == null || newPass == null) {
            ApplicationHelper.showAlert("Thông tin không hợp lệ!", true);
            return false;
        }

        if (oldPass.isBlank() || newPass.isBlank()) {
            ApplicationHelper.showAlert("Mật khẩu không được để trống!", true);
            return false;
        }

        String selectSql = "SELECT password, master_key_encrypted FROM system_users WHERE email = ?";
        String updateSql = "UPDATE system_users SET password = ?, master_key_encrypted = ? WHERE email = ?";

        Connection conn = null;
        try {
            conn = DatabaseHelper.getConnection();
            if (conn == null) {
                ApplicationHelper.showAlert(ApplicationHelper.CONN_DB_MESSAGE, true);
                return false;
            }

            conn.setAutoCommit(false);


            String currentHashedPass;
            String currentEncryptedMasterKey;

            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentHashedPass = rs.getString("password");
                        currentEncryptedMasterKey = rs.getString("master_key_encrypted");
                    } else {
                        ApplicationHelper.showAlert("Không tìm thấy người dùng với email này!", true);
                        conn.rollback();
                        return false;
                    }
                }
            }


            boolean isOldPassCorrect = BCrypt.verifyer()
                    .verify(oldPass.getBytes(), currentHashedPass.getBytes())
                    .verified;
            if (!isOldPassCorrect) {
                ApplicationHelper.showAlert("Mật khẩu hiện tại không đúng!", true);
                conn.rollback();
                return false;
            }

            String newHashedPass = hashPass(newPass);

            String newEncryptedMasterKey = Argon2KeyDerivation.updateWrappedKey(
                    currentEncryptedMasterKey, oldPass, newPass);

            if (newEncryptedMasterKey == null) {
                ApplicationHelper.showAlert("Lỗi khi cập nhật khóa bảo mật. Thao tác bị hủy.", true);
                conn.rollback();
                return false;
            }

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, newHashedPass);
                ps.setString(2, newEncryptedMasterKey);
                ps.setString(3, email);

                int rows = ps.executeUpdate();
                if (rows > 0) {
                    conn.commit();
                    ApplicationHelper.showAlert("Đổi mật khẩu thành công!", false);
                    return true;
                } else {
                    conn.rollback();
                    ApplicationHelper.showAlert("Cập nhật thất bại, không có dòng nào bị thay đổi.", true);
                    return false;
                }
            }

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            ApplicationHelper.showAlert("Lỗi cơ sở dữ liệu: " + e.getMessage(), true);
            e.printStackTrace();
            return false;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            ApplicationHelper.showAlert("Lỗi hệ thống: " + e.getMessage(), true);
            e.printStackTrace();
            return false;

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }
}
