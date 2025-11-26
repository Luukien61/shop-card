package com.luukien.javacard.controller;

import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.CardHelper;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

public class UpdateUserPinController implements DialogController {
    @FXML
    private PasswordField currentPinField;
    @FXML
    private TextField currentPinText;
    @FXML
    private CheckBox showCurrentPinCheck;

    @FXML
    private PasswordField newPinField;
    @FXML
    private TextField newPinText;
    @FXML
    private CheckBox showNewPinCheck;

    @FXML
    private PasswordField confirmPinField;
    @FXML
    private TextField confirmPinText;
    @FXML
    private CheckBox showConfirmPinCheck;

    @Setter
    private Stage dialogStage;
    @Getter
    private boolean success = false;

    @FXML
    private void initialize() {
        bindPinField(currentPinField, currentPinText, showCurrentPinCheck);
        bindPinField(newPinField, newPinText, showNewPinCheck);
        bindPinField(confirmPinField, confirmPinText, showConfirmPinCheck);

    }

    private void bindPinField(PasswordField pass, TextField text, CheckBox check) {
        text.textProperty().bindBidirectional(pass.textProperty());
        text.managedProperty().bind(check.selectedProperty());
        text.visibleProperty().bind(check.selectedProperty());
        pass.managedProperty().bind(check.selectedProperty().not());
        pass.visibleProperty().bind(check.selectedProperty().not());
    }

    @FXML
    private void onChangePin() {
        String current = currentPinField.getText();
        String newPin = newPinField.getText();
        String confirm = confirmPinField.getText();

        if (current.isBlank() || newPin.isBlank() || confirm.isBlank()) {
            ApplicationHelper.showAlert("Vui lòng nhập đầy đủ các ô!", true);
            return;
        }

        if (!current.matches("\\d{6}")) {
            ApplicationHelper.showAlert("PIN hiện tại phải đúng 6 chữ số!", true);
            return;
        }

        if (!newPin.matches("\\d{6}")) {
            ApplicationHelper.showAlert("PIN mới phải đúng 6 chữ số!", true);
            return;
        }

        if (!newPin.equals(confirm)) {
            ApplicationHelper.showAlert("PIN xác nhận không khớp!", true);
            return;
        }

        if (current.equals(newPin)) {
            ApplicationHelper.showAlert("PIN mới không được trùng PIN cũ!", true);
            return;
        }

        boolean result = CardHelper.changeUserPin(current, newPin);
        if (result) {
            ApplicationHelper.showAlert("Đổi PIN thành công!", false);
            success = true;
            dialogStage.close();
        } else {
            ApplicationHelper.showAlert("PIN hiện tại không đúng!", true);
        }
    }

    @FXML
    private void onCancel() {
        dialogStage.close();
    }

}
