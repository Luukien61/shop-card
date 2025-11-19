package com.luukien.javacard.utils;

import javafx.scene.control.Alert;

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
}
