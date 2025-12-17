package com.luukien.javacard;

import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.utils.CardHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ShopCardApplication extends Application {
    @Override
    public void start(Stage stage) {
        //DatabaseHelper.seedUsers();
        SceneManager.init(stage);
        SceneManager.switchTo(Scenes.LOGIN_SCENE);
    }

    public static void main(String[] args) {
        launch();
    }


}