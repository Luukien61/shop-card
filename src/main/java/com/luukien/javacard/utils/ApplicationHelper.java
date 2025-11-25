package com.luukien.javacard.utils;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

public class ApplicationHelper {

    public static final String TRY_AGAIN_MESSAGE = "Có lỗi xảy ra. Vui lòng thử lại sau.";
    public static final String CONN_DB_MESSAGE = "Không thể kết nối đến database!";

    public static void showAlert(String message, boolean isWait) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (isWait) {
            alert.showAndWait();
        } else alert.show();

    }

    public static Optional<String> showPinDialog() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Xác thực thẻ");
        alert.setHeaderText("Vui lòng nhập mã PIN");

        PasswordField pinField = new PasswordField();
        pinField.setPromptText("Nhập PIN 6 chữ số");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        grid.add(new Label("PIN:"), 0, 0);
        grid.add(pinField, 1, 0);

        alert.getDialogPane().setContent(grid);

        ButtonType okButton = new ButtonType("Xác nhận", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(okButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return Optional.empty();
        }

        String pin = pinField.getText().trim();
        if (!pin.matches("\\d{6}")) {
            showAlert("PIN phải là số, từ 6 chữ số!", true);
            return Optional.empty();
        }

        return Optional.of(pin);
    }
}
