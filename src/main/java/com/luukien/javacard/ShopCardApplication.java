package com.luukien.javacard;

import com.luukien.javacard.screen.SceneManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ShopCardApplication extends Application {
    @Override
    public void start(Stage stage) {
        SceneManager.init(stage);
        SceneManager.switchTo("login-view.fxml");
    }

    public static void main(String[] args) {
        launch();
    }


}