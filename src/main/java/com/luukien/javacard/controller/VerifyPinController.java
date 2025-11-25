package com.luukien.javacard.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import lombok.Setter;

import java.util.function.Predicate;

public class VerifyPinController {
    @FXML
    private Button doneBtn;
    @FXML
    private Button cancelBtn;
    @FXML
    private PasswordField pinTextField;
    @FXML
    private Label attemptLeftLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Label headerLabel;


    @Setter
    private Stage stage;
    private Predicate<String> pinVerifier;
    private int maxAttempts;
    private int attemptsLeft;
    private Runnable onSuccess;
    private Runnable onFailed;

    @FXML
    public void initialize() {
        pinTextField.setOnAction(e -> doneBtn.fire());

        doneBtn.setOnAction(e -> handleVerify());
        cancelBtn.setOnAction(e -> stage.close());
    }

    public void setup(String title, String header, int maxAttempts, Predicate<String> verifier) {
        this.setup(title, header, maxAttempts, verifier, null, null);
    }

    public void setup(String title, String header, int maxAttempts,
                      Predicate<String> verifier, Runnable onSuccess, Runnable onFailed) {
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
        this.attemptsLeft = this.maxAttempts;
        this.pinVerifier = verifier;
        this.onSuccess = onSuccess;
        this.onFailed = onFailed;

        stage.setTitle(title != null ? title : "Xác thực PIN");
        headerLabel.setText(header != null ? header : "Vui lòng nhập mã PIN");
        updateAttemptsLabel();

        errorLabel.setText("");
        pinTextField.clear();
        Platform.runLater(pinTextField::requestFocus);
    }

    private void handleVerify() {
        String pin = pinTextField.getText().trim();

        if (!pin.matches("\\d{6}")) {
            attemptsLeft--;
            showError("PIN phải đúng 6 chữ số!");
            return;
        }

        if (pinVerifier.test(pin)) {
            if (onSuccess != null) onSuccess.run();
            stage.close();
        } else {
            attemptsLeft--;
            if (attemptsLeft > 0) {
                showError("PIN sai! Còn " + attemptsLeft + " lần thử");
            } else {
                lockDialog();
                if (onFailed != null) onFailed.run();
            }
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        updateAttemptsLabel();
        pinTextField.clear();
        pinTextField.requestFocus();
    }

    private void updateAttemptsLabel() {
        attemptLeftLabel.setText("Còn " + attemptsLeft + " lần thử");
        attemptLeftLabel.setStyle(attemptsLeft <= 2
                ? "-fx-text-fill: red; -fx-font-weight: bold;"
                : "-fx-text-fill: #1565c0; -fx-font-weight: bold;");
    }

    private void lockDialog() {
        errorLabel.setText("Đã vượt quá số lần thử cho phép!");
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 15; -fx-font-weight: bold;");
        attemptLeftLabel.setText("Thẻ bị khóa tạm thời");
        attemptLeftLabel.setStyle("-fx-text-fill: red;");
        doneBtn.setDisable(true);
        pinTextField.setDisable(true);

        new Thread(() -> {
            try { Thread.sleep(3000); }
            catch (InterruptedException ignored) {}
            Platform.runLater(stage::close);
        }).start();
    }

    public String getPin() {
        return pinTextField.getText().trim();
    }
}
