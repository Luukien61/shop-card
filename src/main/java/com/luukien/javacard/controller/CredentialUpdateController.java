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

    // Dùng để validate định dạng mới
    private String requiredPattern = ".*";
    private String patternDescription = "giá trị";

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


//        actionButton.setText("Đổi " + type.label.toLowerCase());

        if (type.regex != null) {
            this.requiredPattern = type.regex;
            this.patternDescription = getDescriptionFromType(type);
        } else {
            this.requiredPattern = ".*";
            this.patternDescription = type.label.toLowerCase();
        }

        clearAllFields();
        Platform.runLater(() -> currentField.requestFocus());
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

        boolean result = updateAction.apply(current, newVal);
        if (result) {
            ApplicationHelper.showAlert("Đổi " + secretType.label.toLowerCase() + " thành công!", false);
            success = true;
            dialogStage.close();
        } else {
            ApplicationHelper.showAlert(secretType.label + " hiện tại không đúng!", true);
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
}