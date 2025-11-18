package com.luukien.javacard.utils;

import javafx.scene.control.Alert;

public class ApplicationHelper {

    public static void showAlert(String message, boolean isWait) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (isWait) {
            alert.showAndWait();
        } else alert.show();

    }
}
