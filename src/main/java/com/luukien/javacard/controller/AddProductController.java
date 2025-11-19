package com.luukien.javacard.controller;

import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class AddProductController {
    @FXML
    private TextField nameTextfield;
    @FXML
    private TextField codeTextfield;
    @FXML
    private TextField remainTextfield;
    @FXML
    private TextField priceTextfield;
    @FXML
    private Button cancelBtn;
    @FXML
    private Button addBtn;


    @FXML
    private void initialize() {
        cancelBtn.setOnAction(e -> onCancelBtnClick());
    }


    private void onCancelBtnClick() {
        SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
    }
}
