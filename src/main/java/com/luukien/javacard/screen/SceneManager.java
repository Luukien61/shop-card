package com.luukien.javacard.screen;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

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
            stage.getIcons().add(new Image(Objects.requireNonNull(SceneManager.class.getResourceAsStream("/icon.png"))));
            if (scene == null) {
                scene = new Scene(root);
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }

            stage.setWidth(WIDTH);
            stage.setHeight(HEIGHT);
            stage.setResizable(false);
            stage.setTitle("Shop JavaCard");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}