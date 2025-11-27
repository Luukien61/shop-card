package com.luukien.javacard.controller;

import com.luukien.javacard.model.SecretType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.Setter;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class VerifySecretController {

    @FXML
    private Button doneBtn;
    @FXML
    private Button cancelBtn;
    @FXML
    private PasswordField secretField;
    @FXML
    private Label attemptLeftLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Label headerLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label secretLabel;

    @Setter
    private Stage stage;

    private Predicate<String> verifier;
    private int maxAttempts = 5;
    private int attemptsLeft;
    private Consumer<String> onSuccess;
    private Runnable onFailed;
    private SecretType secretType = SecretType.PIN; // Mặc định



    @FXML
    public void initialize() {
        secretField.setOnAction(e -> doneBtn.fire());
        doneBtn.setOnAction(e -> handleVerify());
        cancelBtn.setOnAction(e -> stage.close());
    }

    public void setup(SecretType type, String header, int maxAttempts, Predicate<String> verifier,
                      Consumer<String> onSuccess, Runnable onFailed) {
        this.secretType = type;
        this.maxAttempts = maxAttempts;
        this.attemptsLeft = maxAttempts;
        this.verifier = verifier;
        this.onSuccess = onSuccess;
        this.onFailed = onFailed;

        titleLabel.setText("Xác thực " + type.label);
        headerLabel.setText(header != null && !header.isEmpty() ? header : "Vui lòng nhập " + type.label.toLowerCase());
        secretLabel.setText(type.label + ":");
        secretField.setPromptText(type.prompt);

        errorLabel.setText("");
        updateAttemptsLabel();
        secretField.clear();
        Platform.runLater(secretField::requestFocus);
    }

    public void setup(String title, String header, int maxAttempts, Predicate<String> verifier) {
        setup(SecretType.PIN, header, maxAttempts, verifier, null, null);
        if (title != null) stage.setTitle(title);
    }

    private void handleVerify() {
        String input = secretField.getText().trim();

        if (secretType.regex != null && !input.matches(secretType.regex)) {
            attemptsLeft--;
            showError(secretType.invalidMsg);
            return;
        }
        if (secretType.fixedLength > 0 && input.length() != secretType.fixedLength) {
            attemptsLeft--;
            showError(secretType.label + " phải đúng " + secretType.fixedLength + " ký tự!");
            return;
        }

        if (verifier.test(input)) {
            if (onSuccess != null) onSuccess.accept(input);
            stage.close();
        } else {
            attemptsLeft--;
            if (attemptsLeft > 0) {
                showError("Sai " + secretType.label.toLowerCase() + "! Còn " + attemptsLeft + " lần thử");
            } else {
                lockDialog();
                if (onFailed != null) onFailed.run();
            }
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        updateAttemptsLabel();
        secretField.clear();
        secretField.requestFocus();
    }

    private void updateAttemptsLabel() {
        if (attemptsLeft >= maxAttempts) {
            attemptLeftLabel.setText("");
        } else {
            attemptLeftLabel.setText("Còn " + attemptsLeft + " lần thử");
            attemptLeftLabel.setStyle(attemptsLeft <= 2
                    ? "-fx-text-fill: red; -fx-font-weight: bold;"
                    : "-fx-text-fill: #d32f2f;");
        }
    }

    private void lockDialog() {
        errorLabel.setText("Đã vượt quá số lần thử cho phép!");
        errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 15; -fx-font-weight: bold;");
        attemptLeftLabel.setText(secretType == SecretType.PIN ? "Thẻ bị khóa tạm thời" : "Đã bị khóa");
        attemptLeftLabel.setStyle("-fx-text-fill: red;");
        doneBtn.setDisable(true);
        secretField.setDisable(true);

        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            Platform.runLater(stage::close);
        }).start();
    }

}