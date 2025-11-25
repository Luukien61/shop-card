package com.luukien.javacard.utils;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

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

    public static Optional<String> showPinDialog(String title, String header) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle(Objects.requireNonNullElse(title, "Xác thực thẻ"));

        alert.setHeaderText(Objects.requireNonNullElse(header, "Vui lòng nhập mã PIN"));

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

    public static Optional<String> showVerifyPinDialog(
            String title,
            String header,
            int maxRetries,
            Predicate<String> pinVerifier
    ) {
        if (maxRetries < 0) maxRetries = 5;

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title != null ? title : "Xác thực PIN");
        dialog.setHeaderText(header != null ? header : "Vui lòng nhập mã PIN để tiếp tục");

        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image("icon.png"));

        // Nút
        ButtonType okButton = new ButtonType("Xác nhận", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButton, ButtonType.CANCEL);

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 30, 20, 30));

        PasswordField pinField = new PasswordField();
        pinField.setPromptText("PIN 6 chữ số");
        pinField.setPrefWidth(200);

        Label hintLabel = new Label("Còn " + maxRetries + " lần thử");
        hintLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1976d2;");

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 13;");

        grid.add(new Label("PIN:"), 0, 0);
        grid.add(pinField, 1, 0);
        grid.add(hintLabel, 1, 1);
        grid.add(errorLabel, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setOnShown(e -> pinField.requestFocus());

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(okButton);
        okBtn.setDefaultButton(true);

        int[] attemptsLeft = {maxRetries};

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != okButton) return null;

            String pin = pinField.getText().trim();

            // validate PIN
            if (!pin.matches("\\d{6}")) {
                attemptsLeft[0]--;
                updateRetryMessage(errorLabel, hintLabel, attemptsLeft[0], "PIN phải đúng 6 chữ số!");
                pinField.clear();
                pinField.requestFocus();
                return null;
            }

            boolean pinCorrect = pinVerifier.test(pin);

            if (pinCorrect) {
                return pin;
            }

            // PIN sai
            attemptsLeft[0]--;
            if (attemptsLeft[0] > 0) {
                updateRetryMessage(errorLabel, hintLabel, attemptsLeft[0],
                        "PIN sai! Còn " + attemptsLeft[0] + " lần thử");
                pinField.clear();
                pinField.requestFocus();
            } else {
                errorLabel.setText("Đã vượt quá số lần thử!");
                errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14; -fx-font-weight: bold;");
                hintLabel.setText("Tài khoản bị khóa");
                hintLabel.setStyle("-fx-text-fill: red;");
                okBtn.setDisable(true);
            }

            return null;
        });


        pinField.setOnAction(e -> okBtn.fire());

        return dialog.showAndWait();
    }

    private static void updateRetryMessage(Label errorLabel, Label hintLabel, int attemptsLeft, String errorMsg) {
        errorLabel.setText(errorMsg);
        if (attemptsLeft > 0) {
            hintLabel.setText("Còn " + attemptsLeft + " lần thử");
            hintLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d32f2f;");
        }
    }
}
