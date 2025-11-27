package com.luukien.javacard.dialog;

import com.luukien.javacard.controller.VerifySecretController;
import com.luukien.javacard.model.SecretType;
import com.luukien.javacard.screen.Scenes;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class VerifyCredentialDialog {


    public static void show(SecretType type, String header, int maxAttempts,
                            Predicate<String> verifier,
                            Consumer<String> onSuccess, Runnable onFailed) {
        try {
            FXMLLoader loader = new FXMLLoader(VerifyCredentialDialog.class.getResource("/com/luukien/javacard/" + Scenes.VERIFY_PIN_SCENE));
            Parent root = loader.load();
            VerifySecretController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setScene(new Scene(root));
            stage.setResizable(false);

            controller.setStage(stage);
            controller.setup(type, header, maxAttempts, verifier, onSuccess, onFailed);

            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}