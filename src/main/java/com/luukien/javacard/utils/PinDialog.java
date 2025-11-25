package com.luukien.javacard.utils;

import com.luukien.javacard.controller.VerifyPinController;
import com.luukien.javacard.screen.Scenes;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.function.Predicate;

public class PinDialog {

    public static void show(String title, String header, int maxAttempts,
                            Predicate<String> verifier,
                            Runnable onSuccess) {
        show(title, header, maxAttempts, verifier, onSuccess, null);
    }

    public static void show(String title, String header, int maxAttempts,
                            Predicate<String> verifier,
                            Runnable onSuccess, Runnable onFailed) {
        try {
            FXMLLoader loader = new FXMLLoader(PinDialog.class.getResource("/com/luukien/javacard/" + Scenes.VERIFY_PIN_SCENE));
            Parent root = loader.load();
            VerifyPinController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setScene(new Scene(root));
            stage.setResizable(false);

            controller.setStage(stage);
            controller.setup(title, header, maxAttempts, verifier, onSuccess, onFailed);

            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}