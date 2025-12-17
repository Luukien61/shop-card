package com.luukien.javacard.screen;

import com.luukien.javacard.controller.DialogController;
import com.luukien.javacard.utils.ApplicationHelper;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public class SceneManager {
    private static Stage stage;
    private static Scene scene;
    private static final double WIDTH = 650;   // ← size chung
    private static final double HEIGHT = 550;

    public static void init(Stage primaryStage) {
        stage = primaryStage;
        scene = primaryStage.getScene();
    }

    public static void switchTo(String fxmlFile) {
        try {
            Parent root = FXMLLoader.load(Objects.requireNonNull(
                    SceneManager.class.getResource("/com/luukien/javacard/" + fxmlFile)));

            stage.getIcons().add(new Image(Objects.requireNonNull(
                    SceneManager.class.getResourceAsStream("/icon.png"))));

            if (scene == null) {
                scene = new Scene(root, WIDTH, HEIGHT);
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }

            stage.setWidth(WIDTH);
            stage.setHeight(HEIGHT);
            stage.setResizable(false);
            stage.setTitle("Shop JavaCard");
            stage.centerOnScreen();
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            ApplicationHelper.showAlert("Không tải được giao diện!", true);
        }
    }


    public static <T> T showModal(String fxmlFile) {
        return showModal(fxmlFile, null, null, null);
    }

    public static <T> T showModal(String fxmlFile, String title) {
        return showModal(fxmlFile, title, null, null);
    }

    public static <T> T showModal(String fxmlFile, String title, Double width, Double height) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource("/com/luukien/javacard/" + fxmlFile));
            Parent root = loader.load();
            T controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setResizable(false);
            dialogStage.setTitle(title != null ? title : "Shop JavaCard");

            try {
                dialogStage.getIcons().add(new Image(Objects.requireNonNull(
                        SceneManager.class.getResourceAsStream("/icon.png"))));
            } catch (Exception ignored) {
            }
            Scene scene;
            if (width == null && height == null) {
                scene = new Scene(root);
            } else {
                scene = new Scene(root, width, height);
            }

            dialogStage.setScene(scene);

            if (controller instanceof DialogController) {
                ((DialogController) controller).setDialogStage(dialogStage);
            }

            dialogStage.showAndWait();
            return controller;

        } catch (Exception e) {
            e.printStackTrace();
            ApplicationHelper.showAlert("Không thể mở cửa sổ!", true);
            return null;
        }
    }

    public static <T> T showModal(String fxmlFile, Consumer<T> controllerConsumer) {
        return showModal(fxmlFile, null, null, null, controllerConsumer);
    }


    public static <T> T showModal(
            String fxmlFile,
            String title,
            Double width,
            Double height,
            Consumer<T> controllerConsumer
    ) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource("/com/luukien/javacard/" + fxmlFile));
            Parent root = loader.load();
            T controller = loader.getController();

            if (controllerConsumer != null) {
                controllerConsumer.accept(controller);
            }

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(stage);
            dialogStage.setResizable(false);
            dialogStage.setTitle(title != null ? title : "Shop JavaCard");

            Scene scene = (width == null && height == null)
                    ? new Scene(root)
                    : new Scene(root, width, height);

            dialogStage.setScene(scene);

            if (controller instanceof DialogController) {
                ((DialogController) controller).setDialogStage(dialogStage);
            }

            dialogStage.showAndWait();
            return controller;

        } catch (Exception e) {
            e.printStackTrace();
            ApplicationHelper.showAlert("Không thể mở cửa sổ!", true);
            return null;
        }
    }
}