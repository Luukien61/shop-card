package com.luukien.javacard.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;

public class InitiateCardController {

    @FXML
    private TextField usernameField;
    @FXML
    private DatePicker dateField;
    @FXML
    private RadioButton menGender;
    @FXML
    private ToggleGroup gender;
    @FXML
    private RadioButton womenGender;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressField;
    @FXML
    private ImageView imageView;
    @FXML
    private Button chooseImageBtn;
    @FXML
    private Button finishBtn;

    private File selectedImageFile = null;


    @FXML
    public void initialize() {
        chooseImageBtn.setOnAction(e -> onChooseImage());
        finishBtn.setOnAction(e -> onFinish());
    }


    @FXML
    private void onChooseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh");

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            selectedImageFile = file;
            Image image = new Image(file.toURI().toString());
            imageView.setImage(image);
        }
    }


    @FXML
    private void onFinish() {

        String username = usernameField.getText();
        LocalDate birthDate = dateField.getValue();
        String phone = phoneField.getText();
        String address = addressField.getText();


        String genderSelected = "";
        if (menGender.isSelected()) {
            genderSelected = "Nam";
        } else if (womenGender.isSelected()) {
            genderSelected = "Nữ";
        }


        String imagePath = selectedImageFile != null ? selectedImageFile.getAbsolutePath() : null;


        System.out.println("Tên: " + username);
        System.out.println("Ngày sinh: " + birthDate);
        System.out.println("Giới tính: " + genderSelected);
        System.out.println("SĐT: " + phone);
        System.out.println("Địa chỉ: " + address);
        System.out.println("Ảnh: " + imagePath);


    }


    private byte[] fileToByteArray(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
