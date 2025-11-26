package com.luukien.javacard.utils;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.Optional;

public class ApplicationHelper {

    public static final String TRY_AGAIN_MESSAGE = "Có lỗi xảy ra. Vui lòng thử lại sau.";
    public static final String CONN_DB_MESSAGE = "Không thể kết nối đến database!";

    private static Stage progressStage;
    private static Label progressLabel;

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


    public static void showToast(String message) {
        Platform.runLater(() -> {
            Stage toastStage = new Stage();
            toastStage.initModality(Modality.NONE);
            toastStage.initOwner(getPrimaryStage());
            toastStage.setAlwaysOnTop(true);

            Label label = new Label(message);
            label.setStyle("-fx-background-color: rgba(0,0,0,0.8); -fx-text-fill: white; -fx-padding: 12; -fx-background-radius: 8; -fx-font-size: 14;");

            VBox root = new VBox(label);
            root.setAlignment(Pos.CENTER);
            Scene scene = new Scene(root);
            scene.setFill(null);
            toastStage.setScene(scene);
            toastStage.setX(getPrimaryStage().getX() + getPrimaryStage().getWidth() / 2 - 100);
            toastStage.setY(getPrimaryStage().getY() + 100);
            toastStage.show();

            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                Platform.runLater(toastStage::close);
            }).start();
        });
    }

    public static void showProgress(String message) {
        Platform.runLater(() -> {
            if (progressStage != null && progressStage.isShowing()) return;

            progressStage = new Stage();
            progressStage.initModality(Modality.APPLICATION_MODAL);
            progressStage.initOwner(getPrimaryStage());
            progressStage.setTitle("Đang xử lý...");

            ProgressIndicator pi = new ProgressIndicator();
            pi.setPrefSize(60, 60);

            progressLabel = new Label(message != null ? message : "Đang xử lý, vui lòng đợi...");
            progressLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #333;");

            VBox box = new VBox(20, pi, progressLabel);
            box.setAlignment(Pos.CENTER);
            box.setStyle("-fx-padding: 30; -fx-background-color: white; -fx-background-radius: 10;");

            Scene scene = new Scene(box, 320, 180);
            progressStage.setScene(scene);
            progressStage.setResizable(false);
            progressStage.show();
        });
    }

    public static void updateProgress(String message) {
        Platform.runLater(() -> {
            if (progressLabel != null) {
                progressLabel.setText(message);
            }
        });
    }

    public static void hideProgress() {
        Platform.runLater(() -> {
            if (progressStage != null && progressStage.isShowing()) {
                progressStage.close();
                progressStage = null;
            }
        });
    }

    public static void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Getter
    @Setter
    private static Stage primaryStage;

}
