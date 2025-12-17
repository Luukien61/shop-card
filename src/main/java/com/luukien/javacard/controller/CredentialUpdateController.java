// CredentialUpdateController.java
package com.luukien.javacard.controller;

import com.luukien.javacard.model.SecretType;
import com.luukien.javacard.utils.ApplicationHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class CredentialUpdateController implements DialogController {

    @FXML
    private Label confirmLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label instructionLabel;
    @FXML
    private Label currentLabel;
    @FXML
    private Label newLabel;
    @FXML
    private Button actionButton;
    @FXML
    private Label warningLabel;

    @FXML
    private PasswordField currentField;
    @FXML
    private TextField currentText;
    @FXML
    private CheckBox showCurrentCheck;

    @FXML
    private PasswordField newField;
    @FXML
    private TextField newText;
    @FXML
    private CheckBox showNewCheck;

    @FXML
    private PasswordField confirmField;
    @FXML
    private TextField confirmText;
    @FXML
    private CheckBox showConfirmCheck;

    @Setter
    private Stage dialogStage;
    @Getter
    private boolean success = false;

    private SecretType secretType = SecretType.PIN;
    private BiFunction<String, String, Boolean> updateAction;

    // Validation
    private String requiredPattern = ".*";
    private String patternDescription = "giá trị";

    // Giới hạn số lần thử
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 5;

    // Lưu trạng thái cho từng loại secret (PIN, PASSWORD, etc.)
    private static final Map<String, Integer> attemptCountMap = new HashMap<>();
    private static final Map<String, LocalDateTime> lockoutTimeMap = new HashMap<>();

    private String attemptKey; // Key để track số lần thử cho secret type hiện tại
    private int remainingAttempts;

    @FXML
    private void initialize() {
        bindField(currentField, currentText, showCurrentCheck);
        bindField(newField, newText, showNewCheck);
        bindField(confirmField, confirmText, showConfirmCheck);
    }

    private void bindField(PasswordField pass, TextField text, CheckBox check) {
        text.textProperty().bindBidirectional(pass.textProperty());
        text.managedProperty().bind(check.selectedProperty());
        text.visibleProperty().bind(check.selectedProperty());
        pass.managedProperty().bind(check.selectedProperty().not());
        pass.visibleProperty().bind(check.selectedProperty().not());
    }

    public void setup(SecretType type,
                      String customTitle,
                      String customInstruction,
                      BiFunction<String, String, Boolean> updateAction) {
        this.secretType = type;
        this.updateAction = updateAction;
        this.attemptKey = type.name(); // Sử dụng tên của SecretType làm key

        titleLabel.setText(customTitle != null && !customTitle.isBlank()
                ? customTitle
                : "Đổi " + type.label.toLowerCase());

        if (customInstruction != null && !customInstruction.isBlank()) {
            instructionLabel.setText(customInstruction);
        } else {
            if (type.regex != null) {
                instructionLabel.setText(type.label + " phải đúng định dạng yêu cầu");
            } else {
                instructionLabel.setText("Vui lòng nhập đầy đủ thông tin để đổi " + type.label.toLowerCase());
            }
        }

        String currentTypeLabel = type.label + " hiện tại";
        String newTypeLabel = type.label + " mới";
        String confirmTypeLabel = "Xác nhận " + newTypeLabel;
        String currentPromptText = "Nhập " + currentTypeLabel;
        String newPromptText = "Nhập " + newTypeLabel;
        String confirmPromptText = "Nhập lại " + newTypeLabel;

        currentLabel.setText(currentTypeLabel);
        currentText.setPromptText(currentPromptText);
        currentField.setPromptText(currentPromptText);

        newLabel.setText(newTypeLabel);
        newText.setPromptText(newPromptText);
        newField.setPromptText(newPromptText);

        confirmLabel.setText(confirmTypeLabel);
        confirmText.setPromptText(confirmPromptText);
        confirmField.setPromptText(confirmPromptText);

        if (type.regex != null) {
            this.requiredPattern = type.regex;
            this.patternDescription = getDescriptionFromType(type);
        } else {
            this.requiredPattern = ".*";
            this.patternDescription = type.label.toLowerCase();
        }

        clearAllFields();
        checkLockoutStatus();
        Platform.runLater(() -> currentField.requestFocus());
    }

    private void checkLockoutStatus() {
        if (isLockedOut()) {
            long minutesRemaining = getRemainingLockoutMinutes();
            disableInputs();
            updateWarningLabel("Tài khoản đã bị khóa do nhập sai quá nhiều lần.\n" +
                    "Vui lòng thử lại sau " + minutesRemaining + " phút.");
        } else {
            enableInputs();
            updateAttemptCounter();
        }
    }

    private boolean isLockedOut() {
        LocalDateTime lockoutTime = lockoutTimeMap.get(attemptKey);
        if (lockoutTime == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(lockoutTime)) {
            return true;
        } else {
            // Hết thời gian khóa, reset
            lockoutTimeMap.remove(attemptKey);
            attemptCountMap.put(attemptKey, 0);
            return false;
        }
    }

    private long getRemainingLockoutMinutes() {
        LocalDateTime lockoutTime = lockoutTimeMap.get(attemptKey);
        if (lockoutTime == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(now, lockoutTime);
        return Math.max(1, duration.toMinutes());
    }

    private void updateAttemptCounter() {
        int attempts = attemptCountMap.getOrDefault(attemptKey, 0);
        remainingAttempts = MAX_ATTEMPTS - attempts;

        if (attempts > 0 && attempts < MAX_ATTEMPTS) {
            updateWarningLabel("Cảnh báo: Còn " + remainingAttempts + " lần thử");
        } else if (attempts == 0) {
            updateWarningLabel("");
        }
    }

    private void updateWarningLabel(String message) {
        if (warningLabel != null) {
            warningLabel.setText(message);
            warningLabel.setVisible(!message.isEmpty());
            warningLabel.setManaged(!message.isEmpty());
        }
    }

    private void disableInputs() {
        currentField.setDisable(true);
        currentText.setDisable(true);
        newField.setDisable(true);
        newText.setDisable(true);
        confirmField.setDisable(true);
        confirmText.setDisable(true);
        actionButton.setDisable(true);
        showCurrentCheck.setDisable(true);
        showNewCheck.setDisable(true);
        showConfirmCheck.setDisable(true);
    }

    private void enableInputs() {
        currentField.setDisable(false);
        currentText.setDisable(false);
        newField.setDisable(false);
        newText.setDisable(false);
        confirmField.setDisable(false);
        confirmText.setDisable(false);
        actionButton.setDisable(false);
        showCurrentCheck.setDisable(false);
        showNewCheck.setDisable(false);
        showConfirmCheck.setDisable(false);
    }

    private String getDescriptionFromType(SecretType type) {
        return switch (type) {
            case PIN -> "6 chữ số";
            case TWO_FACTOR -> "6-8 chữ số";
            case PASSWORD, MASTER_PASSWORD -> "mật khẩu mạnh";
            case PASSPHRASE -> "cụm từ khôi phục hợp lệ";
            default -> type.label.toLowerCase();
        };
    }

    private void clearAllFields() {
        currentField.clear();
        newField.clear();
        confirmField.clear();
        currentText.clear();
        newText.clear();
        confirmText.clear();
        showCurrentCheck.setSelected(false);
        showNewCheck.setSelected(false);
        showConfirmCheck.setSelected(false);
    }

    @FXML
    private void onConfirm() {
        // Kiểm tra lockout trước khi xử lý
        if (isLockedOut()) {
            long minutesRemaining = getRemainingLockoutMinutes();
            ApplicationHelper.showAlert("Tài khoản đã bị khóa.\nVui lòng thử lại sau " +
                    minutesRemaining + " phút.", true);
            return;
        }

        String current = currentField.getText().trim();
        String newVal = newField.getText();
        String confirm = confirmField.getText();

        if (current.isBlank() || newVal.isBlank() || confirm.isBlank()) {
            ApplicationHelper.showAlert("Vui lòng nhập đầy đủ các trường!", true);
            return;
        }

        if (!newVal.matches(requiredPattern)) {
            ApplicationHelper.showAlert("Giá trị mới không đúng định dạng!\nYêu cầu: " + patternDescription, true);
            newField.requestFocus();
            return;
        }

        if (typeHasFixedLength() && newVal.length() != getFixedLength()) {
            ApplicationHelper.showAlert(secretType.label + " mới phải đúng " + getFixedLength() + " ký tự!", true);
            newField.requestFocus();
            return;
        }

        if (!newVal.equals(confirm)) {
            ApplicationHelper.showAlert("Xác nhận không khớp!", true);
            confirmField.requestFocus();
            return;
        }

        if (current.equals(newVal)) {
            ApplicationHelper.showAlert("Giá trị mới không được trùng với hiện tại!", true);
            newField.requestFocus();
            return;
        }

        // Thực hiện update
        boolean result = updateAction.apply(current, newVal);
        if (result) {
            ApplicationHelper.showAlert("Đổi " + secretType.label.toLowerCase() + " thành công!", false);
            success = true;
            // Reset counter khi thành công
            attemptCountMap.put(attemptKey, 0);
            dialogStage.close();
        } else {
            handleFailedAttempt();
        }
    }

    private void handleFailedAttempt() {
        int attempts = attemptCountMap.getOrDefault(attemptKey, 0) + 1;
        attemptCountMap.put(attemptKey, attempts);

        if (attempts >= MAX_ATTEMPTS) {

            LocalDateTime lockoutTime = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
            lockoutTimeMap.put(attemptKey, lockoutTime);

            disableInputs();
            updateWarningLabel("Tài khoản đã bị khóa do nhập sai quá nhiều lần.\n" +
                    "Vui lòng thử lại sau " + LOCKOUT_MINUTES + " phút.");

            ApplicationHelper.showAlert(
                    "Bạn đã nhập sai " + MAX_ATTEMPTS + " lần!\n" +
                            "Tài khoản đã bị khóa trong " + LOCKOUT_MINUTES + " phút.",
                    true
            );
        } else {
            remainingAttempts = MAX_ATTEMPTS - attempts;
            updateAttemptCounter();

            ApplicationHelper.showAlert(
                    secretType.label + " hiện tại không đúng!\n" +
                            "Còn " + remainingAttempts + " lần thử.",
                    true
            );

            currentField.clear();
            currentField.requestFocus();
        }
    }

    private boolean typeHasFixedLength() {
        return requiredPattern.equals("\\d{6}") || requiredPattern.equals("\\d{6,8}");
    }

    private int getFixedLength() {
        return requiredPattern.equals("\\d{6}") ? 6 : 8;
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }

    // Các phương thức static để quản lý trạng thái từ bên ngoài
    public static void resetAttempts(String secretTypeKey) {
        attemptCountMap.put(secretTypeKey, 0);
        lockoutTimeMap.remove(secretTypeKey);
    }

    public static boolean isSecretLocked(String secretTypeKey) {
        LocalDateTime lockoutTime = lockoutTimeMap.get(secretTypeKey);
        if (lockoutTime == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(lockoutTime);
    }

    public static int getRemainingAttempts(String secretTypeKey) {
        int attempts = attemptCountMap.getOrDefault(secretTypeKey, 0);
        return MAX_ATTEMPTS - attempts;
    }
}