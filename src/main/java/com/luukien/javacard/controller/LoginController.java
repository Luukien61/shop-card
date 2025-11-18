package com.luukien.javacard.controller;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.state.AppState;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.function.Consumer;

public class LoginController {
    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    public void initialize() {
        loginButton.setOnAction(e -> onLoginClicked());
    }

    private void onLoginClicked() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            ApplicationHelper.showAlert("Vui lòng nhập email và mật khẩu!", true);
            return;
        }

        if (checkLogin(email, password, (role -> AppState.getInstance().setCurrentUser(email, role)))) {

            ApplicationHelper.showAlert("Đăng nhập thành công!", false);

            SceneManager.switchTo("home-management-view.fxml");
        } else {
            ApplicationHelper.showAlert("Email hoặc mật khẩu không đúng!", true);
        }

    }

    private boolean checkLogin(String email, String password, Consumer<String> consumer) {
        String sql = "SELECT password, role FROM system_users WHERE email = ?";

        try (Connection conn = DatabaseHelper.getConnection()) {
            assert conn != null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, email);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    String hashedPassword = rs.getString("password");
                    String role = rs.getString("role");
                    var isMatch = BCrypt.verifyer().verify(password.toCharArray(), hashedPassword).verified;
                    if (isMatch) consumer.accept(role);
                    return isMatch;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            ApplicationHelper.showAlert("Lỗi kết nối cơ sở dữ liệu!", true);
        }

        return false;
    }

}
