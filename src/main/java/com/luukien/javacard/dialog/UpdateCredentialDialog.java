package com.luukien.javacard.dialog;

import com.luukien.javacard.controller.CredentialUpdateController;
import com.luukien.javacard.model.SecretType;
import com.luukien.javacard.screen.Scenes;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.function.BiFunction;

public class UpdateCredentialDialog {

    public static void show(SecretType type,
                            String customTitle,
                            String customInstruction,
                            BiFunction<String, String, Boolean> updateAction) {
        try {
            FXMLLoader loader = new FXMLLoader(UpdateCredentialDialog.class
                    .getResource("/com/luukien/javacard/" + Scenes.UPDATE_CREDENTIAL_SCENE));
            Parent root = loader.load();
            CredentialUpdateController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setScene(new Scene(root));
            stage.setResizable(false);

            controller.setDialogStage(stage);
            controller.setup(type, customTitle, customInstruction, updateAction);

            stage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
